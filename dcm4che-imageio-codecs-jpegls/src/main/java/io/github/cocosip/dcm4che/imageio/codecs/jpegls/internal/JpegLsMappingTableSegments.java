package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import java.util.ArrayList;
import java.util.List;

final class JpegLsMappingTableSegments {
    private static final int MAXIMUM_DATA_LENGTH = 65530;

    private JpegLsMappingTableSegments() {
    }

    static List<byte[]> encode(JpegLsMappingTable table) {
        byte[] entries = table.entries();
        List<byte[]> segments = new ArrayList<byte[]>();
        int offset = 0;
        boolean specification = true;
        while (offset < entries.length) {
            int maximum = MAXIMUM_DATA_LENGTH - MAXIMUM_DATA_LENGTH % table.entryWidth();
            int length = Math.min(maximum, entries.length - offset);
            byte[] payload = new byte[length + 3];
            payload[0] = (byte) (specification ? 2 : 3);
            payload[1] = (byte) table.tableId();
            payload[2] = (byte) table.entryWidth();
            System.arraycopy(entries, offset, payload, 3, length);
            segments.add(payload);
            offset += length;
            specification = false;
        }
        return segments;
    }
}
