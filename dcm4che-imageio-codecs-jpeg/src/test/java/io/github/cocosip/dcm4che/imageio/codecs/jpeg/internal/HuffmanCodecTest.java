package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.junit.jupiter.api.Test;

class HuffmanCodecTest {
    @Test
    void assignsCanonicalCodesAndRoundTripsSymbols() throws Exception {
        int[] counts = new int[16];
        counts[0] = 1;
        counts[1] = 1;
        HuffmanTable table = HuffmanTable.fromDefinition(counts, new int[] {3, 7});

        assertEquals(0, table.codeFor(3));
        assertEquals(2, table.sizeFor(7));
        assertEquals(2, table.codeFor(7));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BitWriter writer = new BitWriter(new MemoryCacheImageOutputStream(bytes));
        HuffmanCodec.encodeSymbol(writer, table, 3);
        HuffmanCodec.encodeSymbol(writer, table, 7);
        writer.flush();

        BitReader reader = new BitReader(new MemoryCacheImageInputStream(
                new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(3, HuffmanCodec.decodeSymbol(reader, table));
        assertEquals(7, HuffmanCodec.decodeSymbol(reader, table));
    }
}
