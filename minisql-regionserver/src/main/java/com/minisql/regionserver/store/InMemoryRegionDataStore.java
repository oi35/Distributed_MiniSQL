package com.minisql.regionserver.store;

import com.google.protobuf.ByteString;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * In-memory store used by default and in tests.
 */
public class InMemoryRegionDataStore implements RegionDataStore {

    private final NavigableMap<ByteArrayKey, StoredRowRecord> rows =
            Collections.synchronizedNavigableMap(new TreeMap<>());

    @Override
    public void upsert(byte[] key, Map<String, ByteString> columns, long timestamp) {
        ByteArrayKey rowKey = new ByteArrayKey(key);
        rows.compute(rowKey, (ignored, existing) -> {
            Map<String, ByteString> nextColumns = new LinkedHashMap<>();
            if (existing != null) {
                nextColumns.putAll(existing.getColumns());
            }
            nextColumns.putAll(columns);
            return new StoredRowRecord(key.clone(), nextColumns, timestamp);
        });
    }

    @Override
    public StoredRowRecord get(byte[] key) {
        return rows.get(new ByteArrayKey(key));
    }

    @Override
    public boolean delete(byte[] key) {
        return rows.remove(new ByteArrayKey(key)) != null;
    }

    @Override
    public boolean exists(byte[] key) {
        return rows.containsKey(new ByteArrayKey(key));
    }

    @Override
    public List<StoredRowRecord> scan(byte[] startKey, byte[] endKey, boolean reverse) {
        List<StoredRowRecord> orderedRows = new ArrayList<>();
        synchronized (rows) {
            NavigableMap<ByteArrayKey, StoredRowRecord> ordered = reverse ? rows.descendingMap() : rows;
            ByteArrayKey start = startKey == null || startKey.length == 0 ? null : new ByteArrayKey(startKey);
            ByteArrayKey end = endKey == null || endKey.length == 0 ? null : new ByteArrayKey(endKey);
            for (Map.Entry<ByteArrayKey, StoredRowRecord> entry : ordered.entrySet()) {
                if (!isInRange(entry.getKey(), start, end, reverse)) {
                    continue;
                }
                orderedRows.add(entry.getValue());
            }
        }
        return orderedRows;
    }

    @Override
    public long rowCount() {
        synchronized (rows) {
            return rows.size();
        }
    }

    @Override
    public long sizeBytes() {
        long sizeBytes = 0L;
        synchronized (rows) {
            for (StoredRowRecord row : rows.values()) {
                sizeBytes += row.getKey().length;
                for (Map.Entry<String, ByteString> entry : row.getColumns().entrySet()) {
                    sizeBytes += entry.getKey().getBytes(StandardCharsets.UTF_8).length;
                    sizeBytes += entry.getValue().size();
                }
            }
        }
        return sizeBytes;
    }

    @Override
    public void close() {
        rows.clear();
    }

    private boolean isInRange(ByteArrayKey candidate, ByteArrayKey start, ByteArrayKey end, boolean reverse) {
        if (!reverse) {
            boolean afterStart = start == null || candidate.compareTo(start) >= 0;
            boolean beforeEnd = end == null || end.bytes.length == 0 || candidate.compareTo(end) < 0;
            return afterStart && beforeEnd;
        }
        boolean beforeStart = start == null || start.bytes.length == 0 || candidate.compareTo(start) <= 0;
        boolean afterEnd = end == null || candidate.compareTo(end) > 0;
        return beforeStart && afterEnd;
    }

    private static final class ByteArrayKey implements Comparable<ByteArrayKey> {
        private final byte[] bytes;

        private ByteArrayKey(byte[] bytes) {
            this.bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public int compareTo(ByteArrayKey other) {
            return Arrays.compareUnsigned(bytes, other.bytes);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ByteArrayKey)) {
                return false;
            }
            return Arrays.equals(bytes, ((ByteArrayKey) obj).bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
}
