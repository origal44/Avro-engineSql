package com.avroengine.stub.avro;

/**
 * Thrown when the Avro-&gt;CSV stub encounters a schema shape it does not
 * support (nested records, arrays, maps, fixed, bytes, enums, or unions that
 * are not a simple {@code ["null", T]} nullable wrapper).
 */
public class UnsupportedAvroTypeException extends RuntimeException {
    public UnsupportedAvroTypeException(String message) {
        super(message);
    }
}
