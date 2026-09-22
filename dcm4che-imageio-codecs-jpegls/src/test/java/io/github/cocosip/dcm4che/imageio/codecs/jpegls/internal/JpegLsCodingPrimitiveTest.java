package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class JpegLsCodingPrimitiveTest {
    @Test
    void predictorUsesMedianEdgeDetection() {
        assertEquals(10, JpegLsPredictor.predict(10, 20, 25));
        assertEquals(20, JpegLsPredictor.predict(10, 20, 5));
        assertEquals(18, JpegLsPredictor.predict(10, 20, 12));
    }

    @Test
    void traitsExposeEightBitLosslessDefaultsAndErrorMapping() throws Exception {
        JpegLsTraits traits = JpegLsTraits.create(255, 0, 64, 3, 7, 21);

        assertEquals(256, traits.range());
        assertEquals(8, traits.quantizedBitsPerPixel());
        assertEquals(32, traits.limit());
        assertEquals(4, traits.initialContextValue());
        assertEquals(3, traits.threshold1());
        assertEquals(7, traits.threshold2());
        assertEquals(21, traits.threshold3());
        assertEquals(0, traits.mapError(0));
        assertEquals(6, traits.mapError(3));
        assertEquals(5, traits.mapError(-3));
        assertEquals(-3, traits.unmapError(5));
        assertEquals(3, traits.unmapError(6));
    }

    @Test
    void customTraitsExposeThresholdsToTheContextModel() throws Exception {
        JpegLsTraits traits = JpegLsTraits.create(255, 0, 127, 11, 23, 47);
        JpegLsContextModel model = new JpegLsContextModel(traits.maximumSampleValue(),
                traits.nearLossless(), traits.resetThreshold(), traits.threshold1(),
                traits.threshold2(), traits.threshold3());

        assertEquals(11, model.traits().threshold1());
        assertEquals(23, model.traits().threshold2());
        assertEquals(47, model.traits().threshold3());
    }

    @Test
    void traitsQuantizeAndReconstructNearLosslessErrors() throws Exception {
        JpegLsTraits traits = JpegLsTraits.create(255, 2, 64, 9, 17, 37);

        assertEquals(-2, traits.quantizeGradient(-9));
        assertEquals(0, traits.quantizeGradient(2));
        assertEquals(3, traits.quantizeGradient(30));
        assertEquals(1, traits.computeErrorValue(7));
        assertEquals(-1, traits.computeErrorValue(-7));
        assertEquals(105, traits.reconstruct(100, 1));
        assertTrue(traits.isNear(100, 102));
        assertFalse(traits.isNear(100, 103));
    }

    @Test
    void regularContextUpdatesAndComputesGolombParameter() throws Exception {
        JpegLsRegularContextState state = new JpegLsRegularContextState(4);

        assertEquals(2, state.golombParameter());
        state.update(-3, 0, 64);

        assertEquals(7, state.a());
        assertEquals(2, state.n());
        assertEquals(-1, state.b());
        assertEquals(-1, state.c());
        assertEquals(-1, state.errorCorrection(0, 0));
    }

    @Test
    void contextModelUsesSignSymmetryForOppositeGradients() throws Exception {
        JpegLsContextModel model = new JpegLsContextModel(255, 0, 64, 3, 7, 21);

        JpegLsContextModel.Context positive = model.context(1, 0, -1);
        JpegLsContextModel.Context negative = model.context(-1, 0, 1);

        assertTrue(positive.state() == negative.state());
        assertEquals(0, positive.sign());
        assertEquals(-1, negative.sign());
    }

    @Test
    void bitWriterStuffsAfterFfAndReaderUnstuffsIt() throws Exception {
        JpegLsBitWriter writer = new JpegLsBitWriter();
        writer.writeBits(0xff, 8);
        writer.writeBit(1);
        writer.writeBits(0x2, 2);

        byte[] encoded = writer.toByteArray();

        assertArrayEquals(new byte[] {(byte) 0xff, 0x60}, encoded);
        JpegLsBitReader reader = new JpegLsBitReader(encoded);
        assertEquals(0xff, reader.readBits(8));
        assertEquals(1, reader.readBit());
        assertEquals(0x2, reader.readBits(2));
    }

    @Test
    void golombReaderAndWriterRoundTripValuesAndLimitedEscape() throws Exception {
        int[][] values = {{0, 0}, {1, 0}, {2, 1}, {5, 2}, {17, 4}};
        for (int[] value : values) {
            JpegLsGolombWriter writer = new JpegLsGolombWriter();
            writer.write(value[0], value[1]);
            JpegLsGolombReader reader = new JpegLsGolombReader(writer.toByteArray());
            assertEquals(value[0], reader.read(value[1]));
        }

        JpegLsGolombWriter writer = new JpegLsGolombWriter();
        writer.writeMapped(10000, 10, 64, 16);
        JpegLsGolombReader reader = new JpegLsGolombReader(writer.toByteArray());
        assertEquals(10000, reader.readMapped(10, 64, 16));
    }

    @Test
    void runModeScannerRoundTripsRunLengthAndInterruption() throws Exception {
        JpegLsTraits traits = JpegLsTraits.create(255, 0, 64, 3, 7, 21);
        JpegLsRunModeScanner writerScanner = new JpegLsRunModeScanner(traits);
        JpegLsGolombWriter writer = new JpegLsGolombWriter();

        writerScanner.encodeRunLength(writer, 5, false);
        writerScanner.encodeRunInterruption(writer, 1, -2);

        JpegLsRunModeScanner readerScanner = new JpegLsRunModeScanner(traits);
        JpegLsGolombReader reader = new JpegLsGolombReader(writer.toByteArray());
        assertEquals(5, readerScanner.decodeRunLength(reader, 10));
        assertEquals(-2, readerScanner.decodeRunInterruption(reader, 1));
    }

    @Test
    void rejectsInvalidPrimitiveArgumentsAndTruncatedBits() throws Exception {
        assertThrows(JpegLsException.class, () -> JpegLsTraits.create(0, 0, 64, 1, 2, 3));
        assertThrows(JpegLsException.class, () -> JpegLsTraits.create(255, 0, 64, 4, 3, 5));
        assertThrows(JpegLsException.class, () -> new JpegLsGolombWriter().write(-1, 0));
        assertThrows(JpegLsException.class, () -> new JpegLsGolombReader(new byte[0]).read(0));
        assertThrows(JpegLsException.class, () -> new JpegLsBitReader(new byte[] {0}).readBits(9));
        assertEquals(0, Arrays.copyOf(new JpegLsGolombWriter().toByteArray(), 0).length);
    }
}
