package com.avroengine.exec;

import com.avroengine.ast.Expr;
import com.avroengine.datasource.Batch;
import com.avroengine.datasource.Row;
import com.avroengine.datasource.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Keeps only rows whose predicate evaluates to TRUE (SQL three-valued logic:
 * FALSE and UNKNOWN/NULL are both dropped). Batches that end up empty after
 * filtering are skipped rather than passed downstream.
 */
public final class FilterOperator implements Operator {

    private final Operator upstream;
    private final Expr predicate;
    private final ExprEvaluator evaluator;
    private Batch pending;

    public FilterOperator(Operator upstream, Expr predicate) {
        this.upstream = upstream;
        this.predicate = predicate;
        this.evaluator = new ExprEvaluator(upstream.schema());
        advance();
    }

    @Override
    public Schema schema() {
        return upstream.schema();
    }

    private void advance() {
        while (upstream.hasNext()) {
            Batch batch = upstream.next();
            List<Row> kept = new ArrayList<>();
            for (Row row : batch.rows()) {
                if (Boolean.TRUE.equals(evaluator.evaluate(predicate, row))) {
                    kept.add(row);
                }
            }
            if (!kept.isEmpty()) {
                pending = new Batch(batch.schema(), kept);
                return;
            }
        }
        pending = null;
    }

    @Override
    public boolean hasNext() {
        return pending != null;
    }

    @Override
    public Batch next() {
        if (pending == null) {
            throw new NoSuchElementException();
        }
        Batch result = pending;
        advance();
        return result;
    }
}
