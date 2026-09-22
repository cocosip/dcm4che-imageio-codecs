package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import java.util.Arrays;

final class JpegLsMappingTable {
    private final int tableId;
    private final int entryWidth;
    private final byte[] entries;

    JpegLsMappingTable(int tableId, int entryWidth, byte[] entries) throws JpegLsException {
        if (tableId < 1 || tableId > 255) {
            throw new JpegLsException("invalid JPEG-LS mapping table identifier: " + tableId);
        }
        if (entryWidth < 1 || entryWidth > 255) {
            throw new JpegLsException("invalid JPEG-LS mapping table entry width: " + entryWidth);
        }
        if (entries.length == 0 || entries.length % entryWidth != 0) {
            throw new JpegLsException("misaligned JPEG-LS mapping table entries");
        }
        this.tableId = tableId;
        this.entryWidth = entryWidth;
        this.entries = Arrays.copyOf(entries, entries.length);
    }

    int tableId() {
        return tableId;
    }

    int entryWidth() {
        return entryWidth;
    }

    int entryCount() {
        return entries.length / entryWidth;
    }

    byte[] entries() {
        return Arrays.copyOf(entries, entries.length);
    }

    byte[] entry(int reconstructedSample) {
        if (reconstructedSample < 0 || reconstructedSample >= entryCount()) {
            throw new IndexOutOfBoundsException("mapping table sample: " + reconstructedSample);
        }
        int offset = reconstructedSample * entryWidth;
        return Arrays.copyOfRange(entries, offset, offset + entryWidth);
    }
}
