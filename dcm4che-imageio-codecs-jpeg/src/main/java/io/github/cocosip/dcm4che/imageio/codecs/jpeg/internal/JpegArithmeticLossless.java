package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Annex F arithmetic coding for JPEG lossless predictive samples. */
final class JpegArithmeticLossless {
    private JpegArithmeticLossless() {
    }

    static Encoded encode(JpegFrame frame, int predictor, int restartInterval)
            throws IOException {
        return encode(frame, predictor, restartInterval, 0, 1);
    }

    static Encoded encode(JpegFrame frame, int predictor, int restartInterval,
            int dcL, int dcU) throws IOException {
        validate(frame, predictor, restartInterval);
        validateConditioning(dcL, dcU);
        List<byte[]> segments = new ArrayList<byte[]>();
        List<Integer> restartMarkers = new ArrayList<Integer>();
        int total = frame.width() * frame.height();
        int start = 0;
        int restart = 0;
        while (start < total) {
            int end = restartInterval == 0 ? total : Math.min(total, start + restartInterval);
            JpegQmCoder.Encoder coder = new JpegQmCoder.Encoder();
            int[] stats = new int[64];
            int[] contexts = new int[frame.components()];
            for (int mcu = start; mcu < end; mcu++) {
                int y = mcu / frame.width();
                int x = mcu % frame.width();
                for (int component = 0; component < frame.components(); component++) {
                    int prediction = predict(frame, x, y, component, predictor, start);
                    encodeDifference(coder, stats, contexts, component,
                            frame.sample(x, y, component) - prediction, dcL, dcU);
                }
            }
            segments.add(coder.finish());
            start = end;
            if (start < total) {
                restartMarkers.add(0xd0 + restart);
                restart = (restart + 1) & 7;
            }
        }
        return new Encoded(segments, restartMarkers);
    }

    static JpegFrame decode(int width, int height, int components, int precision,
            int predictor, int restartInterval, List<byte[]> segments,
            List<Integer> restartMarkers) throws IOException {
        return decode(width, height, components, precision, predictor, restartInterval,
                segments, restartMarkers, 0, 1);
    }

    static JpegFrame decode(int width, int height, int components, int precision,
            int predictor, int restartInterval, List<byte[]> segments,
            List<Integer> restartMarkers, int dcL, int dcU) throws IOException {
        if (width <= 0 || height <= 0 || (components != 1 && components != 3)
                || precision < 8 || precision > 16 || predictor < 1 || predictor > 7
                || restartInterval < 0 || restartInterval > 0xffff
                || segments == null || segments.isEmpty()) {
            throw new JpegException("invalid arithmetic JPEG lossless frame");
        }
        int total = width * height;
        validateConditioning(dcL, dcU);
        int[] samples = new int[total * components];
        int mcu = 0;
        int restart = 0;
        for (int segmentIndex = 0; segmentIndex < segments.size() && mcu < total; segmentIndex++) {
            int end = restartInterval == 0 ? total : Math.min(total, mcu + restartInterval);
            JpegQmCoder.Decoder coder = new JpegQmCoder.Decoder(segments.get(segmentIndex));
            int[] stats = new int[64];
            int[] contexts = new int[components];
            while (mcu < end) {
                int y = mcu / width;
                int x = mcu % width;
                for (int component = 0; component < components; component++) {
                    int prediction = predict(samples, width, components, x, y, component,
                            precision, predictor, mcu - (mcu % (restartInterval == 0 ? total : restartInterval)));
                    int difference = decodeDifference(coder, stats, contexts, component, dcL, dcU);
                    int sample = (prediction + difference) & ((1 << precision) - 1);
                    samples[(mcu * components) + component] = sample;
                }
                mcu++;
            }
            if (restartInterval != 0 && mcu < total) {
                if (restartMarkers == null || segmentIndex >= restartMarkers.size()
                        || restartMarkers.get(segmentIndex) != 0xd0 + restart) {
                    throw new JpegException("arithmetic JPEG lossless restart marker sequence is invalid");
                }
                restart = (restart + 1) & 7;
            }
        }
        if (mcu != total || (restartInterval == 0 && restartMarkers != null
                && !restartMarkers.isEmpty())) {
            throw new JpegException("arithmetic JPEG lossless scan does not match restart interval"
                    + " (mcu=" + mcu + ", total=" + total + ", segments=" + segments.size()
                    + ", restarts=" + (restartMarkers == null ? 0 : restartMarkers.size()) + ")");
        }
        return JpegFrame.of(width, height, components, samples, precision);
    }

