package com.minisql.client.sql;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SqlResult {

    public enum Kind { UPDATE, QUERY }

    private final Kind kind;
    private final int updateCount;
    private final List<String> columns;
    private final List<Map<String, Object>> rows;

    private SqlResult(Kind kind, int updateCount,
                      List<String> columns, List<Map<String, Object>> rows) {
        this.kind = kind;
        this.updateCount = updateCount;
        this.columns = columns;
        this.rows = rows;
    }

    public static SqlResult updateCount(int count) {
        return new SqlResult(Kind.UPDATE, count, Collections.emptyList(), Collections.emptyList());
    }

    public static SqlResult rows(List<String> columns, List<Map<String, Object>> rows) {
        return new SqlResult(Kind.QUERY, 0,
                Collections.unmodifiableList(columns),
                Collections.unmodifiableList(rows));
    }

    public Kind getKind() { return kind; }

    public int getUpdateCount() {
        if (kind != Kind.UPDATE) {
            throw new IllegalStateException("not an update result");
        }
        return updateCount;
    }

    public List<String> getColumns() {
        if (kind != Kind.QUERY) {
            throw new IllegalStateException("not a query result");
        }
        return columns;
    }

    public List<Map<String, Object>> getRows() {
        if (kind != Kind.QUERY) {
            throw new IllegalStateException("not a query result");
        }
        return rows;
    }

    public int size() {
        return kind == Kind.UPDATE ? updateCount : rows.size();
    }
}
