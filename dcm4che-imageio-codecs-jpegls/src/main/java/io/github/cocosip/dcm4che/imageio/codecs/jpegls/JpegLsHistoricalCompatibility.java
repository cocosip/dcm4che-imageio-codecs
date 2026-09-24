package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import java.io.IOException;

import io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal.JpegLsFrameCodec;

/**
 * Explicit compatibility helpers for historical YBR_FULL_422 JPEG-LS data.
 *
 * <p>This path is deliberately separate from the standard DICOM JPEG-LS
 * adapter. It accepts the packed, interleaved 8-bit representation used by
 * older codecs, expands it to RGB, and reports the metadata that callers must
 * apply to the transcoded pixel data.</p>
 */
public final class JpegLsHistoricalCompatibility {
    private JpegLsHistoricalCompatibility() {
    }

    /**
     * Expands one packed interleaved YBR_FULL_422 frame to an interleaved RGB
     * frame and returns the corresponding output metadata.
     *
     * @param frame packed rows containing four bytes per two-pixel group
     * @param width frame width in pixels
     * @param height frame height in pixels
     * @param planarConfiguration whether the source is planar; planar input is rejected
     * @return normalized RGB frame and metadata
     * @throws IllegalArgumentException if geometry, layout, or frame length is invalid
     */
    public static NormalizedFrame normalizeYbrFull422ForEncode(byte[] frame, int width,
            int height, boolean planarConfiguration) {
        int groupsPerRow = validatePackedFrame(frame, width, height, planarConfiguration);
        byte[] rgb = new byte[checkedLength(width, height, 3, "RGB frame")];
        int output = 0;
        for (int y = 0; y < height; y++) {
            int rowOffset = y * groupsPerRow * 4;
            for (int group = 0; group < groupsPerRow; group++) {
                int input = rowOffset + group * 4;
                int pixel = group * 2;
                int cb = frame[input + 2] & 0xff;
                int cr = frame[input + 3] & 0xff;
                output = writeRgb(frame[input] & 0xff, cb, cr, rgb, output);
                if (pixel + 1 < width) {
                    output = writeRgb(frame[input + 1] & 0xff, cb, cr, rgb, output);
                }
            }
        }
        return new NormalizedFrame(rgb, "RGB", 0);
    }

    /**
     * Decodes a JPEG-LS frame that was historically carried with
     * YBR_FULL_422 metadata and returns its normalized RGB pixel bytes.
     *
     * @param jpegLsFrame complete JPEG-LS frame
     * @param width expected frame width
     * @param height expected frame height
     * @return normalized RGB frame and metadata
     * @throws IOException if the JPEG-LS frame is malformed or does not match
     *         the compatibility profile
     */
    public static NormalizedFrame decodeYbrFull422(byte[] jpegLsFrame, int width, int height)
            throws IOException {
        if (jpegLsFrame == null) {
            throw new NullPointerException("jpegLsFrame");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("JPEG-LS compatibility dimensions must be positive");
        }
        JpegLsFrameCodec.DecodedFrame decoded = JpegLsFrameCodec.decode(jpegLsFrame);
        if (decoded.width() != width || decoded.height() != height
                || decoded.precision() != 8 || decoded.components() != 3
                || decoded.hasMappedOutput()) {
            throw new IOException("JPEG-LS frame is not an 8-bit three-component compatibility frame");
        }
        int[] samples = decoded.samples();
        byte[] rgb = new byte[checkedLength(width, height, 3, "RGB frame")];
        if (samples.length != rgb.length) {
            throw new IOException("JPEG-LS compatibility sample count does not match geometry");
        }
        for (int i = 0; i < samples.length; i++) {
            if (samples[i] < 0 || samples[i] > 255) {
                throw new IOException("JPEG-LS compatibility sample is outside 8-bit range");
            }
            rgb[i] = (byte) samples[i];
        }
        return new NormalizedFrame(rgb, "RGB", 0);
    }

    private static int validatePackedFrame(byte[] frame, int width, int height,
            boolean planarConfiguration) {
        if (frame == null) {
            throw new NullPointerException("frame");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("JPEG-LS compatibility dimensions must be positive");
        }
        if (planarConfiguration) {
            throw new IllegalArgumentException("planar YBR_FULL_422 compatibility input is not supported");
        }
        long groupsPerRowLong = ((long) width + 1L) / 2L;
        if (groupsPerRowLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("YBR_FULL_422 row group count exceeds Java limits");
        }
        int groupsPerRow = (int) groupsPerRowLong;
        int expected = checkedLength(groupsPerRow, height, 4, "YBR_FULL_422 frame");
        if (frame.length != expected) {
            throw new IllegalArgumentException("YBR_FULL_422 frame length " + frame.length
                    + " does not match expected length " + expected);
        }
        return groupsPerRow;
    }

    private static int writeRgb(int y, int cb, int cr, byte[] output, int offset) {
        double blueDifference = cb - 128.0;
        double redDifference = cr - 128.0;
        output[offset++] = (byte) clamp(roundAwayFromZero(y + 1.402 * redDifference));
        output[offset++] = (byte) clamp(roundAwayFromZero(
                y - 0.344136 * blueDifference - 0.714136 * redDifference));
        output[offset++] = (byte) clamp(roundAwayFromZero(y + 1.772 * blueDifference));
        return offset;
    }

    private static int roundAwayFromZero(double value) {
        return value < 0 ? (int) Math.ceil(value - 0.5) : (int) Math.floor(value + 0.5);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int checkedLength(int first, int second, int multiplier, String name) {
        long length = (long) first * second * multiplier;
        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " exceeds Java array limits");
        }
        return (int) length;
    }

    /** Holds normalized pixel bytes and the metadata required by the caller. */
    public static final class NormalizedFrame {
        private final byte[] pixelData;
        private final String photometricInterpretation;
        private final int planarConfiguration;

        private NormalizedFrame(byte[] pixelData, String photometricInterpretation,
                int planarConfiguration) {
            this.pixelData = pixelData.clone();
            this.photometricInterpretation = photometricInterpretation;
            this.planarConfiguration = planarConfiguration;
        }

        /** Returns a defensive copy of interleaved RGB pixel bytes. */
        public byte[] pixelData() {
            return pixelData.clone();
        }

        /** Returns the output DICOM Photometric Interpretation. */
        public String photometricInterpretation() {
            return photometricInterpretation;
        }

        /** Returns the output DICOM Planar Configuration. */
        public int planarConfiguration() {
            return planarConfiguration;
        }
    }
}
