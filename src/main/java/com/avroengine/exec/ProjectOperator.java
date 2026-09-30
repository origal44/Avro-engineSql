package com.avroengine.exec;

import com.avroengine.ast.SelectItem;
import com.avroengine.datasource.Batch;
import com.avroengine.datasource.Row;
import com.avroengine.datasource.Schema;

import java.util.ArrayList;
import java.util.List;

/** Evaluates each SELECT-list expression per row, producing the query's output schema. */
public final class ProjectOperator implements Operator {

    private final Operator upstream;
    private final List<SelectItem> items;
    private final Schema outputSchema;
    private final ExprEvaluator evaluator;

    public ProjectOperator(Operator upstream, List<SelectItem> items, Schema outputSchema) {
        this.upstream = upstream;
        this.items = items;
        this.outputSchema = outputSchema;
        this.evaluator = new ExprEvaluator(upstream.schema());
    }

    @Override
    public Schema schema() {
        return outputSchema;
    }

    @Override
    public boolean hasNext() {
        return upstream.hasNext();
    }

    @Override
    public Batch next() {
        Batch batch = upstream.next();
        List<Row> outRows = new ArrayList<>(batch.size());
        for (Row row : batch.rows()) {
            Object[] values = new Object[items.size()];
            for (int i = 0; i < items.size(); i++) {
                values[i] = evaluator.evaluate(items.get(i).expr(), row);
            }
            outRows.add(new Row(values));
        }
        return new Batch(outputSchema, outRows);
    }
}
