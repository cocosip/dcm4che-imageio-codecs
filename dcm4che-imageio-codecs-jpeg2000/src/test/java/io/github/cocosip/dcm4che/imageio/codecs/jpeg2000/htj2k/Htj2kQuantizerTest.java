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
}
