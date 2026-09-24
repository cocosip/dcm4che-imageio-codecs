package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;

class Jpeg2000EbcotTest {
    @Test
    void encodesAllThreeTierOnePassTypesAndReconstructsCoefficients() throws Exception {
        int[] coefficients = {
                0, 5, -2, 0,
                7, 0, 0, -1,
                0, 0, 3, 0,
                -4, 0, 0, 2
        };

        Jpeg2000EbcotEncodedBlock encoded = new Jpeg2000EbcotEncoder(4, 4, 0, 0)
                .encode(coefficients, 16);

        assertTrue(encoded.data().length > 0);
        assertTrue(encoded.passes().stream().anyMatch(
                pass -> pass.type() == Jpeg2000EbcotPass.Type.SIGNIFICANCE_PROPAGATION));
        assertTrue(encoded.passes().stream().anyMatch(
                pass -> pass.type() == Jpeg2000EbcotPass.Type.MAGNITUDE_REFINEMENT));
        assertTrue(encoded.passes().stream().anyMatch(
                pass -> pass.type() == Jpeg2000EbcotPass.Type.CLEANUP));

        int[] decoded = Jpeg2000EbcotDecoder.decode(encoded, encoded.passes().size());
        assertArrayEquals(coefficients, decoded);
    }

    @Test
    void passLengthsAreBoundedAndTruncationProducesAPartialBlock() throws Exception {
        int[] coefficients = {9, -4, 0, 1, 0, -8};
        Jpeg2000EbcotEncodedBlock encoded = new Jpeg2000EbcotEncoder(3, 2, 0, 0)
                .encode(coefficients, 9);

        int previous = 0;
        for (Jpeg2000EbcotPass pass : encoded.passes()) {
            assertTrue(pass.byteLength() >= previous);
            assertTrue(pass.byteLength() <= encoded.data().length);
            previous = pass.byteLength();
        }

        int[] partial = Jpeg2000EbcotDecoder.decode(encoded, 1);
        assertFalse(java.util.Arrays.equals(coefficients, partial));
        assertEquals(coefficients.length, partial.length);
    }

    @Test
    void zeroBlockHasNoPassesAndDecodesToZeros() throws Exception {
        int[] coefficients = new int[16];
        Jpeg2000EbcotEncodedBlock encoded = new Jpeg2000EbcotEncoder(4, 4, 0, 0)
                .encode(coefficients, 9);

        assertEquals(0, encoded.data().length);
        assertTrue(encoded.passes().isEmpty());
        assertArrayEquals(coefficients, Jpeg2000EbcotDecoder.decode(encoded, 9));
    }

    @Test
    void resetVscAndSegmarkStylesRemainDeterministicAndRoundTrip() throws Exception {
        int[] coefficients = {12, -7, 2, 0, -1, 5, 0, -3};
        Jpeg2000EbcotEncodedBlock first = new Jpeg2000EbcotEncoder(4, 2, 3, 0x2a)
                .encode(coefficients, 12);
        Jpeg2000EbcotEncodedBlock second = new Jpeg2000EbcotEncoder(4, 2, 3, 0x2a)
                .encode(coefficients, 12);

        assertArrayEquals(first.data(), second.data());
        assertArrayEquals(coefficients, Jpeg2000EbcotDecoder.decode(first, first.passes().size()));
    }

    @Test
    void rejectsIntegerMinimumCoefficientInsteadOfChangingItsMagnitude() {
        assertThrows(Jpeg2000Exception.class,
                () -> new Jpeg2000EbcotEncoder(1, 1, 0, 0)
                        .encode(new int[] {Integer.MIN_VALUE}, 3));
    }

    @Test
    void matchesIndependentFoDicomTierOneVectors() throws Exception {
        int[] first = {
                0, 5, -2, 0,
                7, 0, 0, -1,
                0, 0, 3, 0,
                -4, 0, 0, 2
        };
        int[] second = {9, -4, 0, 1, 0, -8};

        assertArrayEquals(hex("0EC977D2CB764A"),
                new Jpeg2000EbcotEncoder(4, 4, 0, 0).encode(first, 16).data());
        assertArrayEquals(hex("04CB5B8B"),
                new Jpeg2000EbcotEncoder(3, 2, 0, 0).encode(second, 9).data());

        Jpeg2000EbcotEncodedBlock foreign = new Jpeg2000EbcotEncodedBlock(
                3, 2, 0, 0, 3, hex("04CB5B8B"),
                new Jpeg2000EbcotEncoder(3, 2, 0, 0).encode(second, 9).passes());
        assertArrayEquals(second, Jpeg2000EbcotDecoder.decode(foreign, foreign.passes().size()));
    }

