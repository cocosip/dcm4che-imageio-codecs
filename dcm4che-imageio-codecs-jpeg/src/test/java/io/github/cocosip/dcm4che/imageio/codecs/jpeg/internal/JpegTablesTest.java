package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JpegTablesTest {
    @Test
    void exposesStandardBaselineTables() {
        assertEquals(64, JpegTables.standardLuminanceQuantization().length);
        assertEquals(64, JpegTables.standardChrominanceQuantization().length);
        assertEquals(0, JpegTables.standardLuminanceDc().codeFor(0));
    }

    @Test
    void rejectsInvalidQuantizationValues() {
        assertThrows(IllegalArgumentException.class,
                () -> QuantizationTable.of(new int[] {0}));
    }
}
