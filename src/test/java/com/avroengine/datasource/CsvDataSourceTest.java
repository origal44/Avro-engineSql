package com.avroengine.datasource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvDataSourceTest {

    @TempDir
    Path tmp;

    private Path writeCsv(String content) throws IOException {
        Path file = tmp.resolve("data.csv");
        Files.writeString(file, content);
        return file;
    }

    @Test
    void parsesSchemaFromHeaderRows() throws IOException {
        Path file = writeCsv("id,name\r\nINT,STRING\r\n\"1\",\"Alice\"\r\n");
        CsvDataSource ds = new CsvDataSource(file);

        assertEquals(2, ds.schema().size());
        assertEquals("id", ds.schema().column(0).name());
        assertEquals(DataType.INT, ds.schema().column(0).type());
        assertEquals("name", ds.schema().column(1).name());
        assertEquals(DataType.STRING, ds.schema().column(1).type());
    }

    @Test
    void convertsAllPrimitiveTypesAndNulls() throws IOException {
        Path file = writeCsv(
                "a,b,c,d,e\r\n"
                        + "INT,LONG,DOUBLE,STRING,BOOLEAN\r\n"
                        + "\"1\",\"2\",\"3.5\",\"hi\",\"true\"\r\n"
                        + "\\N,\\N,\\N,\\N,\\N\r\n");
        CsvDataSource ds = new CsvDataSource(file);

        Iterator<Batch> it = ds.batches(10);
        assertTrue(it.hasNext());
        Batch batch = it.next();
        assertEquals(2, batch.size());
        assertFalse(it.hasNext());

        var row0 = batch.rows().get(0);
        assertEquals(1, row0.get(0));
        assertEquals(2L, row0.get(1));
        assertEquals(3.5, row0.get(2));
        assertEquals("hi", row0.get(3));
        assertEquals(Boolean.TRUE, row0.get(4));

        var row1 = batch.rows().get(1);
        for (int i = 0; i < 5; i++) {
            assertNull(row1.get(i));
        }
    }

    @Test
    void distinguishesEmptyStringFromNull() throws IOException {
        Path file = writeCsv("s\r\nSTRING\r\n\"\"\r\n\\N\r\n");
        CsvDataSource ds = new CsvDataSource(file);

        Batch batch = ds.batches(10).next();
        assertEquals("", batch.rows().get(0).get(0));
        assertNull(batch.rows().get(1).get(0));
    }

    @Test
    void splitsRowsAcrossMultipleBatches() throws IOException {
        StringBuilder sb = new StringBuilder("n\r\nINT\r\n");
        for (int i = 0; i < 5; i++) {
            sb.append('"').append(i).append("\"\r\n");
        }
        Path file = writeCsv(sb.toString());
        CsvDataSource ds = new CsvDataSource(file);

        List<Integer> seen = new ArrayList<>();
        Iterator<Batch> it = ds.batches(2);
        int batchCount = 0;
        while (it.hasNext()) {
            Batch b = it.next();
            batchCount++;
            for (var row : b.rows()) {
                seen.add((Integer) row.get(0));
            }
        }
        assertEquals(3, batchCount); // 2 + 2 + 1
        assertEquals(List.of(0, 1, 2, 3, 4), seen);
    }

    @Test
    void freshIteratorRereadsFromTheStart() throws IOException {
        Path file = writeCsv("n\r\nINT\r\n\"1\"\r\n\"2\"\r\n");
        CsvDataSource ds = new CsvDataSource(file);

        assertEquals(2, count(ds));
        assertEquals(2, count(ds)); // second call gets a fresh iterator, not exhausted state
    }

    private int count(CsvDataSource ds) {
        int total = 0;
        Iterator<Batch> it = ds.batches(1024);
        while (it.hasNext()) {
            total += it.next().size();
        }
        return total;
    }

    @Test
    void rejectsUnknownType() throws IOException {
        Path file = writeCsv("x\r\nWEIRD\r\n\"1\"\r\n");
        assertThrows(CsvDataSourceException.class, () -> new CsvDataSource(file));
    }

    @Test
    void rejectsValueThatDoesNotMatchDeclaredType() throws IOException {
        Path file = writeCsv("x\r\nINT\r\n\"notanumber\"\r\n");
        CsvDataSource ds = new CsvDataSource(file);
        assertThrows(CsvDataSourceException.class, () -> ds.batches(10));
    }
}
