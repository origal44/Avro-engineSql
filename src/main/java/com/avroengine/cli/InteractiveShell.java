package com.avroengine.cli;

import com.avroengine.binder.BindException;
import com.avroengine.catalog.Catalog;
import com.avroengine.datasource.Column;
import com.avroengine.datasource.CsvDataSource;
import com.avroengine.datasource.DataSource;
import com.avroengine.datasource.DataType;
import com.avroengine.lexer.LexException;
import com.avroengine.parser.ParseException;
import com.avroengine.stub.avro.AvroToCsvConverter;
import com.avroengine.stub.avro.SampleEmployeesGenerator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Beginner-friendly interactive mode: greets the user, explains each step,
 * loads Avro/CSV files as tables, and runs SQL typed at a prompt.
 */
public final class InteractiveShell {

    private static final String SQL_SHAPE = "SELECT <fields> FROM <table> [WHERE <condition>]";

    private final BufferedReader in;
    private final PrintStream out;
    /** Loaded tables in load order, keyed by lower-case name (the catalog is case-insensitive). */
    private final Map<String, LoadedTable> tables = new LinkedHashMap<>();

    private record LoadedTable(String name, Path source, DataSource dataSource) {
    }

    public InteractiveShell(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public void run() throws IOException {
        printWelcome();
        while (true) {
            out.print(tables.isEmpty() ? "\nengine> " : "\nsql> ");
            out.flush();
            String line = in.readLine();
            if (line == null) {
                return;
            }
            line = line.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (!handle(line)) {
                out.println("Goodbye!");
                return;
            }
        }
    }

    /** Handles one input line; returns false when the user asks to exit. */
    private boolean handle(String line) {
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1].strip() : "";
        try {
            switch (command) {
                case "exit", "quit", "q" -> {
                    return false;
                }
                case "help", "?" -> printHelp();
                case "load", "open" -> load(rest);
                case "sample" -> loadSample();
                case "tables" -> printTables();
                case "columns", "describe", "desc" -> printColumns(rest);
                case "examples" -> printExamples();
                case "select" -> runSql(line);
                default -> {
                    out.println("I don't know the command '" + parts[0] + "'.");
                    out.println("Type 'help' to see what you can do, or start a query with SELECT.");
                }
            }
        } catch (IOException | RuntimeException e) {
            out.println("Error: " + e.getMessage());
        }
        return true;
    }

    // ---- commands -------------------------------------------------------

