package com.minisql.client.sql;

import com.minisql.client.exception.MiniSQLClientException;
import com.minisql.common.proto.ErrorCode;

import java.util.Objects;

public abstract class Predicate {

    public abstract boolean test(DecodedRow row);

    public static Predicate and(Predicate left, Predicate right) {
        return new And(left, right);
    }

    public static Predicate or(Predicate left, Predicate right) {
        return new Or(left, right);
    }

    public static Predicate comparison(String column, Op op, Object literal) {
        return new Comparison(column, op, literal);
    }

    public static Predicate alwaysTrue() {
        return new AlwaysTrue();
    }

    public enum Op {
        EQ, NEQ, LT, LTE, GT, GTE
    }

    public static final class AlwaysTrue extends Predicate {
        @Override
        public boolean test(DecodedRow row) { return true; }
    }

    public static final class And extends Predicate {
        public final Predicate left;
        public final Predicate right;

        And(Predicate left, Predicate right) {
            this.left = Objects.requireNonNull(left);
            this.right = Objects.requireNonNull(right);
        }

        @Override
        public boolean test(DecodedRow row) {
            return left.test(row) && right.test(row);
        }
    }

    public static final class Or extends Predicate {
        public final Predicate left;
        public final Predicate right;

        Or(Predicate left, Predicate right) {
            this.left = Objects.requireNonNull(left);
            this.right = Objects.requireNonNull(right);
        }

        @Override
        public boolean test(DecodedRow row) {
            return left.test(row) || right.test(row);
        }
    }

    public static final class Comparison extends Predicate {
        public final String column;
        public final Op op;
        public final Object literal;

        Comparison(String column, Op op, Object literal) {
            this.column = Objects.requireNonNull(column);
            this.op = Objects.requireNonNull(op);
            this.literal = literal;
        }

        @Override
        public boolean test(DecodedRow row) {
            Object actual = row.get(column);
            if (actual == null || literal == null) {
                return false;
            }
            int cmp = compareValues(actual, literal);
            switch (op) {
                case EQ:  return cmp == 0;
                case NEQ: return cmp != 0;
                case LT:  return cmp < 0;
                case LTE: return cmp <= 0;
                case GT:  return cmp > 0;
                case GTE: return cmp >= 0;
                default:
                    throw new MiniSQLClientException(
                            "unsupported comparison op " + op,
                            ErrorCode.ERROR_UNIMPLEMENTED);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static int compareValues(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        if (a instanceof Boolean && b instanceof Boolean) {
            return Boolean.compare((Boolean) a, (Boolean) b);
        }
        if (a instanceof CharSequence && b instanceof CharSequence) {
            return a.toString().compareTo(b.toString());
        }
        if (a.getClass() == b.getClass() && a instanceof Comparable) {
            return ((Comparable) a).compareTo(b);
        }
        throw new MiniSQLClientException(
                "cannot compare " + a.getClass().getSimpleName()
                        + " with " + b.getClass().getSimpleName(),
                ErrorCode.ERROR_INVALID_ARGUMENT);
    }
}
