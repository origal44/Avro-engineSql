package com.avroengine.binder;

/** Thrown when a query references an unknown table/column, or has a type error. */
public class BindException extends RuntimeException {
    public BindException(String message) {
        super(message);
    }
}
