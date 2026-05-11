package com.minisql.client.sql;

import com.google.protobuf.ByteString;
import com.minisql.client.MiniSQLClient;
import com.minisql.client.exception.MiniSQLClientException;
import com.minisql.client.schema.TableSchemaCache;
import com.minisql.common.proto.ColumnSchema;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.TableSchema;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Offset;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class JoinExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(JoinExecutor.class);

    private final MiniSQLClient client;
    private final TableSchemaCache schemas;
    private final TableScanner scanner;

    public JoinExecutor(MiniSQLClient client, TableSchemaCache schemas) {
        this.client = Objects.requireNonNull(client, "client");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
        this.scanner = new TableScanner(client);
    }

    public SqlResult execute(PlainSelect plain) {
        JoinPlan plan = plan(plain);

        Expression where = plain.getWhere();
        Predicate wherePred = (where != null)
                ? PredicateBuilder.build(where, plan.left.schema, plan.right.schema)
                : Predicate.alwaysTrue();

        JoinPredicateSplitter.SplitResult split =
                JoinPredicateSplitter.split(wherePred, plan.left.schema, plan.right.schema);

        ExecutorService executor = client.scanExecutor();

        CompletableFuture<List<DecodedRow>> leftFuture = CompletableFuture.supplyAsync(
                () -> scanWithFilter(plan.left.schema, split.leftOnly), executor);
        CompletableFuture<List<DecodedRow>> rightFuture = CompletableFuture.supplyAsync(
                () -> scanWithFilter(plan.right.schema, split.rightOnly), executor);

        List<DecodedRow> leftRows = leftFuture.join();
        List<DecodedRow> rightRows = rightFuture.join();

        Map<ByteString, List<DecodedRow>> buildSide = new HashMap<>();
        for (DecodedRow row : leftRows) {
            ByteString key = row.rawValue(plan.left.joinColumn.getName());
            if (key == null) continue;
            buildSide.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        List<Map<String, Object>> outRows = new ArrayList<>();
        for (DecodedRow right : rightRows) {
            ByteString key = right.rawValue(plan.right.joinColumn.getName());
            if (key == null) continue;
            List<DecodedRow> matches = buildSide.get(key);
            if (matches == null) continue;
            for (DecodedRow left : matches) {
                if (!split.postJoin.test(new JoinedRow(left, right, plan))) {
                    continue;
                }
                outRows.add(project(plan, left, right));
            }
        }

        LOG.debug("hash join {} x {} -> {} rows",
                plan.left.schema.getTableName(), plan.right.schema.getTableName(),
                outRows.size());

        List<OrderByApplier.SortKey> sortKeys = extractOrderBy(plain);
        int limit = extractLimit(plain);
        int offset = extractOffset(plain);
        if (!sortKeys.isEmpty() || limit > 0 || offset > 0) {
            outRows = OrderByApplier.apply(outRows, sortKeys, limit, offset);
        }

        return SqlResult.rows(plan.outputColumns, outRows);
    }

    private List<DecodedRow> scanWithFilter(TableSchema schema, Predicate filter) {
        List<DecodedRow> result = new ArrayList<>();
        for (DecodedRow row : scanner.scanAll(schema)) {
            if (filter.test(row)) {
                result.add(row);
            }
        }
        return result;
    }

    private JoinPlan plan(PlainSelect plain) {
        List<Join> joins = plain.getJoins();
        if (joins == null || joins.size() != 1) {
            throw new MiniSQLClientException(
                    "only exactly one JOIN is supported",
                    ErrorCode.ERROR_UNIMPLEMENTED);
        }
        Join join = joins.get(0);
        if (join.isOuter() || join.isLeft() || join.isRight() || join.isFull()
                || join.isCross() || join.isSemi()) {
            throw new MiniSQLClientException(
                    "only INNER JOIN is supported",
                    ErrorCode.ERROR_UNIMPLEMENTED);
        }

        TableRef leftRef = resolveTableRef(plain.getFromItem());
        TableRef rightRef = resolveTableRef(join.getRightItem());

        Collection<Expression> onExprs = join.getOnExpressions();
        if (onExprs == null || onExprs.isEmpty()) {
            throw new MiniSQLClientException(
                    "JOIN requires an ON clause",
                    ErrorCode.ERROR_INVALID_ARGUMENT);
        }
        if (onExprs.size() != 1) {
            throw new MiniSQLClientException(
                    "JOIN supports only a single ON equality",
                    ErrorCode.ERROR_UNIMPLEMENTED);
        }
        Expression on = onExprs.iterator().next();
        if (!(on instanceof EqualsTo)) {
            throw new MiniSQLClientException(
                    "JOIN ON must be an equality: " + on,
                    ErrorCode.ERROR_UNIMPLEMENTED);
        }
        EqualsTo eq = (EqualsTo) on;
        JoinKeyRef leftKey = resolveJoinKey(eq.getLeftExpression(), leftRef, rightRef);
        JoinKeyRef rightKey = resolveJoinKey(eq.getRightExpression(), leftRef, rightRef);
        if (leftKey.owner == rightKey.owner) {
            throw new MiniSQLClientException(
                    "JOIN ON must reference both tables",
                    ErrorCode.ERROR_INVALID_ARGUMENT);
        }
        if (leftKey.owner != leftRef) {
            JoinKeyRef swap = leftKey;
            leftKey = rightKey;
            rightKey = swap;
        }
        ColumnSchema leftCol = ValueCodec.findColumn(leftRef.schema, leftKey.columnName);
        ColumnSchema rightCol = ValueCodec.findColumn(rightRef.schema, rightKey.columnName);
        if (!sameEncoding(leftCol, rightCol)) {
            throw new MiniSQLClientException(
                    "JOIN columns have incompatible types: "
                            + leftCol.getType() + " vs " + rightCol.getType(),
                    ErrorCode.ERROR_INVALID_ARGUMENT);
        }

        JoinSide leftSide = new JoinSide(leftRef, leftCol);
        JoinSide rightSide = new JoinSide(rightRef, rightCol);
        List<Projection> projections = buildProjections(plain.getSelectItems(), leftRef, rightRef);
        List<String> outputColumns = new ArrayList<>(projections.size());
        for (Projection p : projections) {
            outputColumns.add(p.outputName);
        }
        return new JoinPlan(leftSide, rightSide, projections, outputColumns);
    }

    private TableRef resolveTableRef(FromItem item) {
        if (!(item instanceof Table)) {
            throw new MiniSQLClientException(
                    "subqueries in FROM are not supported",
                    ErrorCode.ERROR_UNIMPLEMENTED);
        }
        Table table = (Table) item;
        String name = table.getName();
        TableSchema schema = schemas.get(name);
        String alias = table.getAlias() != null ? table.getAlias().getName() : name;
        return new TableRef(name, alias, schema);
    }

    private static JoinKeyRef resolveJoinKey(Expression expr, TableRef left, TableRef right) {
        if (!(expr instanceof Column)) {
            throw new MiniSQLClientException(
                    "JOIN ON must reference columns directly",
                    ErrorCode.ERROR_UNIMPLEMENTED);
        }
        Column column = (Column) expr;
        String colName = column.getColumnName();
        Table tableRef = column.getTable();
        if (tableRef != null && tableRef.getName() != null) {
            String qualifier = tableRef.getName();
            if (matches(qualifier, left)) {
                return new JoinKeyRef(left, colName);
            }
            if (matches(qualifier, right)) {
                return new JoinKeyRef(right, colName);
            }
            throw new MiniSQLClientException(
                    "unknown table qualifier in ON clause: " + qualifier,
                    ErrorCode.ERROR_INVALID_ARGUMENT);
        }
        boolean inLeft = hasColumn(left.schema, colName);
        boolean inRight = hasColumn(right.schema, colName);
        if (inLeft && inRight) {
            throw new MiniSQLClientException(
                    "ambiguous column in ON clause: " + colName,
                    ErrorCode.ERROR_INVALID_ARGUMENT);
        }
        if (inLeft) {
            return new JoinKeyRef(left, colName);
        }
        if (inRight) {
            return new JoinKeyRef(right, colName);
        }
        throw new MiniSQLClientException(
                "column " + colName + " not found in either table",
                ErrorCode.ERROR_NOT_FOUND);
    }

    private static List<Projection> buildProjections(List<SelectItem> items,
                                                     TableRef left, TableRef right) {
        List<Projection> projections = new ArrayList<>();
        for (SelectItem item : items) {
            String raw = item.toString().trim();
            if (raw.equals("*")) {
                projections.clear();
                expandStar(left, projections);
                expandStar(right, projections);
                return projections;
            }
            if (!(item instanceof SelectExpressionItem)) {
                throw new MiniSQLClientException(
                        "unsupported SELECT item: " + raw,
                        ErrorCode.ERROR_UNIMPLEMENTED);
            }
            SelectExpressionItem sei = (SelectExpressionItem) item;
            Expression expr = sei.getExpression();
            if (!(expr instanceof Column)) {
                throw new MiniSQLClientException(
                        "only plain column projections are supported: " + raw,
                        ErrorCode.ERROR_UNIMPLEMENTED);
            }
            Column col = (Column) expr;
            String colName = col.getColumnName();
            Table tableRef = col.getTable();
            TableRef owner;
            if (tableRef != null && tableRef.getName() != null) {
                String qualifier = tableRef.getName();
                if (matches(qualifier, left)) {
                    owner = left;
                } else if (matches(qualifier, right)) {
                    owner = right;
                } else {
                    throw new MiniSQLClientException(
                            "unknown table qualifier: " + qualifier,
                            ErrorCode.ERROR_INVALID_ARGUMENT);
                }
            } else {
                boolean inLeft = hasColumn(left.schema, colName);
                boolean inRight = hasColumn(right.schema, colName);
                if (inLeft && inRight) {
                    throw new MiniSQLClientException(
                            "ambiguous column: " + colName,
                            ErrorCode.ERROR_INVALID_ARGUMENT);
                }
                owner = inLeft ? left : inRight ? right : null;
                if (owner == null) {
                    throw new MiniSQLClientException(
                            "column " + colName + " not found",
                            ErrorCode.ERROR_NOT_FOUND);
                }
            }
            String outputName;
            if (sei.getAlias() != null) {
                outputName = sei.getAlias().getName();
            } else if (tableRef != null) {
                outputName = owner.alias + "." + colName;
            } else {
                outputName = colName;
            }
            projections.add(new Projection(owner, colName, outputName));
        }
        return projections;
    }

    private static void expandStar(TableRef ref, List<Projection> out) {
        for (ColumnSchema col : ref.schema.getColumnsList()) {
            out.add(new Projection(ref, col.getName(), ref.alias + "." + col.getName()));
        }
    }

    private static Map<String, Object> project(JoinPlan plan, DecodedRow left, DecodedRow right) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Projection p : plan.projections) {
            DecodedRow source = p.owner == plan.left.ref ? left : right;
            out.put(p.outputName, source.get(p.columnName));
        }
        return out;
    }

    private static boolean matches(String qualifier, TableRef ref) {
        return qualifier.equalsIgnoreCase(ref.alias) || qualifier.equalsIgnoreCase(ref.name);
    }

    private static boolean hasColumn(TableSchema schema, String name) {
        for (ColumnSchema col : schema.getColumnsList()) {
            if (col.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameEncoding(ColumnSchema a, ColumnSchema b) {
        return normalize(a.getType()).equals(normalize(b.getType()));
    }

    private static String normalize(String t) {
        String up = t.toUpperCase();
        int paren = up.indexOf('(');
        up = paren >= 0 ? up.substring(0, paren) : up;
        switch (up) {
            case "INT":
            case "INTEGER":
            case "BIGINT":
            case "LONG":
            case "SMALLINT":
            case "TINYINT":
                return "INT";
            case "VARCHAR":
            case "CHAR":
            case "TEXT":
            case "STRING":
                return "STR";
            case "DOUBLE":
            case "FLOAT":
            case "REAL":
                return "DBL";
            default:
                return up;
        }
    }

    private static final class TableRef {
        final String name;
        final String alias;
        final TableSchema schema;

        TableRef(String name, String alias, TableSchema schema) {
            this.name = name;
            this.alias = alias;
            this.schema = schema;
        }
    }

    private static final class JoinSide {
        final TableRef ref;
        final TableSchema schema;
        final ColumnSchema joinColumn;

        JoinSide(TableRef ref, ColumnSchema joinColumn) {
            this.ref = ref;
            this.schema = ref.schema;
            this.joinColumn = joinColumn;
        }
    }

    private static final class JoinKeyRef {
        final TableRef owner;
        final String columnName;

        JoinKeyRef(TableRef owner, String columnName) {
            this.owner = owner;
            this.columnName = columnName;
        }
    }

    private static final class Projection {
        final TableRef owner;
        final String columnName;
        final String outputName;

        Projection(TableRef owner, String columnName, String outputName) {
            this.owner = owner;
            this.columnName = columnName;
            this.outputName = outputName;
        }
    }

    private static final class JoinPlan {
        final JoinSide left;
        final JoinSide right;
        final List<Projection> projections;
        final List<String> outputColumns;

        JoinPlan(JoinSide left, JoinSide right,
                 List<Projection> projections, List<String> outputColumns) {
            this.left = left;
            this.right = right;
            this.projections = projections;
            this.outputColumns = outputColumns;
        }
    }

    private static final class JoinedRow extends DecodedRow {
        private final DecodedRow left;
        private final DecodedRow right;

        JoinedRow(DecodedRow left, DecodedRow right, JoinPlan plan) {
            super(left.schema(), left.key(), left.rawColumns(), left.columns());
            this.left = left;
            this.right = right;
        }

        @Override
        public Object get(String column) {
            Object val = left.get(column);
            if (val != null) return val;
            return right.get(column);
        }
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
        if (limit == null || limit.getRowCount() == null) return 0;
        Object val = PredicateBuilder.literalValue(limit.getRowCount());
        return (val instanceof Number) ? ((Number) val).intValue() : 0;
    }

    private static int extractOffset(PlainSelect plain) {
        Offset offset = plain.getOffset();
        if (offset != null && offset.getOffset() != null) {
            Object val = PredicateBuilder.literalValue(offset.getOffset());
            if (val instanceof Number) return ((Number) val).intValue();
        }
        Limit limit = plain.getLimit();
        if (limit != null && limit.getOffset() != null) {
            Object val = PredicateBuilder.literalValue(limit.getOffset());
            if (val instanceof Number) return ((Number) val).intValue();
        }
        return 0;
    }
}
