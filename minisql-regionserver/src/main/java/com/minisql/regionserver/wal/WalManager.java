package com.minisql.regionserver.wal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WalManager implements Closeable {

    private final Path walFile;
    private final Object writeLock = new Object();

    public WalManager(Path walDirectory, String regionServerId) {
        try {
            Files.createDirectories(walDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create WAL directory " + walDirectory, e);
        }
        this.walFile = walDirectory.resolve(regionServerId + ".wal");
    }

    public void append(WalRecord record) {
        synchronized (writeLock) {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(walFile,
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND)))) {
                output.writeLong(record.getSequenceId());
                output.writeUTF(record.getRegionId());
                output.writeUTF(record.getTableName());
                output.writeLong(record.getTimestamp());
                output.writeUTF(record.getOperation());
                output.writeInt(record.getKey().length);
                output.write(record.getKey());
                output.writeInt(record.getColumns().size());
                for (Map.Entry<String, byte[]> entry : record.getColumns().entrySet()) {
                    output.writeUTF(entry.getKey());
                    byte[] value = entry.getValue();
                    output.writeInt(value.length);
                    output.write(value);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to append WAL record", e);
            }
        }
    }

    public List<WalRecord> loadAll() {
        List<WalRecord> records = new ArrayList<>();
        if (!Files.exists(walFile)) {
            return records;
        }

        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(walFile)))) {
            while (true) {
                long sequenceId = input.readLong();
                String regionId = input.readUTF();
                String tableName = input.readUTF();
                long timestamp = input.readLong();
                String operation = input.readUTF();
                byte[] key = new byte[input.readInt()];
                input.readFully(key);
                int columnCount = input.readInt();
                java.util.Map<String, byte[]> columns = new java.util.LinkedHashMap<>();
                for (int i = 0; i < columnCount; i++) {
                    String columnName = input.readUTF();
                    byte[] value = new byte[input.readInt()];
                    input.readFully(value);
                    columns.put(columnName, value);
                }
                records.add(new WalRecord(sequenceId, regionId, tableName, timestamp, operation, key, columns, null));
            }
        } catch (EOFException ignored) {
            return records;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load WAL records", e);
        }
    }

    public Path getWalFile() {
        return walFile;
    }

    @Override
    public void close() {
    }
}
