package com.avroengine.exec;

import com.avroengine.datasource.Batch;
import com.avroengine.datasource.DataSource;
import com.avroengine.datasource.Schema;

import java.util.Iterator;

/** Leaf operator: pulls batches straight from a {@link DataSource} (Strategy pattern at the source). */
public final class ScanOperator implements Operator {

    private final Schema schema;
    private final Iterator<Batch> batches;

    public ScanOperator(DataSource source, int batchSize) {
        this.schema = source.schema();
        this.batches = source.batches(batchSize);
    }

    @Override
    public Schema schema() {
        return schema;
    }

    @Override
    public boolean hasNext() {
        return batches.hasNext();
    }

    @Override
    public Batch next() {
        return batches.next();
    }
}
