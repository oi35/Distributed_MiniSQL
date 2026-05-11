package com.minisql.client.sql;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class OrderByApplier {

    private OrderByApplier() {}

    public static List<Map<String, Object>> apply(
            List<Map<String, Object>> rows,
            List<SortKey> sortKeys,
            int limit,
            int offset) {

        if (rows.isEmpty()) {
            return rows;
        }

        List<Map<String, Object>> result = rows;

        if (sortKeys != null && !sortKeys.isEmpty()) {
            result = new ArrayList<>(result);
            Comparator<Map<String, Object>> cmp = buildComparator(sortKeys);
            result.sort(cmp);
        }

        if (offset > 0) {
            if (offset >= result.size()) {
                return List.of();
            }
            result = result.subList(offset, result.size());
        }

        if (limit > 0 && limit < result.size()) {
            result = result.subList(0, limit);
        }

        return new ArrayList<>(result);
    }

    private static Comparator<Map<String, Object>> buildComparator(List<SortKey> sortKeys) {
        return (a, b) -> {
            for (SortKey key : sortKeys) {
                Object va = a.get(key.column);
                Object vb = b.get(key.column);
                if (va == null && vb == null) continue;
                if (va == null) return key.ascending ? -1 : 1;
                if (vb == null) return key.ascending ? 1 : -1;
                int c = Predicate.compareValues(va, vb);
                if (!key.ascending) c = -c;
                if (c != 0) return c;
            }
            return 0;
        };
    }

    public static final class SortKey {
        public final String column;
        public final boolean ascending;

        public SortKey(String column, boolean ascending) {
            this.column = column;
            this.ascending = ascending;
        }
    }
}
