package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.IOException;

/** JPEG Extended Process 2/4 (SOF1, sequential Huffman, SF444). */
public final class ExtendedJpegCodec {
    private ExtendedJpegCodec() {
    }

    public static byte[] encode(JpegFrame frame) throws IOException {
        return BaselineJpegCodec.encodeExtended(frame);
    }

    public static byte[] encode(JpegFrame frame, JpegSampling sampling) throws IOException {
        return BaselineJpegCodec.encodeExtended(frame, sampling, 0);
    }

    public static byte[] encode(JpegFrame frame, JpegSampling sampling, float quality)
            throws IOException {
        return BaselineJpegCodec.encodeExtended(frame, sampling, quality);
    }

    public static byte[] encode(JpegFrame frame, JpegSampling sampling, int restartInterval,
            float quality) throws IOException {
        return BaselineJpegCodec.encodeExtended(frame, sampling, restartInterval, quality);
    }

    static byte[] encode(JpegFrame frame, int restartInterval) throws IOException {
        return BaselineJpegCodec.encodeExtended(frame, restartInterval);
    }

    public static JpegFrame decode(byte[] data) throws IOException {
        return BaselineJpegCodec.decodeExtended(data);
    }
}
