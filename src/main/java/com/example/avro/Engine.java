package com.example.avro;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

public class Engine {

    public static Map<String, Map<Integer, Object>> select(Map<String, Map<Integer, Object>> table, List<String> cols) {
        Map<String, Map<Integer, Object>> result = new HashMap<>();
        for (String col : cols) {
            result.put(col, table.get(col));
        }
        return result;
    }

    public static Map<String, Map<Integer, Object>> where(Map<String, Map<Integer, Object>> table,
                                                           Predicate<Map<String, Object>> predicate) {
        Set<Integer> rowIds = new TreeSet<>();
        for (Map<Integer, Object> column : table.values()) {
            rowIds.addAll(column.keySet());
        }

        Map<String, Map<Integer, Object>> result = new HashMap<>();
        for (String col : table.keySet()) {
            result.put(col, new HashMap<>());
        }

        for (int rowId : rowIds) {
            Map<String, Object> row = new HashMap<>();
            for (Map.Entry<String, Map<Integer, Object>> e : table.entrySet()) {
                row.put(e.getKey(), e.getValue().get(rowId));
            }
            if (predicate.test(row)) {
                for (Map.Entry<String, Map<Integer, Object>> e : table.entrySet()) {
                    if (e.getValue().containsKey(rowId)) {
                        result.get(e.getKey()).put(rowId, e.getValue().get(rowId));
                    }
                }
            }
        }
        return result;
    }

    public static Predicate<Map<String, Object>> eq(String col, Object value) {
        return row -> Objects.equals(row.get(col), value);
    }

    public static Predicate<Map<String, Object>> ne(String col, Object value) {
        return eq(col, value).negate();
    }

    public static Predicate<Map<String, Object>> gt(String col, Comparable<?> value) {
        return compare(col, value, c -> c > 0);
    }

    public static Predicate<Map<String, Object>> gte(String col, Comparable<?> value) {
        return compare(col, value, c -> c >= 0);
    }

    public static Predicate<Map<String, Object>> lt(String col, Comparable<?> value) {
        return compare(col, value, c -> c < 0);
    }

    public static Predicate<Map<String, Object>> lte(String col, Comparable<?> value) {
        return compare(col, value, c -> c <= 0);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate<Map<String, Object>> compare(String col, Comparable<?> value, IntPredicate test) {
        return row -> {
            Object actual = row.get(col);
            return actual != null && test.test(((Comparable) actual).compareTo(value));
        };
    }

    public static void main(String[] args) {
        Map<String, Map<Integer, Object>> table = new HashMap<>();

        Map<Integer, Object> id = new HashMap<>();
        id.put(0, 1);
        id.put(1, 2);
        id.put(2, 3);
        table.put("id", id);

        Map<Integer, Object> name = new HashMap<>();
        name.put(0, "Alice");
        name.put(1, "Bob");
        name.put(2, "Carol");
        table.put("name", name);

        Map<Integer, Object> age = new HashMap<>();
        age.put(0, 30);
        age.put(1, 25);
        age.put(2, 40);
        table.put("age", age);

        Map<String, Map<Integer, Object>> filtered = where(table, gt("age", 26).and(ne("name", "Carol")));
        Map<String, Map<Integer, Object>> result = select(filtered, List.of("id", "name"));
        System.out.println(result);
    }
}
