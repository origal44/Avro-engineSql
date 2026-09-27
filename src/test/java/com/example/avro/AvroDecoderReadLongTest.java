package com.example.avro;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class AvroDecoderReadLongTest {

    private static byte[] hexToBytes(String hex) {
        String cleaned = hex.replace(" ", "");
        int len = cleaned.length();
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            result[i / 2] = (byte) Integer.parseInt(cleaned.substring(i, i + 2), 16);
        }
        return result;
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> readLongCases() {
        return Stream.of(
                arguments("00", 0L),
                arguments("01", -1L),
                arguments("02", 1L),
                arguments("03", -2L),
                arguments("7e", 63L),
                arguments("80 01", 64L),
                arguments("7f", -64L),
                arguments("d8 04", 300L)
        );
    }

    @ParameterizedTest
    @MethodSource("readLongCases")
    void readLong_decodesExpectedValue(String hex, long expected) {
        AvroDecoder decoder = new AvroDecoder(hexToBytes(hex));
        assertEquals(expected, decoder.readLong());
    }

    @org.junit.jupiter.api.Test
    void readLong_advancesCursorPastAllBytes() {
        AvroDecoder decoder = new AvroDecoder(hexToBytes("d8 04"));
        decoder.readLong();
        assertEquals(2, decoder.pos());
    }
}
