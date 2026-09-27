package com.example.avro;

public class AvroDecoder {
    private final byte[] buf;
    private int pos;

    public AvroDecoder(byte[] buf) {
        this.buf = buf;
    }

    public long readLong() {
        long raw = 0;
        int shift = 0;
        byte b;
        do {
            b = buf[pos++];
            raw |= (long) (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return (raw >>> 1) ^ -(raw & 1);
    }

    public int pos() {
        return pos;
    }
}
