package io.github.cocosip.dcm4che.imageio.codecs.rle.internal;

import java.util.Arrays;

import javax.imageio.IIOException;

import org.dcm4che3.imageio.codec.ImageDescriptor;

public final class RleFrameCodec {

    private RleFrameCodec() {
    }

    public static byte[] encode(ImageDescriptor descriptor, byte[] rawFrame)
            throws IIOException {
        return encode(RlePixelLayout.from(descriptor), rawFrame);
    }

    public static byte[] decode(ImageDescriptor descriptor, byte[] rleFrame)
            throws IIOException {
        return decode(RlePixelLayout.from(descriptor), rleFrame);
    }

    static byte[] encode(RlePixelLayout layout, byte[] rawFrame) throws IIOException {
        if (layout == null) {
            throw new NullPointerException("layout");
        }
        if (rawFrame == null) {
            throw new NullPointerException("rawFrame");
        }
        if (rawFrame.length != layout.frameLength()) {
            throw error("raw frame length " + rawFrame.length
                    + " does not match expected length " + layout.frameLength());
        }

        byte[][] segments = new byte[layout.segmentCount()][];
        int[] offsets = new int[segments.length];
        int frameLength = RleHeader.LENGTH;
        for (int segmentIndex = 0; segmentIndex < segments.length; segmentIndex++) {
            offsets[segmentIndex] = frameLength;
            byte[] segment = extractSegment(rawFrame, layout, segmentIndex);
            byte[] encoded = RleSegmentCodec.encode(segment);
            segments[segmentIndex] = encoded;
            frameLength = checkedAdd(frameLength, encoded.length, "encoded frame length");
            if ((encoded.length & 1) != 0) {
                frameLength = checkedAdd(frameLength, 1, "encoded frame length");
            }
        }

        byte[] frame = new byte[frameLength];
        byte[] header = RleHeader.of(offsets).toBytes();
        System.arraycopy(header, 0, frame, 0, header.length);
        int outputOffset = RleHeader.LENGTH;
        for (byte[] segment : segments) {
            System.arraycopy(segment, 0, frame, outputOffset, segment.length);
            outputOffset += segment.length;
            if ((segment.length & 1) != 0) {
                frame[outputOffset++] = 0;
            }
        }
        return frame;
    }

    static byte[] decode(RlePixelLayout layout, byte[] rleFrame) throws IIOException {
        if (layout == null) {
            throw new NullPointerException("layout");
        }
        if (rleFrame == null) {
            throw new NullPointerException("rleFrame");
        }

        RleHeader header = RleHeader.parse(rleFrame);
        if (header.segmentCount() != layout.segmentCount()) {
            throw error("segment count " + header.segmentCount()
                    + " does not match expected segment count " + layout.segmentCount());
        }

        byte[] rawFrame = new byte[layout.frameLength()];
        for (int segmentIndex = 0; segmentIndex < header.segmentCount(); segmentIndex++) {
            int start = header.segmentOffset(segmentIndex);
            int end = segmentIndex + 1 < header.segmentCount()
                    ? header.segmentOffset(segmentIndex + 1) : rleFrame.length;
            byte[] encodedSegment = Arrays.copyOfRange(rleFrame, start, end);
            byte[] segment = decodePossiblyPadded(encodedSegment, layout.pixelCount());
            copySegment(segment, rawFrame, layout, segmentIndex);
        }
        return rawFrame;
    }

    private static byte[] extractSegment(
            byte[] rawFrame, RlePixelLayout layout, int segmentIndex) {
        byte[] segment = new byte[layout.pixelCount()];
        int sampleIndex = segmentIndex / layout.bytesAllocated();
        int byteIndex = layout.bytesAllocated() - 1
                - (segmentIndex % layout.bytesAllocated());
        for (int pixelIndex = 0; pixelIndex < segment.length; pixelIndex++) {
            segment[pixelIndex] = rawFrame[
                    layout.rawOffset(pixelIndex, sampleIndex, byteIndex)];
        }
        return segment;
    }

    private static void copySegment(byte[] segment, byte[] rawFrame,
            RlePixelLayout layout, int segmentIndex) {
        int sampleIndex = segmentIndex / layout.bytesAllocated();
        int byteIndex = layout.bytesAllocated() - 1
                - (segmentIndex % layout.bytesAllocated());
        for (int pixelIndex = 0; pixelIndex < segment.length; pixelIndex++) {
            rawFrame[layout.rawOffset(pixelIndex, sampleIndex, byteIndex)] = segment[pixelIndex];
        }
    }

    private static byte[] decodePossiblyPadded(byte[] encoded, int expectedLength)
            throws IIOException {
        try {
            return RleSegmentCodec.decode(encoded, expectedLength);
        } catch (IIOException original) {
            if (encoded.length == 0 || encoded[encoded.length - 1] != 0) {
                throw original;
            }
            return RleSegmentCodec.decode(
                    Arrays.copyOf(encoded, encoded.length - 1), expectedLength);
        }
    }

    private static int checkedAdd(int left, int right, String name) throws IIOException {
        long value = (long) left + right;
        if (value > Integer.MAX_VALUE) {
            throw error(name + " exceeds Java array limits: " + value);
        }
        return (int) value;
    }

    private static IIOException error(String message) {
        return new IIOException("RLE Lossless " + message);
    }
}
