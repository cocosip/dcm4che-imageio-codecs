package io.github.cocosip.dcm4che.imageio.codecs.rle.internal;

import java.util.Arrays;

import javax.imageio.IIOException;

final class RleHeader {
    static final int LENGTH = 64;
    static final int MAX_SEGMENT_COUNT = 15;

    private final int[] segmentOffsets;

    private RleHeader(int[] segmentOffsets) {
        this.segmentOffsets = segmentOffsets;
    }

    static RleHeader parse(byte[] frame) throws IIOException {
        if (frame == null) {
            throw new NullPointerException("frame");
        }
        if (frame.length < LENGTH) {
            throw error("frame is shorter than the required 64 byte header");
        }

        int segmentCount = readIntLittleEndian(frame, 0);
        validateSegmentCount(segmentCount);
        int[] offsets = new int[segmentCount];
        for (int index = 0; index < segmentCount; index++) {
            int offset = readIntLittleEndian(frame, 4 + index * 4);
            if (offset < LENGTH || offset >= frame.length) {
                throw error("segment offset " + offset + " is outside the frame");
            }
            offsets[index] = offset;
        }
        validateOffsets(offsets);
        return new RleHeader(offsets);
    }

    static RleHeader of(int[] segmentOffsets) throws IIOException {
        if (segmentOffsets == null) {
            throw new NullPointerException("segmentOffsets");
        }
        validateSegmentCount(segmentOffsets.length);
        int[] copy = segmentOffsets.clone();
        validateOffsets(copy);
        return new RleHeader(copy);
    }

    int segmentCount() {
        return segmentOffsets.length;
    }

    int segmentOffset(int index) {
        return segmentOffsets[index];
    }

    int[] segmentOffsets() {
        return segmentOffsets.clone();
    }

    byte[] toBytes() {
        byte[] bytes = new byte[LENGTH];
        writeIntLittleEndian(bytes, 0, segmentOffsets.length);
        for (int index = 0; index < segmentOffsets.length; index++) {
            writeIntLittleEndian(bytes, 4 + index * 4, segmentOffsets[index]);
        }
        return bytes;
    }

    private static void validateSegmentCount(int count) throws IIOException {
        if (count < 1 || count > MAX_SEGMENT_COUNT) {
            throw error("segment count " + count + " is outside the valid range 1..15");
        }
    }

    private static void validateOffsets(int[] offsets) throws IIOException {
        if (offsets[0] != LENGTH) {
            throw error("first segment offset must be 64: " + offsets[0]);
        }
        for (int index = 1; index < offsets.length; index++) {
            if (offsets[index] <= offsets[index - 1]) {
                throw error("segment offsets must be strictly increasing: "
                        + Arrays.toString(offsets));
            }
        }
    }

    private static int readIntLittleEndian(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | (bytes[offset + 3] << 24);
    }

    private static void writeIntLittleEndian(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }

    private static IIOException error(String message) {
        return new IIOException("RLE Lossless " + message);
    }
}
