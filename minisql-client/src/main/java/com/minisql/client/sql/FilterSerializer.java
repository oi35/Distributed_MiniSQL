package com.minisql.client.sql;

public final class FilterSerializer {

    private FilterSerializer() {}

    public static String serialize(Predicate predicate) {
        if (predicate == null || predicate instanceof Predicate.AlwaysTrue) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendPredicate(sb, predicate);
        return sb.toString();
    }

    private static void appendPredicate(StringBuilder sb, Predicate pred) {
        if (pred instanceof Predicate.And) {
            Predicate.And and = (Predicate.And) pred;
            sb.append('(');
            appendPredicate(sb, and.left);
            sb.append(") AND (");
            appendPredicate(sb, and.right);
            sb.append(')');
        } else if (pred instanceof Predicate.Or) {
            Predicate.Or or = (Predicate.Or) pred;
            sb.append('(');
            appendPredicate(sb, or.left);
            sb.append(") OR (");
            appendPredicate(sb, or.right);
            sb.append(')');
        } else if (pred instanceof Predicate.Comparison) {
            Predicate.Comparison cmp = (Predicate.Comparison) pred;
            sb.append(cmp.column).append(' ').append(opToString(cmp.op)).append(' ');
            appendLiteral(sb, cmp.literal);
        } else if (pred instanceof Predicate.AlwaysTrue) {
            sb.append("1=1");
        }
    }

    private static String opToString(Predicate.Op op) {
        switch (op) {
            case EQ:  return "=";
            case NEQ: return "!=";
            case LT:  return "<";
            case LTE: return "<=";
            case GT:  return ">";
            case GTE: return ">=";
            default:  return "=";
        }
    }

    private static void appendLiteral(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("NULL");
        } else if (value instanceof CharSequence) {
            sb.append('\'');
            String s = value.toString();
            sb.append(s.replace("'", "''"));
            sb.append('\'');
        } else {
            sb.append(value);
        }
    }
}
