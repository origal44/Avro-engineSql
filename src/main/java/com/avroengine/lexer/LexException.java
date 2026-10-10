package com.avroengine.lexer;

/** Thrown for invalid SQL text; carries the character position for a clear error message. */
public class LexException extends RuntimeException {
    private final int position;

    public LexException(String message, int position) {
        super(message + " (at position " + position + ")");
        this.position = position;
    }

    public int position() {
        return position;
    }
}
