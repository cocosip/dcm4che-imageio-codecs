package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpegls.JpegLsMappingTable;

class JpegLsFrameCodecTest {
    @Test
    void encodesAndDecodesStandardMonoLosslessFrame() throws Exception {
        int[] samples = {0, 10, 20, 30, 30, 30, 40, 50, 50, 50, 49, 48};

        byte[] frame = JpegLsFrameCodec.encode(4, 3, 8, 1, 0, samples);

        assertArrayEquals(samples, JpegLsFrameCodec.decode(frame).samples());
    }

    @Test
    void independentFrameDecodersCanRunConcurrently() throws Exception {
        int[] firstSamples = {0, 1000, 2000, 3000};
        int[] secondSamples = {65535, 50000, 40000, 30000};
        byte[] first = JpegLsFrameCodec.encode(2, 2, 16, 1, 0, firstSamples);
        byte[] second = JpegLsFrameCodec.encode(2, 2, 16, 1, 2, secondSamples);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<int[]> firstDecoded = executor.submit(() -> JpegLsFrameCodec.decode(first).samples());
            Future<int[]> secondDecoded = executor.submit(() -> JpegLsFrameCodec.decode(second).samples());

            assertArrayEquals(firstSamples, firstDecoded.get());
            int[] actualSecond = secondDecoded.get();
            for (int i = 0; i < secondSamples.length; i++) {
                assertTrue(Math.abs(secondSamples[i] - actualSecond[i]) <= 2,
                        "concurrent near-lossless sample " + i + " exceeds tolerance");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void encodesAndDecodesSixteenBitNearLosslessFrame() throws Exception {
        int[] samples = {0, 1000, 2000, 3000, 3000, 3002, 4000, 5000};

        byte[] frame = JpegLsFrameCodec.encode(4, 2, 16, 1, 2, samples);
        int[] decoded = JpegLsFrameCodec.decode(frame).samples();

        for (int i = 0; i < samples.length; i++) {
            if (Math.abs(samples[i] - decoded[i]) > 2) {
                throw new AssertionError("sample " + i + " exceeds NEAR tolerance");
            }
        }
    }

    @Test
    void rejectsMalformedFrameAndTrailingData() throws Exception {
        assertThrows(JpegLsException.class, () -> JpegLsFrameCodec.decode(new byte[] {(byte) 0xff}));
        byte[] frame = JpegLsFrameCodec.encode(1, 1, 8, 1, 0, new int[] {7});
        assertThrows(JpegLsException.class, () -> JpegLsFrameCodec.decode(concat(frame, new byte[] {1})));
    }

    @Test
    void rejectsNullCodestreamWithCodecException() {
        assertThrows(JpegLsException.class, () -> JpegLsFrameCodec.decode(null));
    }

    @Test
    void rejectsDecodedSampleCountOverflowBeforeAllocation() throws Exception {
        byte[] frame = JpegLsFrameCodec.encode(1, 1, 8, 1, 0, new int[] {7});
        frame[7] = (byte) 0xff;
        frame[8] = (byte) 0xff;
        frame[9] = (byte) 0xff;
        frame[10] = (byte) 0xff;

        assertThrows(JpegLsException.class, () -> JpegLsFrameCodec.decode(frame));
    }

    @Test
    void rejectsRestartDecodedSampleCountOverflowBeforeAllocation() throws Exception {
        byte[] frame = JpegLsFrameCodec.encode(1, 1, 8, 1, 0, 0, 1, new int[] {7});
        frame[8] = (byte) 0xc3;
        frame[9] = (byte) 0x50;
        frame[10] = (byte) 0xc3;
        frame[11] = (byte) 0x50;

        assertThrows(JpegLsException.class, () -> JpegLsFrameCodec.decode(frame));
    }

    @Test
    void writerRejectsDimensionsThatNeedOversizeMarker() {
        assertThrows(JpegLsException.class,
                () -> JpegLsFrameCodec.encode(65536, 1, 8, 1, 0, new int[65536]));
    }

    @Test
    void writerRejectsNullSamplesWithCodecException() {
        assertThrows(JpegLsException.class,
                () -> JpegLsFrameCodec.encode(1, 1, 8, 1, 0, (int[]) null));
    }

    @Test
    void encodesAndDecodesRgbSampleAndLineInterleavedFrames() throws Exception {
        int[] samples = {1, 10, 100, 2, 20, 110, 3, 30, 120, 4, 40, 130};
        for (int mode : new int[] {2, 1}) {
            byte[] frame = JpegLsFrameCodec.encode(2, 2, 8, 3, 0, mode, samples);
            assertArrayEquals(samples, JpegLsFrameCodec.decode(frame).samples());
        }
    }

    @Test
    void encodesAndDecodesRestartIntervalsWithMarkerRollover() throws Exception {
        int[] samples = new int[20];
        for (int i = 0; i < samples.length; i++) samples[i] = i * 7 & 0xff;

        byte[] frame = JpegLsFrameCodec.encode(2, 10, 8, 1, 0, 0, 1, samples);

        assertArrayEquals(samples, JpegLsFrameCodec.decode(frame).samples());
        assertContains(frame, new byte[] {(byte) 0xff, (byte) 0xd7});
        assertContains(frame, new byte[] {(byte) 0xff, (byte) 0xd0});
    }

    @Test
    void rejectsWrongRestartMarkerOrder() throws Exception {
        int[] samples = {1, 2, 3, 4, 5, 6};
        byte[] frame = JpegLsFrameCodec.encode(2, 3, 8, 1, 0, 0, 1, samples);
        int marker = indexOf(frame, new byte[] {(byte) 0xff, (byte) 0xd0});
        frame[marker + 1] = (byte) 0xd1;

        assertThrows(JpegLsException.class, () -> JpegLsFrameCodec.decode(frame));
    }

    @Test
    void encodesAndDecodesRgbAsThreeNonInterleavedScans() throws Exception {
        int[] samples = {1, 10, 100, 2, 20, 110, 3, 30, 120, 4, 40, 130};

        byte[] frame = JpegLsFrameCodec.encode(2, 2, 8, 3, 0, 0, samples);

        assertArrayEquals(samples, JpegLsFrameCodec.decode(frame).samples());
        if (count(frame, new byte[] {(byte) 0xff, (byte) 0xda}) != 3) {
            throw new AssertionError("expected one SOS per RGB component");
        }
    }

    @Test
    void decodesZeroHeightFrameCompletedByDnlInScanData() throws Exception {
        int[] samples = {1, 2, 3, 4, 5, 6};
        byte[] ordinary = JpegLsFrameCodec.encode(2, 3, 8, 1, 0, samples);
        byte[] withDnl = replaceHeightAndInsertDnl(ordinary, 3);

        assertArrayEquals(samples, JpegLsFrameCodec.decode(withDnl).samples());
    }

    @Test
    void decodesEachComponentScanWithItsOwnNearAndRestartState() throws Exception {
        int[] first = {10, 20, 30, 40};
        int[] second = {100, 102, 104, 106};
        int[] third = {200, 201, 202, 203};
        byte[] baseFrame = JpegLsFrameCodec.encode(2, 2, 8, 3, 0, 0,
                new int[12]);
        byte[] firstFrame = JpegLsFrameCodec.encode(2, 2, 8, 1, 0, 0, 0, first);
        byte[] secondFrame = JpegLsFrameCodec.encode(2, 2, 8, 1, 2, 0, 1, second);
        byte[] thirdFrame = withDri(JpegLsFrameCodec.encode(2, 2, 8, 1, 0, 0, 0, third), 0);

        byte[] combined = combineComponentScans(baseFrame, firstFrame, secondFrame, thirdFrame);
        assertTrue(count(combined, new byte[] {(byte) 0xff, (byte) 0xd0}) >= 1);
        JpegLsFrameCodec.DecodedFrame decodedFrame = JpegLsFrameCodec.decode(combined);
        assertEquals(2, decodedFrame.nearLossless());
        int[] decoded = decodedFrame.samples();

        assertArrayEquals(first, new int[] {decoded[0], decoded[3], decoded[6], decoded[9]});
        for (int i = 0; i < second.length; i++) {
            assertTrue(Math.abs(second[i] - decoded[i * 3 + 1]) <= 2,
                    "component sample " + i + " exceeds scan NEAR");
        }
    }

    @Test
    void skipsApplicationAndCommentMarkersBetweenScans() throws Exception {
        byte[] base = JpegLsFrameCodec.encode(2, 2, 8, 3, 0, 0, new int[12]);
        byte[] first = JpegLsFrameCodec.encode(2, 2, 8, 1, 0, 0, 0,
                new int[] {1, 2, 3, 4});
        byte[] second = JpegLsFrameCodec.encode(2, 2, 8, 1, 0, 0, 0,
                new int[] {5, 6, 7, 8});
        byte[] third = JpegLsFrameCodec.encode(2, 2, 8, 1, 0, 0, 0,
                new int[] {9, 10, 11, 12});

        byte[] combined = combineComponentScans(base, first, second, third);
        int secondSos = indexOf(combined, new byte[] {(byte) 0xff, (byte) 0xda},
                indexOf(combined, new byte[] {(byte) 0xff, (byte) 0xda}) + 2);
        byte[] markers = {(byte) 0xff, (byte) 0xe1, 0, 4, 1, 2,
                (byte) 0xff, (byte) 0xfe, 0, 4, 'o', 'k'};
        byte[] withMarkers = new byte[combined.length + markers.length];
        System.arraycopy(combined, 0, withMarkers, 0, secondSos);
        System.arraycopy(markers, 0, withMarkers, secondSos, markers.length);
        System.arraycopy(combined, secondSos, withMarkers, secondSos + markers.length,
                combined.length - secondSos);

        assertArrayEquals(new int[] {1, 5, 9, 2, 6, 10, 3, 7, 11, 4, 8, 12},
                JpegLsFrameCodec.decode(withMarkers).samples());
    }

    @Test
    void decodesMappingTableEntriesFromReconstructedSamples() throws Exception {
        byte[] ordinary = JpegLsFrameCodec.encode(2, 2, 2, 1, 0,
                new int[] {0, 1, 2, 3});
        byte[] mapped = insertMappingTable(ordinary, 7, 2,
                new byte[] {10, 11, 20, 21, 30, 31, 40, 41});

        JpegLsFrameCodec.DecodedFrame decoded = JpegLsFrameCodec.decode(mapped);

        assertArrayEquals(new int[] {0, 1, 2, 3}, decoded.samples());
        assertTrue(decoded.hasMappedOutput());
        assertEquals(1, decoded.mappedComponents().size());
        JpegLsFrameCodec.DecodedFrame.MappedComponent component =
                decoded.mappedComponents().get(0);
        assertEquals(0, component.component());
        assertEquals(7, component.tableId());
        assertEquals(2, component.entryWidth());
        assertArrayEquals(new byte[] {10, 11, 20, 21, 30, 31, 40, 41}, component.bytes());
    }

    @Test
    void writerAndReaderRoundTripMappingTableContinuation() throws Exception {
        byte[] entries = new byte[65536];
        for (int i = 0; i < entries.length; i++) entries[i] = (byte) i;
        JpegLsMappingTable table = new JpegLsMappingTable(12, 1, entries);
        byte[] frame = JpegLsFrameCodec.encode(2, 1, 16, 1, 0, 0, 0,
                new int[] {0, 65535}, Collections.singletonList(table),
                Collections.singletonMap(1, 12));

        assertTrue(count(frame, new byte[] {(byte) 0xff, (byte) 0xf8}) >= 2);
        JpegLsFrameCodec.DecodedFrame decoded = JpegLsFrameCodec.decode(frame);
        assertArrayEquals(new int[] {0, 65535}, decoded.samples());
        assertArrayEquals(new byte[] {0, (byte) 0xff},
                decoded.mappedComponents().get(0).bytes());
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = new byte[left.length + right.length];
        System.arraycopy(left, 0, result, 0, left.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    private static void assertContains(byte[] bytes, byte[] expected) {
        if (indexOf(bytes, expected) < 0) throw new AssertionError("byte sequence not found");
    }

    private static int indexOf(byte[] bytes, byte[] expected) {
        return indexOf(bytes, expected, 0);
    }

    private static int indexOf(byte[] bytes, byte[] expected, int start) {
        outer: for (int i = start; i <= bytes.length - expected.length; i++) {
            for (int j = 0; j < expected.length; j++) {
                if (bytes[i + j] != expected[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static int count(byte[] bytes, byte[] expected) {
        int count = 0;
        for (int offset = 0; offset <= bytes.length - expected.length; offset++) {
            boolean match = true;
            for (int i = 0; i < expected.length; i++) {
                if (bytes[offset + i] != expected[i]) { match = false; break; }
            }
            if (match) count++;
        }
        return count;
    }

    private static byte[] replaceHeightAndInsertDnl(byte[] frame, int height) {
        byte[] result = new byte[frame.length + 6];
        System.arraycopy(frame, 0, result, 0, frame.length - 2);
        result[7] = 0;
        result[8] = 0;
        int offset = frame.length - 2;
        result[offset] = (byte) 0xff;
        result[offset + 1] = (byte) 0xdc;
        result[offset + 2] = 0;
        result[offset + 3] = 4;
        result[offset + 4] = (byte) (height >>> 8);
        result[offset + 5] = (byte) height;
        result[offset + 6] = (byte) 0xff;
        result[offset + 7] = (byte) 0xd9;
        return result;
    }

    private static byte[] combineComponentScans(byte[] base, byte[]... scans) {
        int baseSos = indexOf(base, new byte[] {(byte) 0xff, (byte) 0xda});
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        output.write(base, 0, baseSos);
        for (int i = 0; i < scans.length; i++) {
            byte[] scan = scans[i];
            int sos = indexOf(scan, new byte[] {(byte) 0xff, (byte) 0xda});
            int sosEnd = sos + 2 + u16(scan, sos + 2);
            int sof = indexOf(scan, new byte[] {(byte) 0xff, (byte) 0xf7});
            int afterSof = sof + 2 + u16(scan, sof + 2);
            int eoi = indexOf(scan, new byte[] {(byte) 0xff, (byte) 0xd9});
            byte[] header = java.util.Arrays.copyOfRange(scan, afterSof, sosEnd);
            header[sos - afterSof + 5] = (byte) (i + 1);
            output.write(header, 0, header.length);
            output.write(scan, sosEnd, eoi - sosEnd);
        }
        output.write(0xff);
        output.write(0xd9);
        return output.toByteArray();
    }

    private static int u16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 8 | bytes[offset + 1] & 0xff;
    }

    private static byte[] withDri(byte[] frame, int interval) {
        int sos = indexOf(frame, new byte[] {(byte) 0xff, (byte) 0xda});
        byte[] result = new byte[frame.length + 6];
        System.arraycopy(frame, 0, result, 0, sos);
        result[sos] = (byte) 0xff;
        result[sos + 1] = (byte) 0xdd;
        result[sos + 2] = 0;
        result[sos + 3] = 4;
        result[sos + 4] = (byte) (interval >>> 8);
        result[sos + 5] = (byte) interval;
        System.arraycopy(frame, sos, result, sos + 6, frame.length - sos);
        return result;
    }

    private static byte[] insertMappingTable(byte[] frame, int tableId, int entryWidth,
            byte[] entries) {
        int sos = indexOf(frame, new byte[] {(byte) 0xff, (byte) 0xda});
        byte[] payload = new byte[3 + entries.length];
        payload[0] = 2;
        payload[1] = (byte) tableId;
        payload[2] = (byte) entryWidth;
        System.arraycopy(entries, 0, payload, 3, entries.length);
        byte[] segment = new byte[payload.length + 4];
        segment[0] = (byte) 0xff;
        segment[1] = (byte) 0xf8;
        segment[2] = (byte) ((payload.length + 2) >>> 8);
        segment[3] = (byte) (payload.length + 2);
        System.arraycopy(payload, 0, segment, 4, payload.length);
        byte[] result = new byte[frame.length + segment.length];
        System.arraycopy(frame, 0, result, 0, sos);
        System.arraycopy(segment, 0, result, sos, segment.length);
        System.arraycopy(frame, sos, result, sos + segment.length, frame.length - sos);
        result[sos + segment.length + 6] = (byte) tableId;
        return result;
    }

}
