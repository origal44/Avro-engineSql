package com.avroengine.exec;

import com.avroengine.binder.Binder;
import com.avroengine.binder.BoundQuery;
import com.avroengine.catalog.Catalog;
import com.avroengine.datasource.Batch;
import com.avroengine.datasource.CsvDataSource;
import com.avroengine.datasource.Row;
import com.avroengine.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryExecutorTest {

    @TempDir
    Path tmp;

    private Catalog catalogWithEmployees() throws IOException {
        Path file = tmp.resolve("employees.csv");
        Files.writeString(file,
                "id,name,salary,active,manager\r\n"
                        + "INT,STRING,DOUBLE,BOOLEAN,STRING\r\n"
                        + "\"1\",\"Alice\",\"5000.0\",\"true\",\\N\r\n"
                        + "\"2\",\"Bob\",\"3000.0\",\"false\",\"Alice\"\r\n"
                        + "\"3\",\"Carol\",\"7000.0\",\"true\",\"Alice\"\r\n"
                        + "\"4\",\"Dave\",\"2000.0\",\"false\",\\N\r\n");
        Catalog catalog = new Catalog();
        catalog.register("employees", new CsvDataSource(file));
        return catalog;
    }

    /** Runs a query with a deliberately tiny batch size to exercise cross-batch behavior. */
    private List<Row> run(String sql, Catalog catalog, int batchSize) {
        BoundQuery q = Binder.bind(Parser.parse(sql), catalog);
        Iterator<Batch> it = QueryExecutor.execute(q, batchSize);
        List<Row> rows = new ArrayList<>();
        while (it.hasNext()) {
            rows.addAll(it.next().rows());
        }
        return rows;
    }

    @Test
    void selectStarReturnsAllColumnsAndRows() throws IOException {
        List<Row> rows = run("SELECT * FROM employees", catalogWithEmployees(), 2);
        assertEquals(4, rows.size());
        assertEquals(5, rows.get(0).values().length);
    }

    @Test
    void projectionSelectsOnlyRequestedColumns() throws IOException {
        List<Row> rows = run("SELECT id, name FROM employees", catalogWithEmployees(), 2);
        assertEquals(4, rows.size());
        assertEquals(2, rows.get(0).values().length);
        assertEquals(1, rows.get(0).get(0));
        assertEquals("Alice", rows.get(0).get(1));
    }

    @Test
    void whereWithAndOrPrecedence() throws IOException {
        // active = TRUE AND salary > 6000 OR name = 'Dave'  ==  (active AND salary>6000) OR name='Dave'
        // matches Carol (active, 7000) and Dave (name match) but not Alice/Bob
        List<Row> rows = run(
                "SELECT name FROM employees WHERE active = TRUE AND salary > 6000 OR name = 'Dave'",
                catalogWithEmployees(), 3);
        List<String> names = rows.stream().map(r -> (String) r.get(0)).sorted().toList();
        assertEquals(List.of("Carol", "Dave"), names);
    }

    @Test
    void isNullFiltersNullManagers() throws IOException {
        List<Row> rows = run("SELECT name FROM employees WHERE manager IS NULL", catalogWithEmployees(), 2);
        List<String> names = rows.stream().map(r -> (String) r.get(0)).sorted().toList();
        assertEquals(List.of("Alice", "Dave"), names);
    }

    @Test
    void isNotNullFiltersNonNullManagers() throws IOException {
        List<Row> rows = run("SELECT name FROM employees WHERE manager IS NOT NULL", catalogWithEmployees(), 2);
        List<String> names = rows.stream().map(r -> (String) r.get(0)).sorted().toList();
        assertEquals(List.of("Bob", "Carol"), names);
    }

    @Test
    void batchSizeDoesNotAffectResultCorrectness() throws IOException {
        Catalog catalog = catalogWithEmployees();
        List<Row> withBigBatches = run("SELECT id FROM employees WHERE salary > 2500", catalog, 1024);
        List<Row> withTinyBatches = run("SELECT id FROM employees WHERE salary > 2500", catalog, 1);
        assertEquals(withBigBatches.size(), withTinyBatches.size());
        assertEquals(3, withBigBatches.size());
    }
}
