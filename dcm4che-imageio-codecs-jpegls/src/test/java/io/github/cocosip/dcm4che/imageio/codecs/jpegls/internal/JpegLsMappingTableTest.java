package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class JpegLsMappingTableTest {
    @Test
    void parsesPaletteStyleFixedVector() throws Exception {
        JpegLsMappingTableParser parser = new JpegLsMappingTableParser();

        parser.accept(new byte[] {
                2, 7, 3,
                0, 0, 0,
                10, 20, 30,
                40, 50, 60,
                70, 80, 90
        });
        JpegLsMappingTable table = parser.finish(3);

        assertEquals(7, table.tableId());
        assertEquals(3, table.entryWidth());
        assertEquals(4, table.entryCount());
        assertArrayEquals(new byte[] {40, 50, 60}, table.entry(2));
    }

    @Test
    void writerSplitsAndParserJoinsLargeTable() throws Exception {
        byte[] entries = new byte[65536 * 2];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = (byte) i;
        }
        JpegLsMappingTable source = new JpegLsMappingTable(9, 2, entries);

        List<byte[]> segments = JpegLsMappingTableSegments.encode(source);

        assertEquals(3, segments.size());
        assertEquals(2, segments.get(0)[0] & 0xff);
        assertEquals(3, segments.get(1)[0] & 0xff);
        assertEquals(3, segments.get(2)[0] & 0xff);
        assertEquals(65533, segments.get(0).length);
        assertEquals(65533, segments.get(1).length);
        assertEquals(15, segments.get(2).length);

        JpegLsMappingTableParser parser = new JpegLsMappingTableParser();
        for (byte[] segment : segments) {
            parser.accept(segment);
        }
        JpegLsMappingTable decoded = parser.finish(65535);

        assertArrayEquals(entries, decoded.entries());
    }

    @Test
    void joinsContinuationWhenEntryAlignmentLeavesSegmentBelowMaximumLength() throws Exception {
        byte[] entries = new byte[65536 * 4];
        JpegLsMappingTable source = new JpegLsMappingTable(10, 4, entries);

        List<byte[]> segments = JpegLsMappingTableSegments.encode(source);

        assertEquals(5, segments.size());
        assertEquals(65531, segments.get(0).length);

        JpegLsMappingTableParser parser = new JpegLsMappingTableParser();
        for (byte[] segment : segments) {
            parser.accept(segment);
        }
        assertArrayEquals(entries, parser.finish(65535).entries());
    }

    @Test
    void acceptsCompleteTableWhoseSingleSegmentUsesMaximumPayload() throws Exception {
        byte[] entries = new byte[65530];
        JpegLsMappingTableParser parser = new JpegLsMappingTableParser();
        byte[] payload = new byte[65533];
        payload[0] = 2;
        payload[1] = 11;
        payload[2] = 1;
        System.arraycopy(entries, 0, payload, 3, entries.length);

        parser.accept(payload);

        assertEquals(65530, parser.finish(65529).entryCount());
    }

    @Test
    void rejectsContinuationWithoutSpecificationAndMismatchedHeader() throws Exception {
        JpegLsMappingTableParser parser = new JpegLsMappingTableParser();

        assertThrows(JpegLsException.class,
                () -> parser.accept(new byte[] {3, 1, 1, 10}));

        parser.accept(new byte[] {2, 1, 2, 0, 1});
        assertThrows(JpegLsException.class,
                () -> parser.accept(new byte[] {3, 2, 2, 0, 2}));
        assertThrows(JpegLsException.class,
                () -> parser.accept(new byte[] {3, 1, 1, 0, 2}));
    }

    @Test
    void rejectsMisalignedIncompleteAndExcessiveEntries() throws Exception {
        JpegLsMappingTableParser misaligned = new JpegLsMappingTableParser();
        assertThrows(JpegLsException.class,
                () -> misaligned.accept(new byte[] {2, 1, 2, 0}));

        JpegLsMappingTableParser incomplete = new JpegLsMappingTableParser();
        incomplete.accept(new byte[] {2, 1, 1, 10, 20});
        assertThrows(JpegLsException.class, () -> incomplete.finish(2));

        JpegLsMappingTableParser excessive = new JpegLsMappingTableParser();
        excessive.accept(new byte[] {2, 1, 1, 10, 20, 30, 40});
        assertThrows(JpegLsException.class, () -> excessive.finish(2));
    }

    @Test
    void rejectsInvalidTableDefinitionAndEntryIndex() throws Exception {
        assertThrows(JpegLsException.class, () -> new JpegLsMappingTable(0, 1, new byte[] {1}));
        assertThrows(JpegLsException.class, () -> new JpegLsMappingTable(1, 0, new byte[] {1}));
        assertThrows(JpegLsException.class, () -> new JpegLsMappingTable(1, 2, new byte[] {1}));

        JpegLsMappingTable table = new JpegLsMappingTable(1, 1, new byte[] {1});
        assertThrows(IndexOutOfBoundsException.class, () -> table.entry(1));
    }
}
