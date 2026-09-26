package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.imageio.IIOException;

import org.junit.jupiter.api.Test;

class Htj2kQuantizerTest {
    @Test
    void matchesOpenJphReversibleFiveLevelPolicy() throws Exception {
        byte[] qcd = Htj2kQuantizer.reversiblePayload(8, 1, 5);
        byte[] expected = new byte[17];
        expected[0] = 0x20;
        expected[1] = 0x48;
        for (int i = 2; i < 14; i++) {
            expected[i] = 0x50;
        }
        for (int i = 14; i < expected.length; i++) {
            expected[i] = 0x48;
        }
        assertArrayEquals(expected, qcd);
        assertEquals(9, Htj2kQuantizer.kmax(qcd, 0, 0));
        assertEquals(10, Htj2kQuantizer.kmax(qcd, 1, 1));
        assertEquals(9, Htj2kQuantizer.kmax(qcd, 5, 3));
        assertEquals(2, Htj2kQuantizer.reversibleCcap15(qcd));
    }

    @Test
    void rejectsMissingOrUnsupportedSubbandSteps() throws Exception {
        byte[] qcd = Htj2kQuantizer.reversiblePayload(8, 1, 5);
        assertThrows(IIOException.class, () -> Htj2kQuantizer.kmax(qcd, 6, 1));
        assertThrows(IIOException.class, () -> Htj2kQuantizer.reversiblePayload(17, 1, 5));
    }

    @Test
    void matchesOpenJphDefaultIrreversibleQuantization() throws Exception {
        byte[] expected = hex("22 77 18 76 EA 76 EA 76 BC 6F 00 6F 00 6E E2 "
                + "67 4C 67 4C 67 64 50 03 50 03 50 46 57 D2 57 D2 57 61");
        byte[] qcd = Htj2kQuantizer.irreversiblePayload(8, 5);
        assertArrayEquals(expected, qcd);
        assertEquals(0x22, Htj2kQuantizer.irreversibleCcap15(qcd));
        assertEquals(14, Htj2kQuantizer.irreversibleKmax(qcd, 0, 0));
        assertEquals(10, Htj2kQuantizer.irreversibleKmax(qcd, 5, 1));
        assertThrows(IIOException.class,
                () -> Htj2kQuantizer.irreversibleKmax(qcd, 6, 1));
    }

    @Test
    void mapsTargetRatioToBoundedQualityHint() throws Exception {
        assertEquals(92, Htj2kQuantizer.qualityHint(2));
        assertEquals(88, Htj2kQuantizer.qualityHint(3));
        assertEquals(30, Htj2kQuantizer.qualityHint(100));
        byte[] defaultQcd = Htj2kQuantizer.irreversiblePayload(8, 5);
        byte[] hintedQcd = Htj2kQuantizer.irreversiblePayload(8, 5, 2);
        org.junit.jupiter.api.Assertions.assertFalse(
                java.util.Arrays.equals(defaultQcd, hintedQcd));
        assertEquals(0x22, hintedQcd[0] & 0xff);
        assertThrows(IIOException.class,
                () -> Htj2kQuantizer.irreversiblePayload(8, 5, Double.NaN));
    }

    private static byte[] hex(String value) {
        String digits = value.replace(" ", "");
        byte[] bytes = new byte[digits.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(digits.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
