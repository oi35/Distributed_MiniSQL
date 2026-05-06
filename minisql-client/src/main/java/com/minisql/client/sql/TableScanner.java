package com.minisql.client.sql;

import com.google.protobuf.ByteString;
import com.minisql.client.MiniSQLClient;
import com.minisql.common.proto.ColumnSchema;
import com.minisql.common.proto.TableSchema;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class TableScanner {

    private final MiniSQLClient client;

    public TableScanner(MiniSQLClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    public Iterable<DecodedRow> scanAll(TableSchema schema) {
        List<String> columns = new ArrayList<>(schema.getColumnsCount());
        for (ColumnSchema col : schema.getColumnsList()) {
            columns.add(col.getName());
        }
        List<MiniSQLClient.ScanRow> raw = client.scan(
                schema.getTableName(), ByteString.EMPTY, ByteString.EMPTY, 0, columns);
        return () -> new Iterator<>() {
            private final Iterator<MiniSQLClient.ScanRow> delegate = raw.iterator();

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public DecodedRow next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                MiniSQLClient.ScanRow row = delegate.next();
                Map<String, Object> decoded = new LinkedHashMap<>();
                Map<String, ByteString> rawCols = row.getColumns();
                for (ColumnSchema col : schema.getColumnsList()) {
                    ByteString value = rawCols.get(col.getName());
                    decoded.put(col.getName(), value == null ? null : ValueCodec.decode(col, value));
                }
                return new DecodedRow(schema, row.getKey(), rawCols, decoded);
            }
        };
    }
}
