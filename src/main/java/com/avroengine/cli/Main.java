package com.avroengine.cli;

import com.avroengine.ast.SelectStatement;
import com.avroengine.binder.Binder;
import com.avroengine.binder.BoundQuery;
import com.avroengine.catalog.Catalog;
import com.avroengine.datasource.Row;
import com.avroengine.exec.Operator;
import com.avroengine.exec.QueryExecutor;
import com.avroengine.parser.Parser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** CLI entry point: starts the {@link InteractiveShell}. */
public final class Main {

    private static final int BATCH_SIZE = 1024;

    public static void main(String[] args) {
        if (args.length > 0) {
            System.err.println("This program takes no arguments. Run it without any, then use 'load <file>' at the prompt.");
            System.exit(2);
        }
        try {
            new InteractiveShell(
                    new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                    System.out).run();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Parses, binds, and runs {@code sql} against {@code catalog}, printing the result table. */
    static void executeAndPrint(String sql, Catalog catalog, PrintStream out) {
        SelectStatement stmt = Parser.parse(sql);
        BoundQuery bound = Binder.bind(stmt, catalog);
        Operator plan = QueryExecutor.execute(bound, BATCH_SIZE);

        List<Row> rows = new ArrayList<>();
        while (plan.hasNext()) {
            rows.addAll(plan.next().rows());
        }
        ResultPrinter.print(bound.outputSchema(), rows, out);
    }
}
