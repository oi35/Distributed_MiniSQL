package com.minisql.regionserver.store;

import com.google.protobuf.ByteString;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

/**
 * Storage abstraction for a single region.
 */
public interface RegionDataStore extends Closeable {

    void upsert(byte[] key, Map<String, ByteString> columns, long timestamp);

    StoredRowRecord get(byte[] key);

    boolean delete(byte[] key);

    boolean exists(byte[] key);

    List<StoredRowRecord> scan(byte[] startKey, byte[] endKey, boolean reverse);

    long rowCount();

    long sizeBytes();

    @Override
    void close();

    final class StoredRowRecord {
        private final byte[] key;
        private final Map<String, ByteString> columns;
        private final long timestamp;

        public StoredRowRecord(byte[] key, Map<String, ByteString> columns, long timestamp) {
            this.key = key;
            this.columns = columns;
            this.timestamp = timestamp;
        }

        public byte[] getKey() {
            return key;
        }

        public Map<String, ByteString> getColumns() {
            return columns;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
