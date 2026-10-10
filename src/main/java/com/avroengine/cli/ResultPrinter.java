package com.avroengine.cli;

import com.avroengine.datasource.Row;
import com.avroengine.datasource.Schema;

import java.io.PrintStream;
import java.util.List;

/** Renders a result set as an aligned, left-justified text table. */
public final class ResultPrinter {

    private ResultPrinter() {
    }

    public static void print(Schema schema, List<Row> rows, PrintStream out) {
        int n = schema.size();
        String[] headers = new String[n];
        for (int i = 0; i < n; i++) {
            headers[i] = schema.column(i).name();
        }

        String[][] cells = new String[rows.size()][n];
        int[] widths = new int[n];
        for (int i = 0; i < n; i++) {
            widths[i] = headers[i].length();
        }
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            for (int c = 0; c < n; c++) {
                Object value = row.get(c);
                String text = value == null ? "NULL" : value.toString();
                cells[r][c] = text;
                widths[c] = Math.max(widths[c], text.length());
            }
        }

        printRow(headers, widths, out);
        printSeparator(widths, out);
        for (String[] row : cells) {
            printRow(row, widths, out);
        }
        out.println("(" + rows.size() + (rows.size() == 1 ? " row)" : " rows)"));
    }

    private static void printRow(String[] cells, int[] widths, PrintStream out) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(cells[i]).append(" ".repeat(widths[i] - cells[i].length()));
        }
        out.println(sb.toString().stripTrailing());
    }

    private static void printSeparator(int[] widths, PrintStream out) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                sb.append("-+-");
            }
            sb.append("-".repeat(widths[i]));
        }
        out.println(sb);
    }
}
