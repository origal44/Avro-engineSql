package com.avroengine.datasource;

/**
 * One row of typed values, positional per the owning {@link Schema}.
 * A {@code null} entry means SQL NULL.
 *
 * <p>Note: the generated equals/hashCode compare {@code values} by array
 * reference (records don't special-case arrays), so this is not usable as a
 * value-equality key -- not needed here since rows are only ever iterated.
 */
public record Row(Object[] values) {

    public Object get(int index) {
        return values[index];
    }
}
