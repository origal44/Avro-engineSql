package com.avroengine.datasource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180-style CSV reader/writer shared by the typed-CSV writer
 * (stub.avro) and the typed-CSV reader (CsvDataSource). Not part of the SQL
 * engine core -- only the datasource layer and the Avro stub know this format
 * exists.
 *
 * <p>Convention: NULL is written as the bare marker {@code \N} (never
 * quoted); any other value -- including an empty string -- is written as a
 * quoted field, so {@code \N} and {@code ""} are never ambiguous.
 */
public final class CsvFormat {

    public static final String NULL_MARKER = "\\N";

    private CsvFormat() {
    }

    /** Writes one record, terminated by "\r\n" as RFC 4180 recommends. */
    public static void writeRecord(Writer out, List<String> fields) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(fields.get(i));
        }
        out.write("\r\n");
    }

    /** Quotes a raw value for CSV output, doubling any embedded quotes. */
    public static String quote(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 2);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                sb.append('"');
            }
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Reads one logical CSV record (handling quoted fields that span
     * physical lines), or returns {@code null} at end of stream.
     */
    public static List<String> readRecord(BufferedReader in) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean sawAnything = false;
        boolean fieldWasQuoted = false;

        int c;
        while ((c = in.read()) != -1) {
            sawAnything = true;
            char ch = (char) c;
            if (inQuotes) {
                if (ch == '"') {
                    in.mark(1);
                    int next = in.read();
                    if (next == '"') {
                        current.append('"');
                    } else {
                        inQuotes = false;
                        if (next != -1) {
                            in.reset();
                        }
                    }
                } else {
                    current.append(ch);
                }
            } else {
                switch (ch) {
                    case '"' -> {
                        inQuotes = true;
                        fieldWasQuoted = true;
                    }
                    case ',' -> {
                        fields.add(finishField(current, fieldWasQuoted));
                        current.setLength(0);
                        fieldWasQuoted = false;
                    }
                    case '\r' -> {
                        in.mark(1);
                        int next = in.read();
                        if (next != '\n' && next != -1) {
                            in.reset();
                        }
                        fields.add(finishField(current, fieldWasQuoted));
                        return fields;
                    }
                    case '\n' -> {
                        fields.add(finishField(current, fieldWasQuoted));
                        return fields;
                    }
                    default -> current.append(ch);
                }
            }
        }

        if (!sawAnything) {
            return null;
        }
        fields.add(finishField(current, fieldWasQuoted));
        return fields;
    }

    private static String finishField(StringBuilder current, boolean wasQuoted) {
        String text = current.toString();
        if (!wasQuoted && text.equals(NULL_MARKER)) {
            return null; // caller distinguishes null from "" via this sentinel
        }
        return text;
    }
}
