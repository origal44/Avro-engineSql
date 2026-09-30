package com.avroengine.catalog;

import com.avroengine.datasource.DataSource;

import java.util.HashMap;
import java.util.Map;

/** Simple in-memory registry mapping table name to {@link DataSource}. */
public final class Catalog {

    private final Map<String, DataSource> tables = new HashMap<>();

    public void register(String tableName, DataSource dataSource) {
        tables.put(tableName.toLowerCase(), dataSource);
    }

    /** Returns the table's DataSource, or {@code null} if not registered. */
    public DataSource lookup(String tableName) {
        return tables.get(tableName.toLowerCase());
    }

    public boolean contains(String tableName) {
        return tables.containsKey(tableName.toLowerCase());
    }
}
