package com.avroengine.datasource;

import java.util.Iterator;

/**
 * Strategy interface for a batch-oriented row source. The engine core
 * depends only on this interface -- never on CSV or Avro directly -- so a
 * source can be swapped (e.g. CSV today, a handwritten Avro decoder later)
 * without touching lexer/parser/binder/exec code.
 */
public interface DataSource {

    Schema schema();

    /** Returns a fresh iterator over batches of at most {@code batchSize} rows each. */
    Iterator<Batch> batches(int batchSize);
}
