package com.example.avro;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.avro.Engine.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineWhereTest {

    private static Map<String, Map<Integer, Object>> table() {
        Map<String, Map<Integer, Object>> t = new HashMap<>();
        t.put("id", new HashMap<>(Map.of(0, 1, 1, 2, 2, 3)));
        t.put("name", new HashMap<>(Map.of(0, "Alice", 1, "Bob", 2, "Carol")));
        t.put("age", new HashMap<>(Map.of(0, 30, 1, 25, 2, 40)));
        return t;
    }

    @Test
    void eqFiltersRows() {
        var r = where(table(), eq("name", "Bob"));
        assertEquals(Map.of(1, "Bob"), r.get("name"));
        assertEquals(Map.of(1, 25), r.get("age"));
    }

    @Test
    void comparisonAndCombinators() {
        var r = where(table(), gt("age", 26).and(ne("name", "Carol")));
        assertEquals(Map.of(0, 30), r.get("age"));

        r = where(table(), lt("age", 26).or(gte("age", 40)));
        assertEquals(Map.of(1, 25, 2, 40), r.get("age"));
    }

    @Test
    void noMatchKeepsColumnsButNoRows() {
        var r = where(table(), gt("age", 100));
        assertEquals(3, r.size());
        assertTrue(r.get("id").isEmpty());
    }

    @Test
    void composesWithSelect() {
        var r = select(where(table(), eq("id", 3)), List.of("name"));
        assertEquals(Map.of("name", Map.of(2, "Carol")), r);
    }
}
