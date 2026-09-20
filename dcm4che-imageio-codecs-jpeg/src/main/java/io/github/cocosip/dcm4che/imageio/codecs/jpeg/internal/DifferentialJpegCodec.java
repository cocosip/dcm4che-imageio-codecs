package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.IOException;

/**
 * Differential Huffman JPEG frame adapters.
 *
 * <p>The standalone frame API uses an all-zero reference frame. Hierarchical
 * callers can use the same marker-level codecs while supplying the reference
 * reconstruction at their orchestration boundary.</p>
 */
public final class DifferentialJpegCodec {
    private DifferentialJpegCodec() {
    }

    public static byte[] encodeSequential(JpegFrame frame) throws IOException {
        return BaselineJpegCodec.encodeSequential(frame, 0xc5, JpegSampling.SF444, 0);
    }

    public static byte[] encodeSequential(JpegFrame frame, JpegSampling sampling)
            throws IOException {
        return BaselineJpegCodec.encodeSequential(frame, 0xc5, sampling, 0);
    }

    public static JpegFrame decodeSequential(byte[] data) throws IOException {
        return BaselineJpegCodec.decodeSequential(data, 0xc5);
    }

    public static byte[] encodeProgressive(JpegFrame frame) throws IOException {
        return ProgressiveJpegCodec.encode(frame, JpegSampling.SF444, 0xc6);
    }

    public static byte[] encodeProgressive(JpegFrame frame, JpegSampling sampling)
            throws IOException {
        return ProgressiveJpegCodec.encode(frame, sampling, 0xc6);
    }

    public static JpegFrame decodeProgressive(byte[] data) throws IOException {
        return ProgressiveJpegCodec.decode(data, 0xc6);
    }

    public static byte[] encodeLossless(JpegFrame frame) throws IOException {
        return LosslessJpegCodec.encode(frame, 1, 0, 0, 0xc7);
    }

    public static JpegFrame decodeLossless(byte[] data) throws IOException {
        return LosslessJpegCodec.decode(data, 0, 0xc7);
    }
}
