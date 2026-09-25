package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

/** Irreversible Part 1 9/7 lifting transform with symmetric boundary extension. */
public final class Jpeg2000Dwt97 {
    private static final double ALPHA = -1.586134342059924;
    private static final double BETA = -0.052980118572961;
    private static final double GAMMA = 0.882911075530934;
    private static final double DELTA = 0.443506852043971;
    private static final double SCALE = 1.230174104914001;

    private Jpeg2000Dwt97() {
    }

    public static double[] forward1D(double[] samples, long origin, Jpeg2000Limits limits)
            throws Jpeg2000Exception {
        if (samples == null) {
            throw new NullPointerException("samples");
        }
        double[] data = copy(samples, samples.length, 1, 0, limits);
        forwardLine(data, (origin & 1) == 0);
        return data;
    }

    public static double[] inverse1D(double[] coefficients, long origin, Jpeg2000Limits limits)
            throws Jpeg2000Exception {
        if (coefficients == null) {
            throw new NullPointerException("coefficients");
        }
        double[] data = copy(coefficients, coefficients.length, 1, 0, limits);
        inverseLine(data, (origin & 1) == 0);
        return data;
    }

    public static double[] forward(double[] samples, int width, int height, int levels,
            long x0, long y0, Jpeg2000Limits limits) throws Jpeg2000Exception {
        double[] data = copy(samples, width, height, levels, limits);
        int currentWidth = width;
        int currentHeight = height;
        for (int level = 0; level < levels; level++) {
            boolean evenX = (x0 & 1) == 0;
            boolean evenY = (y0 & 1) == 0;
            for (int x = 0; x < currentWidth; x++) {
                double[] column = new double[currentHeight];
                for (int y = 0; y < currentHeight; y++) {
                    column[y] = data[y * width + x];
                }
                forwardLine(column, evenY);
                for (int y = 0; y < currentHeight; y++) {
                    data[y * width + x] = column[y];
                }
            }
            for (int y = 0; y < currentHeight; y++) {
                double[] row = new double[currentWidth];
                System.arraycopy(data, y * width, row, 0, currentWidth);
                forwardLine(row, evenX);
                System.arraycopy(row, 0, data, y * width, currentWidth);
            }
            currentWidth = lowCount(currentWidth, evenX);
            currentHeight = lowCount(currentHeight, evenY);
            x0 = (x0 + 1) >> 1;
            y0 = (y0 + 1) >> 1;
        }
        return data;
    }

    public static double[] inverse(double[] coefficients, int width, int height, int levels,
            long x0, long y0, Jpeg2000Limits limits) throws Jpeg2000Exception {
        double[] data = copy(coefficients, width, height, levels, limits);
        int[] widths = new int[levels + 1];
        int[] heights = new int[levels + 1];
        long[] originsX = new long[levels + 1];
        long[] originsY = new long[levels + 1];
        widths[0] = width;
        heights[0] = height;
        originsX[0] = x0;
        originsY[0] = y0;
        for (int level = 1; level <= levels; level++) {
            widths[level] = lowCount(widths[level - 1], (originsX[level - 1] & 1) == 0);
            heights[level] = lowCount(heights[level - 1], (originsY[level - 1] & 1) == 0);
            originsX[level] = (originsX[level - 1] + 1) >> 1;
            originsY[level] = (originsY[level - 1] + 1) >> 1;
        }
        for (int level = levels - 1; level >= 0; level--) {
            int currentWidth = widths[level];
            int currentHeight = heights[level];
            boolean evenX = (originsX[level] & 1) == 0;
            boolean evenY = (originsY[level] & 1) == 0;
            for (int y = 0; y < currentHeight; y++) {
                double[] row = new double[currentWidth];
                System.arraycopy(data, y * width, row, 0, currentWidth);
                inverseLine(row, evenX);
                System.arraycopy(row, 0, data, y * width, currentWidth);
            }
            for (int x = 0; x < currentWidth; x++) {
                double[] column = new double[currentHeight];
                for (int y = 0; y < currentHeight; y++) {
                    column[y] = data[y * width + x];
                }
                inverseLine(column, evenY);
                for (int y = 0; y < currentHeight; y++) {
                    data[y * width + x] = column[y];
                }
            }
        }
        return data;
    }

    private static double[] copy(double[] values, int width, int height, int levels,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        if (values == null || limits == null) {
            throw new NullPointerException(values == null ? "values" : "limits");
        }
        if (levels < 0 || levels > 32 || width <= 0 || height <= 0) {
            throw new Jpeg2000Exception("JPEG 2000 9/7 transform geometry is invalid");
        }
        if (limits.checkedSampleBufferBytes(width, height, 1, Double.BYTES) / Double.BYTES
                != values.length) {
            throw new Jpeg2000Exception("JPEG 2000 9/7 transform dimensions do not match samples");
        }
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new Jpeg2000Exception("JPEG 2000 9/7 transform input is not finite");
            }
        }
        return values.clone();
    }

    private static void forwardLine(double[] data, boolean evenOrigin) {
        int length = data.length;
        int lowParity = evenOrigin ? 0 : 1;
        int highParity = 1 - lowParity;
        lift(data, highParity, ALPHA);
        lift(data, lowParity, BETA);
        lift(data, highParity, GAMMA);
        lift(data, lowParity, DELTA);
        for (int i = 0; i < length; i++) {
            data[i] *= (i & 1) == lowParity ? 1.0 / SCALE : SCALE / 2.0;
        }
        double[] packed = new double[length];
        int low = 0;
        int high = lowCount(length, evenOrigin);
        for (int i = 0; i < length; i++) {
            packed[(i & 1) == lowParity ? low++ : high++] = data[i];
        }
        System.arraycopy(packed, 0, data, 0, length);
    }

    private static void inverseLine(double[] data, boolean evenOrigin) {
        int length = data.length;
        int lowParity = evenOrigin ? 0 : 1;
        int highParity = 1 - lowParity;
        double[] unpacked = new double[length];
        int low = 0;
        int high = lowCount(length, evenOrigin);
        for (int i = 0; i < length; i++) {
            unpacked[i] = data[(i & 1) == lowParity ? low++ : high++];
            unpacked[i] *= (i & 1) == lowParity ? SCALE : 2.0 / SCALE;
        }
        lift(unpacked, lowParity, -DELTA);
        lift(unpacked, highParity, -GAMMA);
        lift(unpacked, lowParity, -BETA);
        lift(unpacked, highParity, -ALPHA);
        System.arraycopy(unpacked, 0, data, 0, length);
    }

    private static void lift(double[] data, int parity, double coefficient) {
        if (data.length == 1) {
            return;
        }
        for (int i = parity; i < data.length; i += 2) {
            int left = i == 0 ? Math.min(1, data.length - 1) : i - 1;
            int right = i == data.length - 1 ? Math.max(0, i - 1) : i + 1;
            data[i] += coefficient * (data[left] + data[right]);
        }
    }

    private static int lowCount(int length, boolean evenOrigin) {
        return evenOrigin ? (length + 1) >> 1 : length >> 1;
    }
}
