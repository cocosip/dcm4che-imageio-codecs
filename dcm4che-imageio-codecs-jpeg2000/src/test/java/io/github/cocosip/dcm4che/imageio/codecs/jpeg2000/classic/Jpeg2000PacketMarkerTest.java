package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

class Jpeg2000PacketMarkerTest {
    @Test
    void decodesSopAndEphAndRejectsWrongSequenceOrMissingEph() throws Exception {
        int[] samples = new int[39 * 37];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = i * 17 & 255;
        }
        Jpeg2000Raster source = Jpeg2000Raster.of(39, 37, 8, 8, false,
                "MONOCHROME2", new int[][] {samples}, Jpeg2000Limits.defaults());
        byte[] encoded = Jpeg2000LosslessCodec.encode(source, false,
                new double[] {0}, Jpeg2000ProgressionOrder.LRCP, true, true);
        assertArrayEquals(samples, Jpeg2000LosslessCodec.decode(encoded).component(0));
        int sop = marker(encoded, 0x91);
        byte[] wrongSequence = encoded.clone();
        wrongSequence[sop + 5] = 1;
        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000LosslessCodec.decode(wrongSequence));
        int eph = marker(encoded, 0x92);
        byte[] missingEph = encoded.clone();
        missingEph[eph + 1] = 0;
        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000LosslessCodec.decode(missingEph));
    }

    @Test
    void decodesSopSequenceRestartedInSecondTilePart() throws Exception {
        int[] samples = new int[39 * 37];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = i * 17 & 255;
        }
        Jpeg2000Raster source = Jpeg2000Raster.of(39, 37, 8, 8, false,
                "MONOCHROME2", new int[][] {samples}, Jpeg2000Limits.defaults());
        byte[] encoded = Jpeg2000LosslessCodec.encode(source, false,
                new double[] {0}, Jpeg2000ProgressionOrder.LRCP, true, true);
        int firstSop = marker(encoded, 0x91);
        int secondSop = marker(encoded, 0x91, firstSop + 2);
        byte[] split = splitTilePart(encoded, secondSop);
        assertArrayEquals(samples, Jpeg2000LosslessCodec.decode(split).component(0));
        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000LosslessCodec.decode(splitTilePart(encoded, secondSop - 1)));
    }

    private static byte[] splitTilePart(byte[] encoded, int splitOffset) {
        int sot = marker(encoded, 0x90);
        int eoc = encoded.length - 2;
        byte[] split = new byte[encoded.length + 14];
        System.arraycopy(encoded, 0, split, 0, splitOffset);
        System.arraycopy(encoded, splitOffset, split, splitOffset + 14,
                encoded.length - splitOffset);
        writeInt(split, sot + 6, splitOffset - sot);
        split[sot + 11] = 2;
        System.arraycopy(encoded, sot, split, splitOffset, 12);
        writeInt(split, splitOffset + 6, 14 + eoc - splitOffset);
        split[splitOffset + 10] = 1;
        split[splitOffset + 11] = 2;
        split[splitOffset + 12] = (byte) 0xff;
        split[splitOffset + 13] = (byte) 0x93;
        int sequence = 0;
        for (int sop = marker(split, 0x91, splitOffset + 14);
                sop >= 0 && sop < eoc + 14;
                sop = marker(split, 0x91, sop + 2)) {
            split[sop + 4] = (byte) (sequence >>> 8);
            split[sop + 5] = (byte) sequence++;
        }
        return split;
    }

    private static int marker(byte[] bytes, int code) {
        return marker(bytes, code, 0);
    }

    private static int marker(byte[] bytes, int code, int start) {
        for (int i = start; i < bytes.length - 1; i++) {
            if ((bytes[i] & 0xff) == 0xff && (bytes[i + 1] & 0xff) == code) {
                return i;
            }
        }
        return -1;
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
