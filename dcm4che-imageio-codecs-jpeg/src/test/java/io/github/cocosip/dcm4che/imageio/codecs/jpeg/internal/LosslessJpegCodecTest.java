package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LosslessJpegCodecTest {
    @Test
    void roundTripsEightBitMonochromeExactly() throws Exception {
        int[] samples = new int[8 * 5];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 37 + 11) & 0xff;
        }

        JpegFrame source = JpegFrame.of(8, 5, 1, samples, 8);
        byte[] encoded = LosslessJpegCodec.encode(source, 1);
        JpegFrame decoded = LosslessJpegCodec.decode(encoded);

        assertTrue(hasMarker(encoded, 0xc3));
        assertEquals(8, decoded.precision());
        assertArrayEquals(samples, decoded.samples());
    }

    @Test
    void roundTripsTwelveBitRgbWithAHighOrderPredictor() throws Exception {
        int[] samples = new int[7 * 4 * 3];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 149 + (i / 3) * 17) & 0xfff;
        }

        JpegFrame source = JpegFrame.of(7, 4, 3, samples, 12);
        JpegFrame decoded = LosslessJpegCodec.decode(LosslessJpegCodec.encode(source, 7));

        assertEquals(12, decoded.precision());
        assertArrayEquals(samples, decoded.samples());
    }

    @Test
    void roundTripsSixteenBitMonochromeExactly() throws Exception {
        int[] samples = new int[] {0, 1, 32768, 65535, 12345, 54321, 65534, 256};

        JpegFrame source = JpegFrame.of(4, 2, 1, samples, 16);
        JpegFrame decoded = LosslessJpegCodec.decode(LosslessJpegCodec.encode(source, 1));

        assertEquals(16, decoded.precision());
        assertArrayEquals(samples, decoded.samples());
    }

    @Test
    void roundTripsLosslessPointTransformForAlignedSamples() throws Exception {
        int[] samples = new int[5 * 3];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = ((i * 37 + 11) & 0x3f) << 2;
        }

        JpegFrame source = JpegFrame.of(5, 3, 1, samples, 8);
        byte[] encoded = LosslessJpegCodec.encode(source, 1, 0, 2);
        JpegFrame decoded = LosslessJpegCodec.decode(encoded);

        assertArrayEquals(samples, decoded.samples());
    }

    @Test
    void sv1DecodeRejectsNonFirstPredictor() throws Exception {
        JpegFrame source = JpegFrame.of(2, 2, 1, new int[] {1, 2, 3, 4}, 8);
        byte[] encoded = LosslessJpegCodec.encode(source, 2);

        assertThrows(JpegException.class, () -> LosslessJpegCodec.decode(encoded, 1));
    }

    @Test
    void roundTripsWithRestartMarkersAndResetsPredictorContext() throws Exception {
        int[] samples = new int[6 * 3];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 41 + 7) & 0xff;
        }
        JpegFrame source = JpegFrame.of(6, 3, 1, samples, 8);

        byte[] encoded = LosslessJpegCodec.encode(source, 1, 2);
        JpegFrame decoded = LosslessJpegCodec.decode(encoded);

        assertTrue(hasMarker(encoded, 0xdd));
        assertTrue(hasRestartMarker(encoded));
        assertArrayEquals(samples, decoded.samples());
    }

    @Test
    void rejectsOutOfOrderRestartMarker() throws Exception {
        JpegFrame source = JpegFrame.of(6, 3, 1, new int[18], 8);
        byte[] encoded = LosslessJpegCodec.encode(source, 1, 2);
        replaceFirstRestartMarker(encoded, 0xd1);

        assertThrows(JpegException.class, () -> LosslessJpegCodec.decode(encoded));
    }

    @Test
    void decodesReferencedLosslessHuffmanTableId() throws Exception {
        int[] samples = new int[8 * 5];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 37 + 11) & 0xff;
        }
        byte[] encoded = LosslessJpegCodec.encode(JpegFrame.of(8, 5, 1, samples, 8), 1);

        remapLosslessHuffmanTable(encoded, 2);

        assertArrayEquals(samples, LosslessJpegCodec.decode(encoded).samples());
    }

    private static boolean hasMarker(byte[] data, int marker) {
        for (int i = 0; i + 1 < data.length; i++) {
            if ((data[i] & 0xff) == 0xff && (data[i + 1] & 0xff) == marker) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRestartMarker(byte[] data) {
        for (int i = 0; i + 1 < data.length; i++) {
            if ((data[i] & 0xff) == 0xff && (data[i + 1] & 0xff) >= 0xd0
                    && (data[i + 1] & 0xff) <= 0xd7) {
                return true;
            }
        }
        return false;
    }

    private static void replaceFirstRestartMarker(byte[] data, int marker) {
        for (int i = 0; i + 1 < data.length; i++) {
            if ((data[i] & 0xff) == 0xff && (data[i + 1] & 0xff) >= 0xd0
                    && (data[i + 1] & 0xff) <= 0xd7) {
                data[i + 1] = (byte) marker;
                return;
            }
        }
        throw new AssertionError("restart marker not found");
    }

    private static void remapLosslessHuffmanTable(byte[] data, int tableId) {
        for (int offset = 2; offset + 3 < data.length;) {
            int marker = data[offset + 1] & 0xff;
            int length = ((data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
            int end = offset + 2 + length;
            if (marker == 0xc4) {
                for (int cursor = offset + 4; cursor < end;) {
                    data[cursor] = (byte) tableId;
                    int values = 0;
                    for (int i = 1; i <= 16; i++) {
                        values += data[cursor + i] & 0xff;
                    }
                    cursor += 17 + values;
                }
            } else if (marker == 0xda) {
                int components = data[offset + 4] & 0xff;
                for (int component = 0; component < components; component++) {
                    data[offset + 6 + component * 2] = (byte) (tableId << 4);
                }
                return;
            }
            offset = end;
        }
        throw new AssertionError("SOS marker not found");
    }
}
