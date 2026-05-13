package com.minisql.client.sql;

import com.minisql.client.exception.MiniSQLClientException;
import com.minisql.common.proto.ErrorCode;
import com.minisql.common.proto.TableSchema;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.schema.Column;

public final class PredicateBuilder {

    private PredicateBuilder() {}

    public static Predicate build(Expression where, TableSchema schema) {
        if (where == null) {
            return Predicate.alwaysTrue();
        }
        if (where instanceof AndExpression) {
            AndExpression and = (AndExpression) where;
            return Predicate.and(build(and.getLeftExpression(), schema),
                    build(and.getRightExpression(), schema));
        }
        if (where instanceof OrExpression) {
            OrExpression or = (OrExpression) where;
            return Predicate.or(build(or.getLeftExpression(), schema),
                    build(or.getRightExpression(), schema));
        }
        if (where instanceof Between) {
            Between b = (Between) where;
            String col = requireColumn(b.getLeftExpression(), schema);
            Object lo = literalValue(b.getBetweenExpressionStart());
            Object hi = literalValue(b.getBetweenExpressionEnd());
            return Predicate.and(
                    Predicate.comparison(col, Predicate.Op.GTE, lo),
                    Predicate.comparison(col, Predicate.Op.LTE, hi));
        }
        return comparison(where, schema);
    }

    public static Predicate build(Expression where, TableSchema left, TableSchema right) {
        if (where == null) {
            return Predicate.alwaysTrue();
        }
        if (where instanceof AndExpression) {
            AndExpression and = (AndExpression) where;
            return Predicate.and(build(and.getLeftExpression(), left, right),
                    build(and.getRightExpression(), left, right));
        }
        if (where instanceof OrExpression) {
            OrExpression or = (OrExpression) where;
            return Predicate.or(build(or.getLeftExpression(), left, right),
                    build(or.getRightExpression(), left, right));
        }
        if (where instanceof Between) {
            Between b = (Between) where;
            String col = requireColumnEither(b.getLeftExpression(), left, right);
            Object lo = literalValue(b.getBetweenExpressionStart());
            Object hi = literalValue(b.getBetweenExpressionEnd());
            return Predicate.and(
                    Predicate.comparison(col, Predicate.Op.GTE, lo),
                    Predicate.comparison(col, Predicate.Op.LTE, hi));
        }
        return comparisonEither(where, left, right);
    }

    private static Predicate comparison(Expression expr, TableSchema schema) {
        String col;
        Object literal;
        Predicate.Op op;
        if (expr instanceof EqualsTo) {
            EqualsTo e = (EqualsTo) expr;
            col = requireColumn(e.getLeftExpression(), schema);
            literal = literalValue(e.getRightExpression());
            op = Predicate.Op.EQ;
        } else if (expr instanceof NotEqualsTo) {
            NotEqualsTo e = (NotEqualsTo) expr;
            col = requireColumn(e.getLeftExpression(), schema);
            literal = literalValue(e.getRightExpression());
            op = Predicate.Op.NEQ;
        } else if (expr instanceof GreaterThan) {
            GreaterThan e = (GreaterThan) expr;
            col = requireColumn(e.getLeftExpression(), schema);
            literal = literalValue(e.getRightExpression());
            op = Predicate.Op.GT;
        } else if (expr instanceof GreaterThanEquals) {
            GreaterThanEquals e = (GreaterThanEquals) expr;
            col = requireColumn(e.getLeftExpression(), schema);
            literal = literalValue(e.getRightExpression());
            op = Predicate.Op.GTE;
        } else if (expr instanceof MinorThan) {
            MinorThan e = (MinorThan) expr;
            col = requireColumn(e.getLeftExpression(), schema);
            literal = literalValue(e.getRightExpression());
            op = Predicate.Op.LT;
        } else if (expr instanceof MinorThanEquals) {
            MinorThanEquals e = (MinorThanEquals) expr;
            col = requireColumn(e.getLeftExpression(), schema);
            literal = literalValue(e.getRightExpression());
            op = Predicate.Op.LTE;
        } else {
            throw new MiniSQLClientException(
                    "unsupported WHERE expression: " + expr,
                    ErrorCode.ERROR_UNIMPLEMENTED);
        }
        return Predicate.comparison(col, op, literal);
    }

