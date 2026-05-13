package com.minisql.regionserver.wal;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class WalRecord {
    private final long sequenceId;
    private final String regionId;
    private final String tableName;
    private final long timestamp;
    private final String operation;
    private final byte[] key;
    private final Map<String, byte[]> columns;
    private final byte[] checksum;

    public WalRecord(long sequenceId,
                     String regionId,
                     String tableName,
                     long timestamp,
                     String operation,
                     byte[] key,
                     Map<String, byte[]> columns,
                     byte[] checksum) {
        this.sequenceId = sequenceId;
        this.regionId = regionId;
        this.tableName = tableName;
        this.timestamp = timestamp;
        this.operation = operation;
        this.key = key;
        this.columns = new LinkedHashMap<>(columns);
        this.checksum = checksum;
    }

    /**
     * Deserialize a WalRecord from the PaxosProposer binary format.
     * Format: seqId(long) + regionId(UTF) + tableName(UTF) + timestamp(long)
     * + operation(UTF) + keyLen(int)+key(bytes) + colCount(int) + perCol(UTF+int+bytes)
     * + checksumLen(int)+checksum(bytes).
     */
    public static WalRecord deserialize(byte[] data) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
            long seqId = dis.readLong();
            String regionId = dis.readUTF();
            String tableName = dis.readUTF();
            long timestamp = dis.readLong();
            String operation = dis.readUTF();
            int keyLen = dis.readInt();
            byte[] key = new byte[keyLen];
            dis.readFully(key);
            int colCount = dis.readInt();
            Map<String, byte[]> columns = new LinkedHashMap<>();
            for (int i = 0; i < colCount; i++) {
                String colName = dis.readUTF();
                int valLen = dis.readInt();
                byte[] val = new byte[valLen];
                dis.readFully(val);
                columns.put(colName, val);
            }
            int checksumLen = dis.readInt();
            byte[] checksum = null;
            if (checksumLen > 0) {
                checksum = new byte[checksumLen];
                dis.readFully(checksum);
            }
            return new WalRecord(seqId, regionId, tableName, timestamp, operation, key, columns, checksum);
        }
    }

    public long getSequenceId() {
        return sequenceId;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getTableName() {
        return tableName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getOperation() {
        return operation;
    }

    public byte[] getKey() {
        return key;
    }

    public Map<String, byte[]> getColumns() {
        return new LinkedHashMap<>(columns);
    }

    public byte[] getChecksum() {
        return checksum;
    }
}