    private static void encodeDifference(JpegQmCoder.Encoder coder, int[] stats,
            int[] contexts, int component, int difference, int dcL, int dcU) {
        int state = contexts[component];
        if (difference == 0) {
            coder.encode(stats, state, 0);
            contexts[component] = 0;
            return;
        }
        coder.encode(stats, state, 1);
        int sign = difference < 0 ? 1 : 0;
        int magnitude = Math.abs(difference);
        coder.encode(stats, state + 1, sign);
        int value = magnitude - 1;
        int m = 0;
        int categoryContext = state + 2 + sign;
        if (value != 0) {
            coder.encode(stats, categoryContext, 1);
            m = 1;
            int probe = value;
            int context = 20;
            while ((probe >>>= 1) != 0) {
                coder.encode(stats, context++, 1);
                m <<= 1;
            }
            categoryContext = context;
        }
        coder.encode(stats, categoryContext, 0);
        if (m < (1 << dcL >> 1)) {
            contexts[component] = 0;
        } else if (m > (1 << dcU >> 1)) {
            contexts[component] = 12 + sign * 4;
        } else {
            contexts[component] = 4 + sign * 4;
        }
        int context = categoryContext + 14;
        while ((m >>>= 1) != 0) {
            coder.encode(stats, context++, (m & value) != 0 ? 1 : 0);
        }
    }

    private static int decodeDifference(JpegQmCoder.Decoder coder, int[] stats,
            int[] contexts, int component, int dcL, int dcU) throws IOException {
        int state = contexts[component];
        if (coder.decode(stats, state) == 0) {
            contexts[component] = 0;
            return 0;
        }
        int sign = coder.decode(stats, state + 1);
        int categoryContext = state + 2 + sign;
        int m = coder.decode(stats, categoryContext);
        if (m != 0) {
            int context = 20;
            while (coder.decode(stats, context) != 0) {
                if ((m <<= 1) == 0x8000) {
                    throw new JpegException("arithmetic JPEG lossless magnitude overflow");
                }
                context++;
            }
            categoryContext = context;
        }
        if (m < (1 << dcL >> 1)) {
            contexts[component] = 0;
        } else if (m > (1 << dcU >> 1)) {
            contexts[component] = 12 + sign * 4;
        } else {
            contexts[component] = 4 + sign * 4;
        }
        int value = m;
        int context = categoryContext + 14;
        while ((m >>>= 1) != 0) {
            if (coder.decode(stats, context++) != 0) {
                value |= m;
            }
        }
        value++;
        return sign == 0 ? value : -value;
    }

    private static int predict(JpegFrame frame, int x, int y, int component,
            int predictor, int restartStart) {
        int mcu = y * frame.width() + x;
        boolean left = x > 0 && mcu - 1 >= restartStart;
        boolean above = y > 0 && mcu - frame.width() >= restartStart;
        if (!left && !above) {
            return 1 << (frame.precision() - 1);
        }
        int a = left ? frame.sample(x - 1, y, component) : 0;
        int b = above ? frame.sample(x, y - 1, component) : 0;
        if (!above) {
            return a;
        }
        if (!left) {
            return b;
        }
        int c = frame.sample(x - 1, y - 1, component);
        return prediction(a, b, c, predictor);
    }

    private static int predict(int[] samples, int width, int components, int x, int y,
            int component, int precision, int predictor, int restartStart) {
        int mcu = y * width + x;
        boolean left = x > 0 && mcu - 1 >= restartStart;
        boolean above = y > 0 && mcu - width >= restartStart;
        if (!left && !above) {
            return 1 << (precision - 1);
        }
        int a = left ? samples[((y * width + x - 1) * components) + component] : 0;
        int b = above ? samples[(((y - 1) * width + x) * components) + component] : 0;
        if (!above) {
            return a;
        }
        if (!left) {
            return b;
        }
        int c = samples[(((y - 1) * width + x - 1) * components) + component];
        return prediction(a, b, c, predictor);
    }

    private static int prediction(int a, int b, int c, int predictor) {
        switch (predictor) {
            case 1: return a;
            case 2: return b;
            case 3: return c;
            case 4: return a + b - c;
            case 5: return a + ((b - c) >> 1);
            case 6: return b + ((a - c) >> 1);
            case 7: return (a + b) >> 1;
            default: throw new IllegalArgumentException("invalid JPEG lossless predictor");
        }
    }

    private static void validate(JpegFrame frame, int predictor, int restartInterval) {
        if (frame == null || (frame.components() != 1 && frame.components() != 3)
                || frame.precision() < 8 || frame.precision() > 16
                || predictor < 1 || predictor > 7 || restartInterval < 0
                || restartInterval > 0xffff) {
            throw new IllegalArgumentException("invalid arithmetic JPEG lossless parameters");
        }
    }

    private static void validateConditioning(int dcL, int dcU) {
        if (dcL < 0 || dcL > dcU || dcU > 15) {
            throw new IllegalArgumentException("invalid arithmetic JPEG DC conditioning");
        }
    }

    static final class Encoded {
        final List<byte[]> segments;
        final List<Integer> restartMarkers;

        Encoded(List<byte[]> segments, List<Integer> restartMarkers) {
            this.segments = segments;
            this.restartMarkers = restartMarkers;
        }
    }
}