    private void load(String args) throws IOException {
        if (args.isEmpty()) {
            out.println("Tell me which file to load, for example:");
            out.println("  load C:\\Users\\me\\Downloads\\people.avro");
            out.println("  load C:\\Users\\me\\Downloads\\people.avro as people");
            return;
        }

        String pathText = args;
        String name = null;
        int as = args.toLowerCase(Locale.ROOT).lastIndexOf(" as ");
        if (as >= 0) {
            pathText = args.substring(0, as).strip();
            name = args.substring(as + 4).strip();
        }
        Path file = Path.of(unquote(pathText));
        if (!Files.isRegularFile(file)) {
            out.println("I can't find a file at: " + file.toAbsolutePath());
            out.println("Tip: in File Explorer, Shift+right-click the file -> 'Copy as path', then paste it here.");
            return;
        }
        if (name == null) {
            name = tableNameFor(file);
        } else if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            out.println("A table name must start with a letter and contain only letters, digits or _ ."
                    + " '" + name + "' is not valid.");
            return;
        }

        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        Path csv;
        if (fileName.endsWith(".csv")) {
            csv = file;
        } else {
            out.println("Reading " + file.getFileName() + " ...");
            csv = Files.createTempFile("avroengine-" + name + "-", ".csv");
            csv.toFile().deleteOnExit();
            new AvroToCsvConverter().convert(file, csv);
        }
        register(name, file, new CsvDataSource(csv));
    }

    private void loadSample() throws IOException {
        Path avro = Files.createTempFile("avroengine-sample-", ".avro");
        avro.toFile().deleteOnExit();
        SampleEmployeesGenerator.generate(avro);
        Path csv = Files.createTempFile("avroengine-employees-", ".csv");
        csv.toFile().deleteOnExit();
        new AvroToCsvConverter().convert(avro, csv);
        register("employees", avro, new CsvDataSource(csv));
    }

    private void register(String name, Path source, DataSource dataSource) {
        boolean replaced = tables.put(name.toLowerCase(Locale.ROOT),
                new LoadedTable(name, source, dataSource)) != null;
        out.println((replaced ? "Reloaded" : "Loaded") + " table '" + name + "' with "
                + dataSource.schema().size() + " columns:");
        printColumnList(dataSource);
        out.println();
        out.println("Try it now:");
        out.println("  SELECT * FROM " + name);
        out.println("Type 'examples' for more queries you can copy.");
    }

    private void runSql(String sql) {
        if (tables.isEmpty()) {
            out.println("No tables are loaded yet. Load a file first:");
            out.println("  load <path-to-file.avro>      (or type 'sample' for demo data)");
            return;
        }
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1);
        }
        Catalog catalog = new Catalog();
        for (LoadedTable t : tables.values()) {
            catalog.register(t.name(), t.dataSource());
        }
        try {
            Main.executeAndPrint(sql, catalog, out);
        } catch (LexException | ParseException e) {
            out.println("Error: " + e.getMessage());
            out.println("A query looks like: " + SQL_SHAPE);
            out.println("Text values need single quotes, e.g.  WHERE name = 'Dana'");
        } catch (BindException e) {
            out.println("Error: " + e.getMessage());
            if (e.getMessage().startsWith("Unknown table")) {
                out.println("The word after FROM must be a table name. Loaded tables: "
                        + String.join(", ", tableNames()));
                out.println("(Field names go after SELECT or WHERE, not after FROM.)");
            } else if (e.getMessage().startsWith("Unknown column")) {
                out.println("Type 'columns' to see the fields of each table.");
            }
        }
    }

    private void printTables() {
        if (tables.isEmpty()) {
            out.println("No tables loaded yet. Use 'load <file>' or 'sample'.");
            return;
        }
        for (LoadedTable t : tables.values()) {
            out.println(t.name() + "  (" + t.dataSource().schema().size() + " columns, from " + t.source() + ")");
        }
    }

    private void printColumns(String tableName) {
        if (tables.isEmpty()) {
            out.println("No tables loaded yet. Use 'load <file>' or 'sample'.");
            return;
        }
        if (tableName.isEmpty()) {
            for (LoadedTable t : tables.values()) {
                out.println(t.name() + ":");
                printColumnList(t.dataSource());
            }
            return;
        }
        LoadedTable t = tables.get(tableName.toLowerCase(Locale.ROOT));
        if (t == null) {
            out.println("No table named '" + tableName + "'. Loaded tables: " + String.join(", ", tableNames()));
            return;
        }
        printColumnList(t.dataSource());
    }

    private void printExamples() {
        if (tables.isEmpty()) {
            out.println("Load a file first ('load <file>' or 'sample'), and I'll show examples using its fields.");
            return;
        }
        LoadedTable t = tables.values().iterator().next();
        var columns = t.dataSource().schema().columns();
        Column first = columns.get(0);
        Column second = columns.size() > 1 ? columns.get(1) : first;
        // Prefer a numeric field other than the first one (usually an id) for the filter/calculate examples.
        Column numeric = columns.stream().filter(c -> isNumeric(c.type()) && c != first).findFirst()
                .orElse(isNumeric(first.type()) ? first : null);
        Column text = columns.stream().filter(c -> c.type() == DataType.STRING).findFirst().orElse(null);

        out.println("Copy any of these (they use your table '" + t.name() + "'):");
        out.println();
        out.println("  Show everything:");
        out.println("    SELECT * FROM " + t.name());
        out.println("  Show only some fields:");
        out.println("    SELECT " + first.name() + ", " + second.name() + " FROM " + t.name());
        if (numeric != null) {
            out.println("  Filter by a number:");
            out.println("    SELECT * FROM " + t.name() + " WHERE " + numeric.name() + " > 10");
            out.println("  Calculate:");
            out.println("    SELECT " + first.name() + ", " + numeric.name() + " * 2 FROM " + t.name());
        }
        if (text != null) {
            out.println("  Filter by text (single quotes!):");
            out.println("    SELECT * FROM " + t.name() + " WHERE " + text.name() + " = 'something'");
        }
        out.println("  Find missing values:");
        out.println("    SELECT * FROM " + t.name() + " WHERE " + second.name() + " IS NULL");
        out.println("  Combine conditions:");
        out.println("    SELECT * FROM " + t.name() + " WHERE " + second.name() + " IS NOT NULL AND "
                + (numeric != null ? numeric.name() + " < 100" : first.name() + " IS NOT NULL"));
    }

    // ---- text -------------------------------------------------------------

    private void printWelcome() {
        out.println("""
                ==============================================================
                  Avro SQL Engine - query Avro files with simple SQL
                ==============================================================

                How it works, in 3 steps:
                  1. Load a file:   load C:\\path\\to\\your-file.avro
                                    (no file? type 'sample' to load demo data)
                  2. Look at it:    I'll show the table name and its fields
                  3. Ask questions: SELECT * FROM <table> WHERE <condition>

                Type 'help' at any time to see all commands, 'exit' to quit.""");
    }

    private void printHelp() {
        out.println("""
                Commands:
                  load <file> [as <name>]  Load an .avro (or engine .csv) file as a table.
                                           The table is named after the file unless you give 'as <name>'.
                  sample                   Load built-in demo data as table 'employees'.
                  tables                   List loaded tables.
                  columns [<table>]        Show the fields (and their types) of a table.
                  examples                 Show ready-to-copy queries for your data.
                  help                     Show this help.
                  exit                     Quit.

                Writing a query:
                  %s
                    - SELECT  which fields to show (* = all), separated by commas
                    - FROM    which table (from 'tables'), never a field name
                    - WHERE   optional filter, e.g.  age > 30 AND city = 'Haifa'

                  Operators:  =  <>  <  <=  >  >=  +  -  *  /  AND  OR  NOT  IS NULL  IS NOT NULL
                  Text values go in single quotes: 'HR'. Field and table names ignore case.""".formatted(SQL_SHAPE));
    }

    private void printColumnList(DataSource dataSource) {
        for (Column c : dataSource.schema().columns()) {
            out.printf("    %-20s %s%n", c.name(), describe(c.type()));
        }
    }

    // ---- helpers ----------------------------------------------------------

    private static String describe(DataType type) {
        return switch (type) {
            case INT, LONG -> "whole number";
            case DOUBLE -> "decimal number";
            case STRING -> "text";
            case BOOLEAN -> "true/false";
        };
    }

    private static boolean isNumeric(DataType type) {
        return type == DataType.INT || type == DataType.LONG || type == DataType.DOUBLE;
    }

    private Iterable<String> tableNames() {
        return tables.values().stream().map(LoadedTable::name).toList();
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** Derives a valid SQL identifier from a file name, e.g. {@code "My Data-2024.avro"} -> {@code "my_data_2024"}. */
    static String tableNameFor(Path file) {
        String base = file.getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String name = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        if (name.isEmpty()) {
            return "data";
        }
        return Character.isDigit(name.charAt(0)) ? "t_" + name : name;
    }
}
