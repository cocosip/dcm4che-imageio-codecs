package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import java.io.ByteArrayOutputStream;

final class JpegLsMappingTableParser {
    private static final long MAXIMUM_ENTRY_BYTES = 65536L * 255L;
    private final ByteArrayOutputStream entries = new ByteArrayOutputStream();
    private int tableId;
    private int entryWidth;
    private boolean active;

    void accept(byte[] payload) throws JpegLsException {
        if (payload.length < 4) {
            throw new JpegLsException("truncated JPEG-LS mapping table segment");
        }
        int type = unsigned(payload[0]);
        int incomingTableId = unsigned(payload[1]);
        int incomingEntryWidth = unsigned(payload[2]);
        if (type == 2) {
            validateHeader(incomingTableId, incomingEntryWidth);
            entries.reset();
            tableId = incomingTableId;
            entryWidth = incomingEntryWidth;
            active = true;
        } else if (type == 3) {
            if (!active) {
                throw new JpegLsException("JPEG-LS mapping continuation has no specification");
            }
            if (incomingTableId != tableId || incomingEntryWidth != entryWidth) {
                throw new JpegLsException("JPEG-LS mapping continuation header mismatch");
            }
        } else {
            throw new JpegLsException("invalid JPEG-LS mapping table type: " + type);
        }

        int dataLength = payload.length - 3;
        if (dataLength == 0 || dataLength % entryWidth != 0) {
            throw new JpegLsException("misaligned JPEG-LS mapping table segment");
        }
        if ((long) entries.size() + dataLength > MAXIMUM_ENTRY_BYTES) {
            throw new JpegLsException("JPEG-LS mapping table exceeds Part 1 limits");
        }
        entries.write(payload, 3, dataLength);
    }

    boolean isActive() {
        return active;
    }

    JpegLsMappingTable finish(int maximumSampleValue) throws JpegLsException {
        JpegLsMappingTable table = finishSyntax();
        long expected = (long) maximumSampleValue + 1L;
        if (table.entryCount() != expected) {
            throw new JpegLsException("JPEG-LS mapping table entry count " + table.entryCount()
                    + " does not match " + expected);
        }
        return table;
    }

    JpegLsMappingTable finishSyntax() throws JpegLsException {
        if (!active) {
            throw new JpegLsException("JPEG-LS mapping table is not active");
        }
        JpegLsMappingTable table = new JpegLsMappingTable(tableId, entryWidth, entries.toByteArray());
        active = false;
        entries.reset();
        return table;
    }

    private static void validateHeader(int tableId, int entryWidth) throws JpegLsException {
        if (tableId < 1 || tableId > 255) {
            throw new JpegLsException("invalid JPEG-LS mapping table identifier: " + tableId);
        }
        if (entryWidth < 1 || entryWidth > 255) {
            throw new JpegLsException("invalid JPEG-LS mapping table entry width: " + entryWidth);
        }
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }
}
