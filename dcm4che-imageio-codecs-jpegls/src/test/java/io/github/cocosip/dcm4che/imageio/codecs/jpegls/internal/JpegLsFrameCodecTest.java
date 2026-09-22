package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JpegLsFrameCodecTest {
    @Test
    void encodesAndDecodesStandardMonoLosslessFrame() throws Exception {
        int[] samples = {0, 10, 20, 30, 30, 30, 40, 50, 50, 50, 49, 48};

        byte[] frame = JpegLsFrameCodec.encode(4, 3, 8, 1, 0, samples);

        assertArrayEquals(samples, JpegLsFrameCodec.decode(frame).samples());
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
        outer: for (int i = 0; i <= bytes.length - expected.length; i++) {
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

}
