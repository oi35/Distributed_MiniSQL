package com.minisql.regionserver.replication;

import com.minisql.regionserver.wal.WalManager;
import com.minisql.regionserver.wal.WalRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 增强版WAL服务 —— 为副本同步提供高效日志管理。
 *
 * 核心能力：
 * 1. 自动生成单调递增的序列号
 * 2. 维护内存索引：序列号 → 日志记录，支持快速范围查询
 * 3. 按 Region 分组索引，支持副本同步时按 region 拉取增量日志
 * 4. 日志轮转：当单个WAL文件超过大小阈值时自动创建新文件
 * 5. 计算每条日志的 MD5 校验和，用于副本数据一致性验证
 */
public class WalService implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(WalService.class);

    // 默认单个WAL文件最大大小：64MB
    private static final long DEFAULT_MAX_FILE_SIZE = 64 * 1024 * 1024L;

    private final String regionServerId;
    private final Path walDirectory;
    private final long maxFileSizeBytes;

    // 当前活跃的 WalManager（写入目标）
    private volatile WalManager currentWalManager;
    // 历史 WalManager 列表（用于读取旧日志）
    private final List<WalManager> archivedWalManagers;

    // 全局序列号生成器（单调递增）
    private final AtomicLong sequenceGenerator;

    // 内存索引：sequenceId → WalRecord（保留最近N条记录以加速查询）
    private final TreeMap<Long, WalRecord> memoryIndex;
    // 每个 region 的序列号列表：regionId → 该 region 的所有序列号
    private final Map<String, TreeSet<Long>> regionIndex;

    // 保护索引的读写锁
    private final ReadWriteLock indexLock;
    // 内存中最多保留的记录数
    private static final int MAX_IN_MEMORY_RECORDS = 10000;

    // 文件轮转计数器
    private int walFileCounter;

    /**
     * @param walDirectory   WAL 文件存储目录
     * @param regionServerId RegionServer 唯一标识
     */
    public WalService(String walDirectory, String regionServerId) {
        this(walDirectory, regionServerId, DEFAULT_MAX_FILE_SIZE);
    }

    /**
     * @param walDirectory    WAL 文件存储目录
     * @param regionServerId  RegionServer 唯一标识
     * @param maxFileSizeBytes 单个文件最大字节数
     */
    public WalService(String walDirectory, String regionServerId, long maxFileSizeBytes) {
        this.regionServerId = regionServerId;
        this.walDirectory = Paths.get(walDirectory);
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.archivedWalManagers = new ArrayList<>();
        this.sequenceGenerator = new AtomicLong(0);
        this.memoryIndex = new TreeMap<>();
        this.regionIndex = new ConcurrentHashMap<>();
        this.indexLock = new ReentrantReadWriteLock();
        this.walFileCounter = 0;

        try {
            Files.createDirectories(this.walDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建WAL目录: " + walDirectory, e);
        }

        // 创建第一个 WAL 文件
        rotateWalFile();
        // 从已有文件中恢复索引
        rebuildIndex();
        logger.info("WalService 初始化完成: regionServer={}, 当前序列号={}",
                regionServerId, sequenceGenerator.get());
    }

    /**
     * 追加一条日志记录到WAL，返回包含序列号的完整日志记录。
     *
     * @param regionId  Region ID
     * @param tableName 表名
     * @param operation 操作类型（PUT / DELETE）
     * @param key       行键
     * @param columns   列数据
     * @return 完整的 WalRecord（含分配的序列号和校验和）
     */
    public WalRecord append(String regionId, String tableName, String operation,
                            byte[] key, Map<String, byte[]> columns) {
        long seq = sequenceGenerator.incrementAndGet();
        long timestamp = System.currentTimeMillis();
        byte[] checksum = computeChecksum(seq, regionId, operation, key, columns);

        WalRecord record = new WalRecord(seq, regionId, tableName, timestamp,
                operation, key, columns, checksum);

        // 1. 先写入磁盘（WAL原则：先写日志，再更新索引）
        currentWalManager.append(record);

        // 2. 更新内存索引
        indexLock.writeLock().lock();
        try {
            memoryIndex.put(seq, record);
            regionIndex.computeIfAbsent(regionId, k -> new TreeSet<>()).add(seq);

            // 内存索引超限时清理最旧的记录
            while (memoryIndex.size() > MAX_IN_MEMORY_RECORDS) {
                Long oldestSeq = memoryIndex.firstKey();
                WalRecord oldest = memoryIndex.remove(oldestSeq);
                if (oldest != null) {
                    TreeSet<Long> regionSeqs = regionIndex.get(oldest.getRegionId());
                    if (regionSeqs != null) {
                        regionSeqs.remove(oldestSeq);
                    }
                }
            }
        } finally {
            indexLock.writeLock().unlock();
        }

        // 3. 检查是否需要轮转文件
        checkRotation();

        logger.debug("WAL追加: seq={}, region={}, op={}", seq, regionId, operation);
        return record;
    }

    /**
     * 按序列号范围获取日志记录（用于副本同步）。
     *
     * @param regionId      Region ID，为 null 表示不过滤 region
     * @param startSequence 起始序列号（包含），<=0 表示从第一条开始
     * @param endSequence   结束序列号（包含），<=0 表示到最新一条
     * @return 符合范围的日志记录列表（按序列号升序排列）
     */
    public List<WalRecord> getRecords(String regionId, long startSequence, long endSequence) {
        long start = startSequence <= 0 ? 1 : startSequence;
        long end = endSequence <= 0 ? sequenceGenerator.get() : endSequence;

        List<WalRecord> results = new ArrayList<>();

        indexLock.readLock().lock();
        try {
            // 先收集相关的序列号
            Collection<Long> candidateSeqs;
            if (regionId != null && !regionId.isEmpty()) {
                TreeSet<Long> regionSeqs = regionIndex.get(regionId);
                if (regionSeqs == null) {
                    return results;
                }
                candidateSeqs = regionSeqs.subSet(start, true, end, true);
            } else {
                candidateSeqs = memoryIndex.subMap(start, true, end, true).keySet();
            }

            // 从内存索引中读取记录
            for (Long seq : candidateSeqs) {
                WalRecord record = memoryIndex.get(seq);
                if (record != null) {
                    results.add(record);
                }
            }
        } finally {
            indexLock.readLock().unlock();
        }

        // 如果内存索引中没找到足够的记录，从磁盘文件中读取
        if (results.isEmpty() && start <= sequenceGenerator.get()) {
            results.addAll(loadFromDisk(regionId, start, end));
        }

        return results;
    }

    /**
     * 获取指定 Region 的最新序列号。
     */
    public long getLatestSequenceId(String regionId) {
        indexLock.readLock().lock();
        try {
            TreeSet<Long> regionSeqs = regionIndex.get(regionId);
            if (regionSeqs != null && !regionSeqs.isEmpty()) {
                return regionSeqs.last();
            }
        } finally {
            indexLock.readLock().unlock();
        }
        return 0;
    }

    /**
     * 获取全局最新序列号。
     */
    public long getGlobalLatestSequenceId() {
        return sequenceGenerator.get();
    }

    /**
     * 加载所有日志记录（用于故障恢复时的完整回放）。
     */
    public List<WalRecord> loadAll() {
        List<WalRecord> allRecords = new ArrayList<>();

        // 先读取历史归档文件
        for (WalManager archived : archivedWalManagers) {
            allRecords.addAll(archived.loadAll());
        }
        // 再读取当前活跃文件
        if (currentWalManager != null) {
            allRecords.addAll(currentWalManager.loadAll());
        }

        return allRecords;
    }

    /**
     * 生成数据校验和（MD5），用于副本间数据一致性校验。
     */
    private byte[] computeChecksum(long seq, String regionId, String operation,
                                   byte[] key, Map<String, byte[]> columns) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(Long.toString(seq).getBytes());
            md.update(regionId.getBytes());
            md.update(operation.getBytes());
            md.update(key);
            for (Map.Entry<String, byte[]> entry : columns.entrySet()) {
                md.update(entry.getKey().getBytes());
                md.update(entry.getValue());
            }
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            // MD5 是所有 JVM 的必需算法，正常情况不会抛出此异常
            throw new IllegalStateException("MD5算法不可用", e);
        }
    }

    /**
     * 从磁盘文件中加载指定范围的日志记录（当内存索引不命中时使用）。
     */
    private List<WalRecord> loadFromDisk(String regionId, long startSequence, long endSequence) {
        List<WalRecord> results = new ArrayList<>();

        // 遍历所有WAL文件（包括归档和当前）
        List<WalManager> allManagers = new ArrayList<>(archivedWalManagers);
        if (currentWalManager != null) {
            allManagers.add(currentWalManager);
        }

        for (WalManager manager : allManagers) {
            for (WalRecord record : manager.loadAll()) {
                if (record.getSequenceId() >= startSequence
                        && record.getSequenceId() <= endSequence) {
                    if (regionId == null || regionId.isEmpty()
                            || regionId.equals(record.getRegionId())) {
                        results.add(record);
                    }
                }
            }
        }

        return results;
    }

    /**
     * 检查当前WAL文件是否超过大小阈值，超过则轮转。
     */
    private void checkRotation() {
        Path currentFile = currentWalManager.getWalFile();
        try {
            if (Files.exists(currentFile) && Files.size(currentFile) >= maxFileSizeBytes) {
                logger.info("WAL文件大小超过阈值({}MB)，触发轮转",
                        maxFileSizeBytes / (1024 * 1024));
                rotateWalFile();
            }
        } catch (IOException e) {
            logger.warn("检查WAL文件大小时出错", e);
        }
    }

    /**
     * 轮转WAL文件：归档当前文件，创建新文件。
     */
    private void rotateWalFile() {
        if (currentWalManager != null) {
            archivedWalManagers.add(currentWalManager);
            logger.info("归档WAL文件: {}", currentWalManager.getWalFile());
        }

        walFileCounter++;
        String walFileName = String.format("%s-%03d", regionServerId, walFileCounter);
        currentWalManager = new WalManager(walDirectory, walFileName);

        logger.info("创建新WAL文件: {}", currentWalManager.getWalFile());
    }

    /**
     * 从已有WAL文件重建内存索引（启动时调用）。
     */
    private void rebuildIndex() {
        logger.info("开始重建WAL索引...");
        long maxSeq = 0;
        int recordCount = 0;

        // 找出目录下所有 .wal 文件
        try {
            Files.list(walDirectory)
                    .filter(p -> p.toString().endsWith(".wal"))
                    .sorted()
                    .forEach(walFile -> {
                        WalManager manager = new WalManager(
                                walDirectory, walFile.getFileName().toString());
                        // 不调用 rotate，直接读取
                    });
        } catch (IOException e) {
            logger.warn("扫描WAL目录时出错", e);
        }

        // 从当前 WalManager 加载
        if (currentWalManager != null) {
            List<WalRecord> records = currentWalManager.loadAll();
            for (WalRecord record : records) {
                indexLock.writeLock().lock();
                try {
                    memoryIndex.put(record.getSequenceId(), record);
                    regionIndex.computeIfAbsent(record.getRegionId(), k -> new TreeSet<>())
                            .add(record.getSequenceId());
                } finally {
                    indexLock.writeLock().unlock();
                }
                if (record.getSequenceId() > maxSeq) {
                    maxSeq = record.getSequenceId();
                }
                recordCount++;
            }
        }

        sequenceGenerator.set(maxSeq);
        logger.info("WAL索引重建完成: 加载{}条记录, 最大序列号={}", recordCount, maxSeq);
    }

    @Override
    public void close() {
        if (currentWalManager != null) {
            currentWalManager.close();
        }
        for (WalManager archived : archivedWalManagers) {
            archived.close();
        }
        logger.info("WalService 已关闭: regionServer={}", regionServerId);
    }
}
