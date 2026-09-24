package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import java.util.Arrays;

/**
 * Immutable JPEG-LS mapping table. Entries are opaque byte vectors indexed by
 * reconstructed sample value.
 */
public final class JpegLsMappingTable {
    private static final long MAXIMUM_ENTRY_BYTES = 65536L * 255L;
    private final int tableId;
    private final int entryWidth;
    private final byte[] entries;

    /**
     * Creates a mapping table whose entries are indexed by reconstructed sample
     * value from zero through {@code entries.length / entryWidth - 1}.
     *
     * @param tableId table identifier written to the JPEG-LS stream (1..255)
     * @param entryWidth number of bytes in each entry (1..255)
     * @param entries concatenated opaque entry bytes
     */
    public JpegLsMappingTable(int tableId, int entryWidth, byte[] entries) {
        if (tableId < 1 || tableId > 255) {
            throw new IllegalArgumentException("JPEG-LS mapping table identifier must be 1..255");
        }
        if (entryWidth < 1 || entryWidth > 255) {
            throw new IllegalArgumentException("JPEG-LS mapping table entry width must be 1..255");
        }
        if (entries == null || entries.length == 0 || entries.length % entryWidth != 0) {
            throw new IllegalArgumentException("JPEG-LS mapping table entries must be aligned");
        }
        if (entries.length > MAXIMUM_ENTRY_BYTES) {
            throw new IllegalArgumentException("JPEG-LS mapping table exceeds Part 1 limits");
        }
        this.tableId = tableId;
        this.entryWidth = entryWidth;
        this.entries = Arrays.copyOf(entries, entries.length);
    }

    /** Returns the JPEG-LS table identifier. */
    public int getTableId() {
        return tableId;
    }

    /** Returns the byte width of each entry. */
    public int getEntryWidth() {
        return entryWidth;
    }

    /** Returns the number of entries in the table. */
    public int getEntryCount() {
        return entries.length / entryWidth;
    }

    /** Returns a defensive copy of all concatenated entry bytes. */
    public byte[] getEntries() {
        return Arrays.copyOf(entries, entries.length);
    }

    /** Returns a defensive copy of the entry for one reconstructed sample. */
    public byte[] getEntry(int reconstructedSample) {
        if (reconstructedSample < 0 || reconstructedSample >= getEntryCount()) {
            throw new IndexOutOfBoundsException("mapping table sample: " + reconstructedSample);
        }
        int offset = reconstructedSample * entryWidth;
        return Arrays.copyOfRange(entries, offset, offset + entryWidth);
    }
}
