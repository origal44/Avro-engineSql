package com.avroengine.datasource;

/** Thrown for malformed typed-CSV input (bad header, wrong arity, bad value for its declared type). */
public class CsvDataSourceException extends RuntimeException {
    public CsvDataSourceException(String message) {
        super(message);
    }
}
