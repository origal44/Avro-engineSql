package com.avroengine.exec;

import com.avroengine.binder.BoundQuery;

/** Wires a {@link BoundQuery} into the Scan -&gt; [Filter] -&gt; Project operator pipeline. */
public final class QueryExecutor {

    private QueryExecutor() {
    }

    public static Operator execute(BoundQuery query, int batchSize) {
        Operator plan = new ScanOperator(query.source(), batchSize);
        if (query.where() != null) {
            plan = new FilterOperator(plan, query.where());
        }
        plan = new ProjectOperator(plan, query.projections(), query.outputSchema());
        return plan;
    }
}
