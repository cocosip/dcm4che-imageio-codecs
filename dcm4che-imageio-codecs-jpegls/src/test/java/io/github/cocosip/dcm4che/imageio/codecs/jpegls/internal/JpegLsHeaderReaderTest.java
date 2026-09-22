package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.imageio.stream.MemoryCacheImageInputStream;

import org.junit.jupiter.api.Test;

class JpegLsHeaderReaderTest {
    @Test
    void readsSof55AndSosWhileSkippingApplicationAndCommentSegments() throws Exception {
        byte[] stream = codestream(
                segment(0xe0, 1, 2, 3),
                segment(0xfe, 4, 5),
                sof55(12, 5, 7, new int[][] {{1, 0x11, 0}, {2, 0x11, 0}, {3, 0x11, 0}}),
                sos(new int[][] {{1, 0}, {2, 0}, {3, 0}}, 2, 2, 0));

        JpegLsHeader header = read(stream);

        assertEquals(12, header.frame().precision());
        assertEquals(7, header.frame().width());
        assertEquals(5, header.frame().height());
        assertEquals(3, header.frame().componentCount());
        assertEquals(1, header.frame().component(0).identifier());
        assertEquals(3, header.scan().componentCount());
        assertEquals(2, header.scan().nearLossless());
        assertEquals(JpegLsInterleaveMode.SAMPLE, header.scan().interleaveMode());
        assertEquals(0, header.scan().pointTransform());
    }

    @Test
    void acceptsSingleComponentNonInterleavedScan() throws Exception {
        byte[] stream = codestream(
                sof55(8, 2, 3, new int[][] {{1, 0x11, 0}}),
                sos(new int[][] {{1, 0}}, 0, 0, 0));

        JpegLsHeader header = read(stream);

        assertEquals(JpegLsInterleaveMode.NONE, header.scan().interleaveMode());
        assertEquals(1, header.scan().componentCount());
    }

    @Test
    void rejectsDuplicateStartOfImage() throws Exception {
        byte[] stream = codestream(marker(0xd8), sof55(8, 1, 1, new int[][] {{1, 0x11, 0}}));

        assertThrows(JpegLsException.class, () -> read(stream));
    }

    @Test
    void rejectsDuplicateFrameHeader() throws Exception {
        byte[] frame = sof55(8, 1, 1, new int[][] {{1, 0x11, 0}});
        byte[] stream = codestream(frame, frame, sos(new int[][] {{1, 0}}, 0, 0, 0));

        assertThrows(JpegLsException.class, () -> read(stream));
    }

    @Test
    void rejectsEndOfImageBeforeScan() throws Exception {
        byte[] stream = codestream(
                sof55(8, 1, 1, new int[][] {{1, 0x11, 0}}),
                marker(0xd9));

        assertThrows(JpegLsException.class, () -> read(stream));
    }

    @Test
    void rejectsUnknownAndPartTwoFrameMarkers() throws Exception {
        byte[] unknown = codestream(segment(0xdb, 0));
        byte[] partTwo = codestream(segment(0xf9, 0));

        assertThrows(JpegLsException.class, () -> read(unknown));
        assertThrows(JpegLsException.class, () -> read(partTwo));
    }

    @Test
    void rejectsInvalidFramePrecisionAndDuplicateComponentIdentifiers() throws Exception {
        byte[] invalidPrecision = codestream(
                sof55(1, 1, 1, new int[][] {{1, 0x11, 0}}),
                sos(new int[][] {{1, 0}}, 0, 0, 0));
        byte[] duplicateIdentifiers = codestream(
                sof55(8, 1, 1, new int[][] {{1, 0x11, 0}, {1, 0x11, 0}}),
                sos(new int[][] {{1, 0}}, 0, 0, 0));

        assertThrows(JpegLsException.class, () -> read(invalidPrecision));
        assertThrows(JpegLsException.class, () -> read(duplicateIdentifiers));
    }

    @Test
    void rejectsUnsupportedSamplingAndQuantizationSelectors() throws Exception {
        byte[] sampling = codestream(
                sof55(8, 1, 1, new int[][] {{1, 0x21, 0}}),
                sos(new int[][] {{1, 0}}, 0, 0, 0));
        byte[] quantization = codestream(
                sof55(8, 1, 1, new int[][] {{1, 0x11, 1}}),
                sos(new int[][] {{1, 0}}, 0, 0, 0));

        assertThrows(JpegLsException.class, () -> read(sampling));
        assertThrows(JpegLsException.class, () -> read(quantization));
    }

    @Test
    void rejectsScanSelectorsNotPresentInFrameAndDuplicateScanSelectors() throws Exception {
        byte[] missing = codestream(
                sof55(8, 1, 1, new int[][] {{1, 0x11, 0}}),
                sos(new int[][] {{2, 0}}, 0, 0, 0));
        byte[] duplicate = codestream(
                sof55(8, 1, 1, new int[][] {{1, 0x11, 0}, {2, 0x11, 0}}),
                sos(new int[][] {{1, 0}, {1, 0}}, 0, 1, 0));

        assertThrows(JpegLsException.class, () -> read(missing));
        assertThrows(JpegLsException.class, () -> read(duplicate));
    }

