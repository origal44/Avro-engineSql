package com.avroengine.datasource;

/** A single column of a {@link Schema}: its name and declared type. */
public record Column(String name, DataType type) {
}
