package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.imageio.IIOException;

import org.junit.jupiter.api.Test;

class Htj2kRefinementPassTest {
    @Test
    void appliesPropagationAndMagnitudeRefinementInStripeOrder() throws Exception {
        Htj2kCodeBlock cleanup = new Htj2kCodeBlock(2, 2, 8,
                new int[] {1, 0, 0, 0});
        Htj2kCodeBlock decoded = Htj2kRefinementPass.decode(cleanup, 5, 3,
                new byte[] {0x01, 0x01});
        assertEquals(7, decoded.coefficient(0, 0));
        assertEquals(3, decoded.coefficient(0, 1));
        assertEquals(0, decoded.coefficient(1, 0));
        assertEquals(0, decoded.coefficient(1, 1));
    }

    @Test
    void rejectsTruncatedAndImpossibleRefinement() throws Exception {
        Htj2kCodeBlock cleanup = new Htj2kCodeBlock(2, 2, 8,
                new int[] {1, 0, 0, 0});
        assertThrows(IIOException.class,
                () -> Htj2kRefinementPass.decode(cleanup, 5, 2, new byte[0]));
        assertThrows(IIOException.class,
                () -> Htj2kRefinementPass.decode(cleanup, 7, 3, new byte[] {0x01}));
        Htj2kCodeBlock manySignificant = new Htj2kCodeBlock(4, 4, 8,
                new int[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1});
        IIOException truncated = assertThrows(IIOException.class,
                () -> Htj2kRefinementPass.decode(manySignificant, 5, 3,
                        new byte[] {0x01}));
        assertTrue(truncated.getMessage().contains("reverse bit position 8"));
    }
}
