package com.minisql.client.sql;

import com.google.protobuf.ByteString;
import com.minisql.client.MiniSQLClient;
import com.minisql.client.exception.MiniSQLClientException;
import com.minisql.client.schema.TableSchemaCache;
import com.minisql.common.proto.ColumnSchema;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.TableSchema;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Offset;
import net.sf.jsqlparser.statement.select.OrderByElement;
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
    private final JoinExecutor joinExecutor;

    public SqlExecutor(MiniSQLClient client, TableSchemaCache schemas) {
        this.client = Objects.requireNonNull(client, "client");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
        this.joinExecutor = new JoinExecutor(client, schemas);
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
            Object literal = PredicateBuilder.literalValue(values.get(i));
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
            return joinExecutor.execute(plain);
        }
        String tableName = plain.getFromItem().toString();
        TableSchema schema = schemas.get(tableName);
        List<String> projectedColumns = extractProjection(plain, schema);

        Expression where = plain.getWhere();
        Predicate predicate = PredicateBuilder.build(where, schema);

        if (where != null && isSimplePkEquals(where, schema)) {
            return executePointSelect(tableName, schema, (EqualsTo) where, projectedColumns);
        }

        PkRangeExtractor.Range range = PkRangeExtractor.extract(predicate, schema);
        List<String> scanColumns = allColumnNames(schema);
        String filter = FilterSerializer.serialize(predicate);
        List<MiniSQLClient.ScanRow> scanned = client.scan(
                tableName, range.start, range.end, 0, scanColumns, filter);

        List<Map<String, Object>> rows = new ArrayList<>(scanned.size());
        for (MiniSQLClient.ScanRow row : scanned) {
            DecodedRow decoded = decodeRow(schema, row);
            if (!predicate.test(decoded)) {
                continue;
            }
            rows.add(projectRow(decoded, projectedColumns));
        }

        List<OrderByApplier.SortKey> sortKeys = extractOrderBy(plain);
        int limit = extractLimit(plain);
        int offset = extractOffset(plain);
        if (!sortKeys.isEmpty() || limit > 0 || offset > 0) {
            rows = OrderByApplier.apply(rows, sortKeys, limit, offset);
        }

        return SqlResult.rows(projectedColumns, rows);
    }

    private SqlResult executeDelete(Delete delete) {
        String tableName = delete.getTable().getName();
        TableSchema schema = schemas.get(tableName);
        Expression where = delete.getWhere();

        if (where != null && isSimplePkEquals(where, schema)) {
            EqualsTo eq = (EqualsTo) where;
            ColumnSchema pk = ValueCodec.primaryKeyColumn(schema);
            ByteString key = ValueCodec.encode(pk, PredicateBuilder.literalValue(eq.getRightExpression()));
            MiniSQLClient.DeleteResult result = client.delete(tableName, key);
            if (!result.isSuccess()) {
                throw new MiniSQLClientException(
                        "DELETE failed: " + result.getErrorMessage(),
                        result.getErrorCode());
            }
            return SqlResult.updateCount(result.didExist() ? 1 : 0);
        }

        if (where == null) {
            throw new MiniSQLClientException(
                    "DELETE without WHERE is not allowed",
                    ErrorCode.ERROR_INVALID_ARGUMENT);
        }

        Predicate predicate = PredicateBuilder.build(where, schema);
        PkRangeExtractor.Range range = PkRangeExtractor.extract(predicate, schema);
        List<String> scanColumns = allColumnNames(schema);
        List<MiniSQLClient.ScanRow> scanned = client.scan(
                tableName, range.start, range.end, 0, scanColumns);

        int deleted = 0;
        for (MiniSQLClient.ScanRow row : scanned) {
            DecodedRow decoded = decodeRow(schema, row);
            if (!predicate.test(decoded)) {
                continue;
            }
            MiniSQLClient.DeleteResult result = client.delete(tableName, row.getKey());
            if (!result.isSuccess()) {
                throw new MiniSQLClientException(
                        "DELETE failed mid-scan: " + result.getErrorMessage(),
                        result.getErrorCode());
            }
            if (result.didExist()) {
                deleted++;
            }
        }
        LOG.debug("DELETE from {} affected {} rows", tableName, deleted);
        return SqlResult.updateCount(deleted);
    }

    private SqlResult executePointSelect(String tableName, TableSchema schema,
                                         EqualsTo eq, List<String> projectedColumns) {
        ColumnSchema pk = ValueCodec.primaryKeyColumn(schema);
        ByteString key = ValueCodec.encode(pk,
                PredicateBuilder.literalValue(eq.getRightExpression()));
        MiniSQLClient.GetResult got = client.get(tableName, key, projectedColumns);
        if (!got.isFound()) {
            return SqlResult.rows(projectedColumns, Collections.emptyList());
        }
        Map<String, Object> projected = new LinkedHashMap<>();
        for (String colName : projectedColumns) {
            ColumnSchema colSchema = ValueCodec.findColumn(schema, colName);
            ByteString raw = got.getColumns().get(colSchema.getName());
            projected.put(colSchema.getName(),
                    raw == null ? null : ValueCodec.decode(colSchema, raw));
        }
        return SqlResult.rows(projectedColumns, List.of(projected));
    }

    private static boolean isSimplePkEquals(Expression where, TableSchema schema) {
        if (!(where instanceof EqualsTo)) {
            return false;
        }
        EqualsTo eq = (EqualsTo) where;
        if (!(eq.getLeftExpression() instanceof Column)) {
            return false;
        }
        String col = ((Column) eq.getLeftExpression()).getColumnName();
        return col.equalsIgnoreCase(schema.getPrimaryKey());
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

    private static List<String> allColumnNames(TableSchema schema) {
        List<String> cols = new ArrayList<>(schema.getColumnsCount());
        for (ColumnSchema col : schema.getColumnsList()) {
            cols.add(col.getName());
        }
        return cols;
    }

    private static DecodedRow decodeRow(TableSchema schema, MiniSQLClient.ScanRow row) {
        Map<String, ByteString> raw = row.getColumns();
        Map<String, Object> decoded = new LinkedHashMap<>();
        for (ColumnSchema col : schema.getColumnsList()) {
            ByteString value = raw.get(col.getName());
            decoded.put(col.getName(),
                    value == null ? null : ValueCodec.decode(col, value));
        }
        return new DecodedRow(schema, row.getKey(), raw, decoded);
    }

    private static Map<String, Object> projectRow(DecodedRow row, List<String> projection) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String col : projection) {
            out.put(col, row.get(col));
        }
        return out;
    }

    private static List<OrderByApplier.SortKey> extractOrderBy(PlainSelect plain) {
        List<OrderByElement> elements = plain.getOrderByElements();
        if (elements == null || elements.isEmpty()) {
            return Collections.emptyList();
        }
        List<OrderByApplier.SortKey> keys = new ArrayList<>(elements.size());
        for (OrderByElement elem : elements) {
            Expression expr = elem.getExpression();
            if (!(expr instanceof Column)) {
                throw new MiniSQLClientException(
                        "ORDER BY only supports column references: " + expr,
                        ErrorCode.ERROR_UNIMPLEMENTED);
            }
            keys.add(new OrderByApplier.SortKey(
                    ((Column) expr).getColumnName(), elem.isAsc()));
        }
        return keys;
    }

    private static int extractLimit(PlainSelect plain) {
        Limit limit = plain.getLimit();
        if (limit == null || limit.getRowCount() == null) {
            return 0;
        }
        Object val = PredicateBuilder.literalValue(limit.getRowCount());
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return 0;
    }

    private static int extractOffset(PlainSelect plain) {
        Offset offset = plain.getOffset();
        if (offset != null && offset.getOffset() != null) {
            Object val = PredicateBuilder.literalValue(offset.getOffset());
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
        }
        Limit limit = plain.getLimit();
        if (limit != null && limit.getOffset() != null) {
            Object val = PredicateBuilder.literalValue(limit.getOffset());
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
        }
        return 0;
    }
}
