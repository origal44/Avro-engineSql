package com.avroengine.datasource;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An ordered list of columns, with fast name-&gt;index lookup. */
public final class Schema {

    private final List<Column> columns;
    private final Map<String, Integer> indexByName;

    public Schema(List<Column> columns) {
        this.columns = List.copyOf(columns);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < this.columns.size(); i++) {
            index.put(this.columns.get(i).name().toLowerCase(), i);
        }
        this.indexByName = Collections.unmodifiableMap(index);
    }

    public List<Column> columns() {
        return columns;
    }

    public int size() {
        return columns.size();
    }

    public Column column(int index) {
        return columns.get(index);
    }

    /** Returns the 0-based index of {@code name} (case-insensitive), or -1 if absent. */
    public int indexOf(String name) {
        Integer idx = indexByName.get(name.toLowerCase());
        return idx == null ? -1 : idx;
    }

    public boolean hasColumn(String name) {
        return indexOf(name) >= 0;
    }

    @Override
    public String toString() {
        return columns.toString();
    }
}
