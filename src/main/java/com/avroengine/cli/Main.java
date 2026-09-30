package com.avroengine.cli;

import com.avroengine.ast.SelectStatement;
import com.avroengine.binder.Binder;
import com.avroengine.binder.BoundQuery;
import com.avroengine.catalog.Catalog;
import com.avroengine.datasource.CsvDataSource;
import com.avroengine.datasource.Row;
import com.avroengine.exec.Operator;
import com.avroengine.exec.QueryExecutor;
import com.avroengine.parser.Parser;
import com.avroengine.stub.avro.AvroToCsvConverter;
import com.avroengine.stub.avro.SampleEmployeesGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** CLI entry point: {@code convert}, {@code query}, and {@code generate-sample} subcommands. */
public final class Main {

    private static final int BATCH_SIZE = 1024;

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        if (args.length == 0) {
            printUsage();
            System.exit(2);
            return;
        }
        switch (args[0]) {
            case "convert" -> runConvert(args);
            case "query" -> runQuery(args);
            case "generate-sample" -> runGenerateSample(args);
            default -> {
                printUsage();
                System.exit(2);
            }
        }
    }

    private static void runConvert(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: convert <file.avro> <out.csv>");
        }
        new AvroToCsvConverter().convert(Path.of(args[1]), Path.of(args[2]));
        System.out.println("Converted " + args[1] + " -> " + args[2]);
    }

    private static void runQuery(String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                    "Usage: query <table-name>=<file.csv> [<table2>=<file2.csv> ...] \"<SQL>\"");
        }
        String sql = args[args.length - 1];

        Catalog catalog = new Catalog();
        for (int i = 1; i < args.length - 1; i++) {
            String spec = args[i];
            int eq = spec.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("Expected <table-name>=<file.csv>, got '" + spec + "'");
            }
            String table = spec.substring(0, eq);
            Path file = Path.of(spec.substring(eq + 1));
            catalog.register(table, new CsvDataSource(file));
        }

        SelectStatement stmt = Parser.parse(sql);
        BoundQuery bound = Binder.bind(stmt, catalog);
        Operator plan = QueryExecutor.execute(bound, BATCH_SIZE);

        List<Row> rows = new ArrayList<>();
        while (plan.hasNext()) {
            rows.addAll(plan.next().rows());
        }
        ResultPrinter.print(bound.outputSchema(), rows, System.out);
    }

    private static void runGenerateSample(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: generate-sample <out.avro>");
        }
        SampleEmployeesGenerator.generate(Path.of(args[1]));
        System.out.println("Wrote sample data to " + args[1]);
    }

    private static void printUsage() {
        System.err.println("""
                Usage:
                  convert <file.avro> <out.csv>
                  query <table-name>=<file.csv> [<table2>=<file2.csv> ...] "<SQL>"
                  generate-sample <out.avro>""");
    }
}