    @Test
    void rejectsInterleavedSingleComponentScanAndInvalidPointTransform() throws Exception {
        byte[] interleavedMono = codestream(
                sof55(8, 1, 1, new int[][] {{1, 0x11, 0}}),
                sos(new int[][] {{1, 0}}, 0, 1, 0));
        byte[] pointTransform = codestream(
                sof55(8, 1, 1, new int[][] {{1, 0x11, 0}}),
                sos(new int[][] {{1, 0}}, 0, 0, 1));

        assertThrows(JpegLsException.class, () -> read(interleavedMono));
        assertThrows(JpegLsException.class, () -> read(pointTransform));
    }

    @Test
    void rejectsNonInterleavedMultiComponentScan() throws Exception {
        byte[] stream = codestream(
                sof55(8, 1, 1, new int[][] {{1, 0x11, 0}, {2, 0x11, 0}, {3, 0x11, 0}}),
                sos(new int[][] {{1, 0}, {2, 0}, {3, 0}}, 0, 0, 0));

        assertThrows(JpegLsException.class, () -> read(stream));
    }

    @Test
    void rejectsTruncatedMetadataSegment() throws Exception {
        byte[] stream = concat(marker(0xd8), new byte[] {(byte) 0xff, (byte) 0xe1, 0, 6, 1});

        assertThrows(IOException.class, () -> read(stream));
    }

    @Test
    void appliesPresetMappingAndOversizeDimensionsBeforeScan() throws Exception {
        byte[] stream = codestream(
                segment(0xf8, new byte[] {4, 3, 0x01, 0x11, 0x70, 0x01, 0x38, (byte) 0x80}),
                sof55(16, 0, 0, new int[][] {{1, 0x11, 0}}),
                segment(0xf8, new byte[] {1, 0, 3, 0, 1, 0, 2, 0, 3, 0, 64}),
                segment(0xf8, new byte[] {2, 5, 1, 10, 20, 30, 40}),
                sos(new int[][] {{1, 5}}, 0, 0, 0));

        JpegLsHeader header = read(stream);

        assertEquals(80000, header.frame().width());
        assertEquals(70000, header.frame().height());
        assertEquals(3, header.presetCodingParameters().maximumSampleValue());
        assertEquals(4, header.mappingTable(5).entryCount());
    }

    @Test
    void rejectsMissingMappingTableAndInterruptedContinuation() throws Exception {
        byte[] missing = codestream(
                sof55(8, 1, 1, new int[][] {{1, 0x11, 0}}),
                sos(new int[][] {{1, 7}}, 0, 0, 0));

        byte[] firstChunk = new byte[65533];
        firstChunk[0] = 2;
        firstChunk[1] = 7;
        firstChunk[2] = 1;
        byte[] continuation = new byte[] {3, 7, 1, 1, 2, 3, 4, 5, 6};
        byte[] interrupted = codestream(
                sof55(16, 1, 1, new int[][] {{1, 0x11, 0}}),
                segment(0xf8, firstChunk),
                segment(0xfe, 1),
                segment(0xf8, continuation),
                sos(new int[][] {{1, 7}}, 0, 0, 0));

        assertThrows(JpegLsException.class, () -> read(missing));
        assertThrows(JpegLsException.class, () -> read(interrupted));
    }

    @Test
    void permitsZeroFrameHeightForLaterDnl() throws Exception {
        byte[] stream = codestream(
                sof55(8, 0, 3, new int[][] {{1, 0x11, 0}}),
                sos(new int[][] {{1, 0}}, 0, 0, 0));

        JpegLsHeader header = read(stream);

        assertEquals(0, header.frame().height());
        header.frame().applyDnl(new byte[] {0, 2});
        assertEquals(2, header.frame().height());
    }

    private static JpegLsHeader read(byte[] stream) throws IOException {
        MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(stream));
        return new JpegLsHeaderReader(input).read();
    }

    private static byte[] codestream(byte[]... segments) throws IOException {
        byte[][] all = new byte[segments.length + 1][];
        all[0] = marker(0xd8);
        System.arraycopy(segments, 0, all, 1, segments.length);
        return concat(all);
    }

    private static byte[] sof55(int precision, int height, int width, int[][] components) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(payload);
        data.writeByte(precision);
        data.writeShort(height);
        data.writeShort(width);
        data.writeByte(components.length);
        for (int[] component : components) {
            data.writeByte(component[0]);
            data.writeByte(component[1]);
            data.writeByte(component[2]);
        }
        return segment(0xf7, payload.toByteArray());
    }

    private static byte[] sos(int[][] components, int near, int interleave, int pointTransform)
            throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(payload);
        data.writeByte(components.length);
        for (int[] component : components) {
            data.writeByte(component[0]);
            data.writeByte(component[1]);
        }
        data.writeByte(near);
        data.writeByte(interleave);
        data.writeByte(pointTransform);
        return segment(0xda, payload.toByteArray());
    }

    private static byte[] segment(int code, int... payload) throws IOException {
        byte[] bytes = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            bytes[i] = (byte) payload[i];
        }
        return segment(code, bytes);
    }

    private static byte[] segment(int code, byte[] payload) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(output);
        data.writeByte(0xff);
        data.writeByte(code);
        data.writeShort(payload.length + 2);
        data.write(payload);
        return output.toByteArray();
    }

    private static byte[] marker(int code) {
        return new byte[] {(byte) 0xff, (byte) code};
    }

    private static byte[] concat(byte[]... arrays) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            output.write(array);
        }
        return output.toByteArray();
    }
}
