package com.minisql.client.sql;

import com.google.protobuf.ByteString;
import com.minisql.common.proto.TableSchema;

import java.util.Map;

public class DecodedRow {

    private final TableSchema schema;
    private final ByteString key;
    private final Map<String, ByteString> rawColumns;
    private final Map<String, Object> columns;

    DecodedRow(TableSchema schema, ByteString key,
               Map<String, ByteString> rawColumns, Map<String, Object> columns) {
        this.schema = schema;
        this.key = key;
        this.rawColumns = rawColumns;
        this.columns = columns;
    }

    public TableSchema schema() { return schema; }

    public TableSchema getSchema() { return schema; }

    public ByteString key() { return key; }

    public ByteString getKey() { return key; }

    public Map<String, ByteString> rawColumns() { return rawColumns; }

    public Map<String, ByteString> getRawColumns() { return rawColumns; }

    public Map<String, Object> columns() { return columns; }

    public Map<String, Object> getColumns() { return columns; }

    public Object get(String column) { return columns.get(column); }

    public ByteString rawValue(String column) { return rawColumns.get(column); }
}
