package com.minisql.client.sql;

import com.google.protobuf.ByteString;
import com.minisql.client.exception.MiniSQLClientException;
import com.minisql.common.proto.ColumnSchema;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.TableSchema;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class ValueCodec {

    private ValueCodec() {}

    public static ByteString encode(ColumnSchema column, Object value) {
        String type = normalizeType(column.getType());
        if (value == null) {
            if (!column.getNullable()) {
                throw new MiniSQLClientException(
                        "column " + column.getName() + " does not allow null",
                        ErrorCode.ERROR_INVALID_ARGUMENT);
            }
            return ByteString.EMPTY;
        }
        try {
            switch (type) {
                case "BIGINT":
                case "LONG":
                case "INT":
                case "INTEGER":
                case "SMALLINT":
                case "TINYINT":
                    return encodeLong(toLong(value));
                case "DOUBLE":
                case "FLOAT":
                case "REAL":
                    return encodeDouble(toDouble(value));
                case "BOOLEAN":
                case "BOOL":
                    return ByteString.copyFrom(new byte[]{toBoolean(value) ? (byte) 1 : (byte) 0});
                case "VARCHAR":
                case "CHAR":
                case "TEXT":
                case "STRING":
                    return ByteString.copyFrom(String.valueOf(value), StandardCharsets.UTF_8);
                default:
                    throw new MiniSQLClientException(
                            "unsupported column type: " + column.getType(),
                            ErrorCode.ERROR_UNIMPLEMENTED);
            }
        } catch (NumberFormatException e) {
            throw new MiniSQLClientException(
                    "value '" + value + "' not valid for " + column.getName() + " (" + column.getType() + ")",
                    ErrorCode.ERROR_INVALID_ARGUMENT, e);
        }
    }

    public static Object decode(ColumnSchema column, ByteString raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String type = normalizeType(column.getType());
        switch (type) {
            case "BIGINT":
            case "LONG":
            case "INT":
            case "INTEGER":
            case "SMALLINT":
            case "TINYINT":
                return decodeLong(raw);
            case "DOUBLE":
            case "FLOAT":
            case "REAL":
                return decodeDouble(raw);
            case "BOOLEAN":
            case "BOOL":
                return raw.byteAt(0) != 0;
            case "VARCHAR":
            case "CHAR":
            case "TEXT":
            case "STRING":
                return raw.toStringUtf8();
            default:
                throw new MiniSQLClientException(
                        "unsupported column type: " + column.getType(),
                        ErrorCode.ERROR_UNIMPLEMENTED);
        }
    }

    public static ByteString encodePrimaryKey(TableSchema schema, Object value) {
        ColumnSchema pk = primaryKeyColumn(schema);
        return encode(pk, value);
    }

    public static ColumnSchema primaryKeyColumn(TableSchema schema) {
        String pkName = schema.getPrimaryKey();
        for (ColumnSchema col : schema.getColumnsList()) {
            if (col.getName().equals(pkName)) {
                return col;
            }
        }
        throw new MiniSQLClientException(
                "primary key column '" + pkName + "' not found in schema of " + schema.getTableName(),
                ErrorCode.ERROR_INVALID_ARGUMENT);
    }

    public static ColumnSchema findColumn(TableSchema schema, String name) {
        for (ColumnSchema col : schema.getColumnsList()) {
            if (col.getName().equalsIgnoreCase(name)) {
                return col;
            }
        }
        throw new MiniSQLClientException(
                "column '" + name + "' not found in table " + schema.getTableName(),
                ErrorCode.ERROR_NOT_FOUND);
    }

    private static ByteString encodeLong(long v) {
        // flip sign bit so unsigned byte comparison matches signed numeric order
        long biased = v ^ 0x8000_0000_0000_0000L;
        ByteBuffer buf = ByteBuffer.allocate(Long.BYTES);
        buf.putLong(biased);
        return ByteString.copyFrom(buf.array());
    }

    private static long decodeLong(ByteString raw) {
        if (raw.size() != Long.BYTES) {
            throw new MiniSQLClientException(
                    "corrupt BIGINT value (size=" + raw.size() + ")",
                    ErrorCode.ERROR_INTERNAL);
        }
        ByteBuffer buf = ByteBuffer.wrap(raw.toByteArray());
        return buf.getLong() ^ 0x8000_0000_0000_0000L;
    }

    private static ByteString encodeDouble(double v) {
        // Not order-preserving for negatives; acceptable for non-PK columns.
        ByteBuffer buf = ByteBuffer.allocate(Double.BYTES);
        buf.putDouble(v);
        return ByteString.copyFrom(buf.array());
    }

    private static double decodeDouble(ByteString raw) {
        if (raw.size() != Double.BYTES) {
            throw new MiniSQLClientException(
                    "corrupt DOUBLE value (size=" + raw.size() + ")",
                    ErrorCode.ERROR_INTERNAL);
        }
        return ByteBuffer.wrap(raw.toByteArray()).getDouble();
    }

    private static long toLong(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return Long.parseLong(String.valueOf(v).trim());
    }

    private static double toDouble(Object v) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        return Double.parseDouble(String.valueOf(v).trim());
    }

    private static boolean toBoolean(Object v) {
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        String s = String.valueOf(v).trim();
        if (s.equalsIgnoreCase("true") || s.equals("1")) {
            return true;
        }
        if (s.equalsIgnoreCase("false") || s.equals("0")) {
            return false;
        }
        throw new MiniSQLClientException(
                "cannot parse boolean from '" + v + "'",
                ErrorCode.ERROR_INVALID_ARGUMENT);
    }

    private static String normalizeType(String rawType) {
        String upper = rawType.toUpperCase(Locale.ROOT).trim();
        int paren = upper.indexOf('(');
        return paren >= 0 ? upper.substring(0, paren).trim() : upper;
    }
}
