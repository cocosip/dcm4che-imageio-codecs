package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Jpeg2000TagTreeTest {
    @Test
    void preservesIncrementalStateAcrossLayerThresholds() throws Exception {
        int width = 3;
        int height = 2;
        int[] values = {0, 2, 1, 3, 2, 4};
        Jpeg2000TagTree encoder = new Jpeg2000TagTree(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                encoder.setValue(x, y, values[y * width + x]);
            }
        }

        Jpeg2000PacketBitWriter writer = new Jpeg2000PacketBitWriter();
        for (int threshold = 1; threshold <= 5; threshold++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    encoder.encode(writer, x, y, threshold);
                }
            }
        }
        writer.align();

        Jpeg2000TagTree decoder = new Jpeg2000TagTree(width, height);
        Jpeg2000PacketBitReader reader = new Jpeg2000PacketBitReader(
                writer.toByteArray(), 0, writer.toByteArray().length);
        for (int threshold = 1; threshold <= 5; threshold++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    assertEquals(values[y * width + x] < threshold,
                            decoder.decode(reader, x, y, threshold));
                }
            }
        }
    }

    @Test
    void decodesExactValuesAndDoesNotShareMutableState() throws Exception {
        Jpeg2000TagTree first = new Jpeg2000TagTree(2, 2);
        Jpeg2000TagTree second = new Jpeg2000TagTree(2, 2);
        int[] values = {3, 0, 2, 1};
        for (int index = 0; index < values.length; index++) {
            first.setValue(index & 1, index >>> 1, values[index]);
            second.setValue(index & 1, index >>> 1, values[index]);
        }

        Jpeg2000PacketBitWriter firstWriter = new Jpeg2000PacketBitWriter();
        first.encode(firstWriter, 0, 0, 2);
        Jpeg2000PacketBitWriter secondWriter = new Jpeg2000PacketBitWriter();
        second.encode(secondWriter, 0, 0, 4);
        secondWriter.align();

        Jpeg2000TagTree valueDecoder = new Jpeg2000TagTree(2, 2);
        Jpeg2000PacketBitReader valueReader = new Jpeg2000PacketBitReader(
                secondWriter.toByteArray(), 0, secondWriter.toByteArray().length);
        assertEquals(3, valueDecoder.decodeValue(valueReader, 0, 0));
    }

    @Test
    void rejectsInvalidGeometryLeavesValuesAndThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new Jpeg2000TagTree(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Jpeg2000TagTree(1, 0));

        Jpeg2000TagTree tree = new Jpeg2000TagTree(2, 2);
        assertThrows(IllegalArgumentException.class, () -> tree.setValue(2, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> tree.setValue(0, 0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> tree.encode(new Jpeg2000PacketBitWriter(), 0, 0, 0));
    }
}
