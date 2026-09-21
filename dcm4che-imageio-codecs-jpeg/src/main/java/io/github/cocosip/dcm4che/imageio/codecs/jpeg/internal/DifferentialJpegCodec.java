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

    /** Encodes a frame relative to a caller-owned reference frame. */
    public static byte[] encode(JpegFrame frame, JpegFrame reference,
            DifferentialProcess process) throws IOException {
        validateReference(frame, reference);
        JpegFrame delta = difference(frame, reference);
        switch (process) {
        case SEQUENTIAL_DCT:
            return BaselineJpegCodec.encodeSequential(delta, 0xc5, JpegSampling.SF444, 0);
        case PROGRESSIVE_DCT:
            return ProgressiveJpegCodec.encode(delta, JpegSampling.SF444, 0xc6);
        case LOSSLESS:
            return LosslessJpegCodec.encode(delta, 1, 0, 0, 0xc7);
        default:
            throw new IllegalArgumentException("unsupported differential JPEG process");
        }
    }

    /** Decodes a differential frame and reconstructs it against the reference. */
    public static JpegFrame decode(byte[] data, JpegFrame reference) throws IOException {
        if (data == null || data.length < 6) {
            throw new IllegalArgumentException("differential JPEG data is empty");
        }
        int marker = frameMarker(data);
        JpegFrame delta;
        switch (marker) {
        case 0xc5:
            delta = BaselineJpegCodec.decodeSequential(data, 0xc5);
            break;
        case 0xc6:
            delta = ProgressiveJpegCodec.decode(data, 0xc6);
            break;
        case 0xc7:
            delta = LosslessJpegCodec.decode(data, 0, 0xc7);
            break;
        default:
            throw new IllegalArgumentException("JPEG frame is not differential");
        }
        validateReference(delta, reference);
        return reconstruct(delta, reference);
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

    private static void validateReference(JpegFrame frame, JpegFrame reference) {
        if (frame == null || reference == null) {
            throw new IllegalArgumentException("differential JPEG requires a reference frame");
        }
        if (frame.width() != reference.width() || frame.height() != reference.height()
                || frame.components() != reference.components()
                || frame.precision() != reference.precision()) {
            throw new IllegalArgumentException("differential JPEG reference is incompatible");
        }
    }

    private static JpegFrame difference(JpegFrame frame, JpegFrame reference) {
        int precision = frame.precision();
        int bias = 1 << (precision - 1);
        int maximum = (1 << precision) - 1;
        int[] samples = new int[frame.width() * frame.height() * frame.components()];
        for (int i = 0; i < samples.length; i++) {
            int value = frame.samples()[i] - reference.samples()[i] + bias;
            if (value < 0 || value > maximum) {
                throw new IllegalArgumentException(
                        "differential JPEG sample difference exceeds precision range");
            }
            samples[i] = value;
        }
        return JpegFrame.of(frame.width(), frame.height(), frame.components(), samples, precision);
    }

    private static JpegFrame reconstruct(JpegFrame delta, JpegFrame reference) {
        int bias = 1 << (delta.precision() - 1);
        int maximum = (1 << delta.precision()) - 1;
        int[] encoded = delta.samples();
        int[] referenceSamples = reference.samples();
        for (int i = 0; i < encoded.length; i++) {
            int value = referenceSamples[i] + encoded[i] - bias;
            if (value < 0 || value > maximum) {
                throw new IllegalArgumentException(
                        "differential JPEG reconstructed sample is out of range");
            }
            encoded[i] = value;
        }
        return JpegFrame.of(delta.width(), delta.height(), delta.components(), encoded,
                delta.precision());
    }

    private static int frameMarker(byte[] data) {
        for (int i = 0; i + 1 < data.length; i++) {
            if ((data[i] & 0xff) == 0xff) {
                int marker = data[i + 1] & 0xff;
                if (marker == 0xc5 || marker == 0xc6 || marker == 0xc7) {
                    return marker;
                }
            }
        }
        return -1;
    }
}
