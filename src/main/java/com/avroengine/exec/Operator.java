package com.avroengine.exec;

import com.avroengine.datasource.Batch;
import com.avroengine.datasource.Schema;

import java.util.Iterator;

/**
 * Iterator pattern: every execution stage is an iterator over {@link Batch},
 * so Scan/Filter/Project can be composed without any stage needing to know
 * how its input batches are produced.
 */
public interface Operator extends Iterator<Batch> {
    Schema schema();
}