    static String requireColumn(Expression expr, TableSchema schema) {
        if (!(expr instanceof Column)) {
            throw new MiniSQLClientException(
                    "WHERE left side must be a column reference",
                    ErrorCode.ERROR_INVALID_ARGUMENT);
        }
        String name = ((Column) expr).getColumnName();
        return ValueCodec.findColumn(schema, name).getName();
    }

    static Object literalValue(Expression expr) {
        if (expr instanceof LongValue)   return ((LongValue) expr).getValue();
        if (expr instanceof DoubleValue) return ((DoubleValue) expr).getValue();
        if (expr instanceof StringValue) return ((StringValue) expr).getValue();
        if (expr instanceof NullValue)   return null;
        if (expr instanceof SignedExpression) {
            SignedExpression s = (SignedExpression) expr;
            Object inner = literalValue(s.getExpression());
            if (s.getSign() == '-') {
                if (inner instanceof Long)   return -((Long) inner);
                if (inner instanceof Double) return -((Double) inner);
            }
            return inner;
        }
        throw new MiniSQLClientException(
                "unsupported literal expression: " + expr,
                ErrorCode.ERROR_UNIMPLEMENTED);
    }

    private static String requireColumnEither(Expression expr, TableSchema left, TableSchema right) {
        if (!(expr instanceof Column)) {
            throw new MiniSQLClientException(
                    "WHERE left side must be a column reference",
                    ErrorCode.ERROR_INVALID_ARGUMENT);
        }
        String name = ((Column) expr).getColumnName();
        String found = findColumnInEither(name, left, right);
        if (found == null) {
            throw new MiniSQLClientException(
                    "column " + name + " not found in either table",
                    ErrorCode.ERROR_NOT_FOUND);
        }
        return found;
    }

    private static Predicate comparisonEither(Expression expr, TableSchema left, TableSchema right) {
        String col;
        Object literal;
        Predicate.Op op;
        if (expr instanceof EqualsTo) {
            EqualsTo e = (EqualsTo) expr;
            col = requireColumnEither(e.getLeftExpression(), left, right);
            literal = literalValue(e.getRightExpression());
            op = Predicate.Op.EQ;
        } else if (expr instanceof NotEqualsTo) {
            NotEqualsTo e = (NotEqualsTo) expr;
            col = requireColumnEither(e.getLeftExpression(), left, right);
            literal = literalValue(e.getRightExpression());
            op = Predicate.Op.NEQ;
        } else if (expr instanceof GreaterThan) {
            GreaterThan e = (GreaterThan) expr;
            col = requireColumnEither(e.getLeftExpression(), left, right);
            literal = literalValue(e.getRightExpression());
            op = Predicate.Op.GT;
        } else if (expr instanceof GreaterThanEquals) {
            GreaterThanEquals e = (GreaterThanEquals) expr;
            col = requireColumnEither(e.getLeftExpression(), left, right);
            literal = literalValue(e.getRightExpression());
            op = Predicate.Op.GTE;
        } else if (expr instanceof MinorThan) {
            MinorThan e = (MinorThan) expr;
            col = requireColumnEither(e.getLeftExpression(), left, right);
            literal = literalValue(e.getRightExpression());
            op = Predicate.Op.LT;
        } else if (expr instanceof MinorThanEquals) {
            MinorThanEquals e = (MinorThanEquals) expr;
            col = requireColumnEither(e.getLeftExpression(), left, right);
            literal = literalValue(e.getRightExpression());
            op = Predicate.Op.LTE;
        } else {
            throw new MiniSQLClientException(
                    "unsupported WHERE expression: " + expr,
                    ErrorCode.ERROR_UNIMPLEMENTED);
        }
        return Predicate.comparison(col, op, literal);
    }

    private static String findColumnInEither(String name, TableSchema left, TableSchema right) {
        for (com.minisql.common.proto.ColumnSchema col : left.getColumnsList()) {
            if (col.getName().equalsIgnoreCase(name)) return col.getName();
        }
        for (com.minisql.common.proto.ColumnSchema col : right.getColumnsList()) {
            if (col.getName().equalsIgnoreCase(name)) return col.getName();
        }
        return null;
    }
}