    @Test
    void rejectsPayloadTruncatedBeforeRequestedPassBoundary() throws Exception {
        int[] coefficients = {9, -4, 0, 1, 0, -8};
        Jpeg2000EbcotEncodedBlock complete = new Jpeg2000EbcotEncoder(3, 2, 0, 0)
                .encode(coefficients, 9);
        byte[] truncated = java.util.Arrays.copyOf(complete.data(), complete.data().length - 1);
        Jpeg2000EbcotEncodedBlock malformed = new Jpeg2000EbcotEncodedBlock(
                3, 2, 0, 0, complete.maxBitPlane(), truncated, complete.passes());

        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000EbcotDecoder.decode(malformed, malformed.passes().size()));
    }

    @Test
    void decodesEveryRecordedPassBoundaryFromItsExactPrefix() throws Exception {
        int[] coefficients = {
                24, -12, 0, 5,
                -7, 31, 2, 0,
                0, -3, 18, -22,
                9, 0, -1, 14
        };
        for (int style : new int[] {0, 0x02, 0x08, 0x20, 0x2a}) {
            Jpeg2000EbcotEncodedBlock complete = new Jpeg2000EbcotEncoder(4, 4, 0, style)
                    .encode(coefficients, 13);

            for (int passCount = 1; passCount <= complete.passes().size(); passCount++) {
                int length = complete.passes().get(passCount - 1).byteLength();
                Jpeg2000EbcotEncodedBlock prefix = new Jpeg2000EbcotEncodedBlock(
                        4, 4, 0, style, complete.maxBitPlane(),
                        Arrays.copyOf(complete.data(), length),
                        new ArrayList<Jpeg2000EbcotPass>(complete.passes().subList(0, passCount)));

                assertArrayEquals(
                        Jpeg2000EbcotDecoder.decode(complete, passCount),
                        Jpeg2000EbcotDecoder.decode(prefix, passCount),
                        "style=" + style + ", pass boundary=" + passCount);
            }
        }
    }

    @Test
    void rejectsInvalidPassRequestsAndMetadata() throws Exception {
        int[] coefficients = {9, -4, 0, 1, 0, -8};
        Jpeg2000EbcotEncodedBlock complete = new Jpeg2000EbcotEncoder(3, 2, 0, 0)
                .encode(coefficients, 9);

        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000EbcotDecoder.decode(complete, complete.passes().size() + 1));

        Jpeg2000EbcotPass first = complete.passes().get(0);
        Jpeg2000EbcotEncodedBlock pastPayload = new Jpeg2000EbcotEncodedBlock(
                3, 2, 0, 0, complete.maxBitPlane(), complete.data(),
                Collections.singletonList(new Jpeg2000EbcotPass(
                        first.type(), first.bitPlane(), complete.data().length + 1)));
        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000EbcotDecoder.decode(pastPayload, 1));

        Jpeg2000EbcotEncodedBlock wrongPass = new Jpeg2000EbcotEncodedBlock(
                3, 2, 0, 0, complete.maxBitPlane(), complete.data(),
                Collections.singletonList(new Jpeg2000EbcotPass(
                        Jpeg2000EbcotPass.Type.MAGNITUDE_REFINEMENT,
                        first.bitPlane(), first.byteLength())));
        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000EbcotDecoder.decode(wrongPass, 1));
    }

    @Test
    void rejectsCodeBlockStylesWithoutImplementedEntropyPaths() {
        for (int style : new int[] {0x01, 0x04, 0x10, 0x40}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Jpeg2000EbcotEncoder(1, 1, 0, style));

            Jpeg2000EbcotEncodedBlock unsupported = new Jpeg2000EbcotEncodedBlock(
                    1, 1, 0, style, 0, new byte[] {0, 0},
                    Collections.singletonList(new Jpeg2000EbcotPass(
                            Jpeg2000EbcotPass.Type.CLEANUP, 0, 2)));
            assertThrows(Jpeg2000Exception.class,
                    () -> Jpeg2000EbcotDecoder.decode(unsupported, 1));
        }
    }

    @Test
    void rejectsInvalidSegmentationSymbol() {
        Jpeg2000EbcotEncodedBlock malformed = new Jpeg2000EbcotEncodedBlock(
                1, 1, 0, 0x20, 0, new byte[] {0, 0},
                Collections.singletonList(new Jpeg2000EbcotPass(
                        Jpeg2000EbcotPass.Type.CLEANUP, 0, 2)));

        assertThrows(Jpeg2000Exception.class,
                () -> Jpeg2000EbcotDecoder.decode(malformed, 1));
    }

    @Test
    void roundTripsOddBlocksInEverySubbandOrientation() throws Exception {
        int[] coefficients = new int[35];
        for (int index = 0; index < coefficients.length; index++) {
            coefficients[index] = ((index * 29 + 7) % 63) - 31;
        }

        for (int orientation = 0; orientation < 4; orientation++) {
            for (int style : new int[] {0, 0x02, 0x08, 0x20, 0x2a}) {
                Jpeg2000EbcotEncodedBlock encoded =
                        new Jpeg2000EbcotEncoder(5, 7, orientation, style)
                                .encode(coefficients, 16);
                assertArrayEquals(coefficients,
                        Jpeg2000EbcotDecoder.decode(encoded, encoded.passes().size()),
                        "orientation=" + orientation + ", style=" + style);
            }
        }
    }

    @Test
    void rejectsCodeBlocksBeyondPartOneDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> new Jpeg2000EbcotEncoder(1025, 1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Jpeg2000EbcotEncoder(1024, 8, 0, 0));
    }

    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }
}
