package com.avroengine.datasource;

import java.util.List;

/** A batch of rows sharing one {@link Schema}. Operators pass these, not single rows. */
public record Batch(Schema schema, List<Row> rows) {

    public int size() {
        return rows.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
