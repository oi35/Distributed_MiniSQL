package com.minisql.client.sql;

import com.google.protobuf.ByteString;
import com.minisql.client.MiniSQLClient;
import com.minisql.client.exception.MiniSQLClientException;
import com.minisql.client.schema.TableSchemaCache;
import com.minisql.common.proto.ColumnSchema;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.TableSchema;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SqlExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(SqlExecutor.class);

    private final MiniSQLClient client;
    private final TableSchemaCache schemas;

    public SqlExecutor(MiniSQLClient client, TableSchemaCache schemas) {
        this.client = Objects.requireNonNull(client, "client");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
    }

    public SqlResult execute(String sql) {
        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw new MiniSQLClientException(
                    "SQL parse error: " + e.getMessage(),
                    ErrorCode.ERROR_INVALID_ARGUMENT, e);
        }
        if (stmt instanceof Insert) {
            return executeInsert((Insert) stmt);
        }
        if (stmt instanceof Select) {
            return executeSelect((Select) stmt);
        }
        if (stmt instanceof Delete) {
            return executeDelete((Delete) stmt);
        }
        throw new MiniSQLClientException(
                "unsupported SQL statement: " + stmt.getClass().getSimpleName(),
                ErrorCode.ERROR_UNIMPLEMENTED);
    }

    private SqlResult executeInsert(Insert insert) {
        String tableName = insert.getTable().getName();
        TableSchema schema = schemas.get(tableName);

        List<Column> columns = insert.getColumns();
        if (columns == null || columns.isEmpty()) {
            throw new MiniSQLClientException(
                    "INSERT requires an explicit column list",
                    ErrorCode.ERROR_INVALID_ARGUMENT);
        }
        if (!(insert.getItemsList() instanceof ExpressionList)) {
            throw new MiniSQLClientException(
                    "only INSERT ... VALUES (...) is supported",
                    ErrorCode.ERROR_UNIMPLEMENTED);
        }
        List<Expression> values = ((ExpressionList) insert.getItemsList()).getExpressions();
        if (values.size() != columns.size()) {
            throw new MiniSQLClientException(
                    "column count does not match value count",
                    ErrorCode.ERROR_INVALID_ARGUMENT);
        }

        Map<String, ByteString> encoded = new LinkedHashMap<>();
        Object pkValue = null;
        for (int i = 0; i < columns.size(); i++) {
            String colName = columns.get(i).getColumnName();
            ColumnSchema colSchema = ValueCodec.findColumn(schema, colName);
            Object literal = literalValue(values.get(i));
            encoded.put(colSchema.getName(), ValueCodec.encode(colSchema, literal));
            if (colSchema.getName().equalsIgnoreCase(schema.getPrimaryKey())) {
                pkValue = literal;
            }
        }
        if (pkValue == null) {
            throw new MiniSQLClientException(
                    "primary key " + schema.getPrimaryKey() + " must be specified in INSERT",
                    ErrorCode.ERROR_INVALID_ARGUMENT);
        }

        ByteString pkBytes = ValueCodec.encodePrimaryKey(schema, pkValue);
        MiniSQLClient.PutResult result = client.put(tableName, pkBytes, encoded);
        if (!result.isSuccess()) {
            throw new MiniSQLClientException(
                    "INSERT failed: " + result.getErrorMessage(),
                    result.getErrorCode());
        }
        return SqlResult.updateCount(1);
    }

    private SqlResult executeSelect(Select select) {
        SelectBody body = select.getSelectBody();
        if (!(body instanceof PlainSelect)) {
            throw new MiniSQLClientException(
                    "only simple SELECT statements are supported",
                    ErrorCode.ERROR_UNIMPLEMENTED);
        }
        PlainSelect plain = (PlainSelect) body;
        if (plain.getJoins() != null && !plain.getJoins().isEmpty()) {
            throw new MiniSQLClientException(
                    "JOIN is not supported in v1+",
                    ErrorCode.ERROR_UNIMPLEMENTED);
        }
        String tableName = plain.getFromItem().toString();
        TableSchema schema = schemas.get(tableName);
        List<String> projectedColumns = extractProjection(plain, schema);

        Expression where = plain.getWhere();
        ColumnSchema pk = ValueCodec.primaryKeyColumn(schema);

        if (where instanceof EqualsTo) {
            EqualsTo eq = (EqualsTo) where;
            requirePkColumn(eq.getLeftExpression(), pk);
            ByteString key = ValueCodec.encode(pk, literalValue(eq.getRightExpression()));
            MiniSQLClient.GetResult got = client.get(tableName, key, projectedColumns);
            if (!got.isFound()) {
                return SqlResult.rows(projectedColumns, Collections.emptyList());
            }
            return SqlResult.rows(projectedColumns,
                    List.of(decodeRow(schema, got.getColumns(), projectedColumns)));
        }

        RangeBounds range = extractRange(where, pk, schema);
        List<MiniSQLClient.ScanRow> scanned = client.scan(
                tableName, range.start, range.end, 0, projectedColumns);
        List<Map<String, Object>> rows = new ArrayList<>(scanned.size());
        for (MiniSQLClient.ScanRow row : scanned) {
            rows.add(decodeRow(schema, row.getColumns(), projectedColumns));
        }
        return SqlResult.rows(projectedColumns, rows);
    }

    private SqlResult executeDelete(Delete delete) {
        String tableName = delete.getTable().getName();
        TableSchema schema = schemas.get(tableName);
        ColumnSchema pk = ValueCodec.primaryKeyColumn(schema);
        Expression where = delete.getWhere();
        if (!(where instanceof EqualsTo)) {
            throw new MiniSQLClientException(
                    "DELETE only supports WHERE primary_key = value",
                    ErrorCode.ERROR_UNIMPLEMENTED);
        }
        EqualsTo eq = (EqualsTo) where;
        requirePkColumn(eq.getLeftExpression(), pk);
        ByteString key = ValueCodec.encode(pk, literalValue(eq.getRightExpression()));
        MiniSQLClient.DeleteResult result = client.delete(tableName, key);
        if (!result.isSuccess()) {
            throw new MiniSQLClientException(
                    "DELETE failed: " + result.getErrorMessage(),
                    result.getErrorCode());
        }
        return SqlResult.updateCount(result.didExist() ? 1 : 0);
    }

    private static List<String> extractProjection(PlainSelect plain, TableSchema schema) {
        List<SelectItem> items = plain.getSelectItems();
        List<String> cols = new ArrayList<>();
        for (SelectItem item : items) {
            String raw = item.toString().trim();
            if (raw.equals("*")) {
                cols.clear();
                for (ColumnSchema col : schema.getColumnsList()) {
                    cols.add(col.getName());
                }
                return cols;
            }
            if (item instanceof SelectExpressionItem) {
                Expression expr = ((SelectExpressionItem) item).getExpression();
                if (expr instanceof Column) {
                    cols.add(((Column) expr).getColumnName());
                    continue;
                }
            }
            throw new MiniSQLClientException(
                    "only plain column projections are supported: " + raw,
                    ErrorCode.ERROR_UNIMPLEMENTED);
        }
        return cols;
    }

    private RangeBounds extractRange(Expression where, ColumnSchema pk, TableSchema schema) {
        if (where == null) {
            return RangeBounds.full();
        }
        if (where instanceof Between) {
            Between between = (Between) where;
            requirePkColumn(between.getLeftExpression(), pk);
            ByteString start = ValueCodec.encode(pk, literalValue(between.getBetweenExpressionStart()));
            ByteString endInclusive = ValueCodec.encode(pk, literalValue(between.getBetweenExpressionEnd()));
            return new RangeBounds(start, keyNextOf(endInclusive));
        }
        if (where instanceof GreaterThanEquals) {
            GreaterThanEquals gte = (GreaterThanEquals) where;
            requirePkColumn(gte.getLeftExpression(), pk);
            return new RangeBounds(
                    ValueCodec.encode(pk, literalValue(gte.getRightExpression())),
                    ByteString.EMPTY);
        }
        if (where instanceof GreaterThan) {
            GreaterThan gt = (GreaterThan) where;
            requirePkColumn(gt.getLeftExpression(), pk);
            return new RangeBounds(
                    keyNextOf(ValueCodec.encode(pk, literalValue(gt.getRightExpression()))),
                    ByteString.EMPTY);
        }
        if (where instanceof MinorThan) {
            MinorThan lt = (MinorThan) where;
            requirePkColumn(lt.getLeftExpression(), pk);
            return new RangeBounds(
                    ByteString.EMPTY,
                    ValueCodec.encode(pk, literalValue(lt.getRightExpression())));
        }
        if (where instanceof MinorThanEquals) {
            MinorThanEquals lte = (MinorThanEquals) where;
            requirePkColumn(lte.getLeftExpression(), pk);
            return new RangeBounds(
                    ByteString.EMPTY,
                    keyNextOf(ValueCodec.encode(pk, literalValue(lte.getRightExpression()))));
        }
        throw new MiniSQLClientException(
                "unsupported WHERE clause: " + where,
                ErrorCode.ERROR_UNIMPLEMENTED);
    }

    private static void requirePkColumn(Expression expr, ColumnSchema pk) {
        if (!(expr instanceof Column)) {
            throw new MiniSQLClientException(
                    "WHERE left side must be a column",
                    ErrorCode.ERROR_INVALID_ARGUMENT);
        }
        String name = ((Column) expr).getColumnName();
        if (!name.equalsIgnoreCase(pk.getName())) {
            throw new MiniSQLClientException(
                    "WHERE clause must filter on primary key " + pk.getName()
                            + ", got " + name,
                    ErrorCode.ERROR_UNIMPLEMENTED);
        }
    }

    private static Object literalValue(Expression expr) {
        if (expr instanceof LongValue) {
            return ((LongValue) expr).getValue();
        }
        if (expr instanceof DoubleValue) {
            return ((DoubleValue) expr).getValue();
        }
        if (expr instanceof StringValue) {
            return ((StringValue) expr).getValue();
        }
        if (expr instanceof NullValue) {
            return null;
        }
        if (expr instanceof SignedExpression) {
            SignedExpression signed = (SignedExpression) expr;
            Object inner = literalValue(signed.getExpression());
            if (signed.getSign() == '-') {
                if (inner instanceof Long) {
                    return -((Long) inner);
                }
                if (inner instanceof Double) {
                    return -((Double) inner);
                }
            }
            return inner;
        }
        throw new MiniSQLClientException(
                "unsupported literal expression: " + expr,
                ErrorCode.ERROR_UNIMPLEMENTED);
    }

    private static ByteString keyNextOf(ByteString key) {
        byte[] arr = key.toByteArray();
        byte[] next = new byte[arr.length + 1];
        System.arraycopy(arr, 0, next, 0, arr.length);
        next[arr.length] = 0;
        return ByteString.copyFrom(next);
    }

    private static Map<String, Object> decodeRow(TableSchema schema,
                                                 Map<String, ByteString> raw,
                                                 List<String> projection) {
        Map<String, Object> decoded = new LinkedHashMap<>();
        for (String col : projection) {
            ColumnSchema colSchema = ValueCodec.findColumn(schema, col);
            ByteString bytes = raw.get(colSchema.getName());
            decoded.put(colSchema.getName(), bytes == null ? null : ValueCodec.decode(colSchema, bytes));
        }
        return decoded;
    }

    private static final class RangeBounds {
        final ByteString start;
        final ByteString end;

        RangeBounds(ByteString start, ByteString end) {
            this.start = start;
            this.end = end;
        }

        static RangeBounds full() {
            return new RangeBounds(ByteString.EMPTY, ByteString.EMPTY);
        }
    }
}
