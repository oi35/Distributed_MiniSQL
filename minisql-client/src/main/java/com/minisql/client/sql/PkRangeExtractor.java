package com.minisql.client.sql;

import com.google.protobuf.ByteString;
import com.minisql.common.proto.ColumnSchema;
import com.minisql.common.proto.TableSchema;

import java.util.ArrayList;
import java.util.List;

public final class PkRangeExtractor {

    private PkRangeExtractor() {}

    public static Range extract(Predicate predicate, TableSchema schema) {
        ColumnSchema pk = ValueCodec.primaryKeyColumn(schema);
        List<Predicate.Comparison> pkComparisons = collectPkComparisons(predicate, pk.getName());

        Range range = Range.unbounded();
        for (Predicate.Comparison cmp : pkComparisons) {
            ByteString encoded = ValueCodec.encode(pk, cmp.literal);
            switch (cmp.op) {
                case EQ:
                    range = range.intersect(Range.singleton(encoded));
                    break;
                case GT:
                    range = range.intersect(Range.openStart(next(encoded)));
                    break;
                case GTE:
                    range = range.intersect(Range.openStart(encoded));
                    break;
                case LT:
                    range = range.intersect(Range.openEnd(encoded));
                    break;
                case LTE:
                    range = range.intersect(Range.openEnd(next(encoded)));
                    break;
                case NEQ:
                    // NEQ doesn't narrow a range; leave as-is.
                    break;
                default:
                    break;
            }
        }
        return range;
    }

    private static List<Predicate.Comparison> collectPkComparisons(Predicate node, String pkName) {
        List<Predicate.Comparison> hits = new ArrayList<>();
        walk(node, pkName, hits);
        return hits;
    }

    private static void walk(Predicate node, String pkName, List<Predicate.Comparison> out) {
        if (node instanceof Predicate.And) {
            Predicate.And and = (Predicate.And) node;
            walk(and.left, pkName, out);
            walk(and.right, pkName, out);
            return;
        }
        if (node instanceof Predicate.Comparison) {
            Predicate.Comparison cmp = (Predicate.Comparison) node;
            if (cmp.column.equalsIgnoreCase(pkName)) {
                out.add(cmp);
            }
        }
        // Or / AlwaysTrue / non-PK comparisons do not contribute to the range.
    }

    private static ByteString next(ByteString key) {
        byte[] src = key.toByteArray();
        byte[] dst = new byte[src.length + 1];
        System.arraycopy(src, 0, dst, 0, src.length);
        return ByteString.copyFrom(dst);
    }

    public static final class Range {
        public final ByteString start;
        public final ByteString end;

        private Range(ByteString start, ByteString end) {
            this.start = start;
            this.end = end;
        }

        public static Range unbounded() {
            return new Range(ByteString.EMPTY, ByteString.EMPTY);
        }

        public static Range openStart(ByteString start) {
            return new Range(start, ByteString.EMPTY);
        }

        public static Range openEnd(ByteString end) {
            return new Range(ByteString.EMPTY, end);
        }

        public static Range singleton(ByteString key) {
            byte[] src = key.toByteArray();
            byte[] after = new byte[src.length + 1];
            System.arraycopy(src, 0, after, 0, src.length);
            return new Range(key, ByteString.copyFrom(after));
        }

        Range intersect(Range other) {
            ByteString s = maxStart(this.start, other.start);
            ByteString e = minEnd(this.end, other.end);
            return new Range(s, e);
        }

        private static ByteString maxStart(ByteString a, ByteString b) {
            if (a.isEmpty()) return b;
            if (b.isEmpty()) return a;
            return compareUnsigned(a, b) >= 0 ? a : b;
        }

        private static ByteString minEnd(ByteString a, ByteString b) {
            if (a.isEmpty()) return b;
            if (b.isEmpty()) return a;
            return compareUnsigned(a, b) <= 0 ? a : b;
        }

        private static int compareUnsigned(ByteString a, ByteString b) {
            int n = Math.min(a.size(), b.size());
            for (int i = 0; i < n; i++) {
                int av = a.byteAt(i) & 0xFF;
                int bv = b.byteAt(i) & 0xFF;
                if (av != bv) return Integer.compare(av, bv);
            }
            return Integer.compare(a.size(), b.size());
        }
    }
}
