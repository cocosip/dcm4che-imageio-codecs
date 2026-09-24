package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

public final class Jpeg2000Dwt53 {
    private Jpeg2000Dwt53() {
    }

    public static int[] forward1D(int[] samples, long origin, Jpeg2000Limits limits)
            throws Jpeg2000Exception {
        int[] data = copySignal(samples, limits);
        forwardLine(data, (origin & 1) == 0);
        return data;
    }

    public static int[] inverse1D(int[] coefficients, long origin, Jpeg2000Limits limits)
            throws Jpeg2000Exception {
        int[] data = copySignal(coefficients, limits);
        inverseLine(data, (origin & 1) == 0);
        return data;
    }

    public static int[] forward(
            int[] samples,
            int width,
            int height,
            int levels,
            long x0,
            long y0,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        int[] data = copyImage(samples, width, height, levels, limits);
        int currentWidth = width;
        int currentHeight = height;
        long currentX0 = x0;
        long currentY0 = y0;
        for (int level = 0; level < levels; level++) {
            forward2D(
                    data, currentWidth, currentHeight, width,
                    (currentX0 & 1) == 0, (currentY0 & 1) == 0);
            currentWidth = splitLength(currentWidth, (currentX0 & 1) == 0);
            currentHeight = splitLength(currentHeight, (currentY0 & 1) == 0);
            currentX0 = (currentX0 + 1) >> 1;
            currentY0 = (currentY0 + 1) >> 1;
        }
        return data;
    }

    public static int[] inverse(
            int[] coefficients,
            int width,
            int height,
            int levels,
            long x0,
            long y0,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        int[] data = copyImage(coefficients, width, height, levels, limits);
        int[] widths = new int[levels + 1];
        int[] heights = new int[levels + 1];
        long[] originsX = new long[levels + 1];
        long[] originsY = new long[levels + 1];
        widths[0] = width;
        heights[0] = height;
        originsX[0] = x0;
        originsY[0] = y0;
        for (int level = 1; level <= levels; level++) {
            widths[level] = splitLength(widths[level - 1], (originsX[level - 1] & 1) == 0);
            heights[level] = splitLength(heights[level - 1], (originsY[level - 1] & 1) == 0);
            originsX[level] = (originsX[level - 1] + 1) >> 1;
            originsY[level] = (originsY[level - 1] + 1) >> 1;
        }
        for (int level = levels - 1; level >= 0; level--) {
            inverse2D(
                    data, widths[level], heights[level], width,
                    (originsX[level] & 1) == 0, (originsY[level] & 1) == 0);
        }
        return data;
    }

    private static int[] copySignal(int[] samples, Jpeg2000Limits limits)
            throws Jpeg2000Exception {
        if (samples == null) {
            throw new NullPointerException("samples");
        }
        if (limits == null) {
            throw new NullPointerException("limits");
        }
        limits.checkedSampleBufferBytes(samples.length, 1, 1, Integer.BYTES);
        return samples.clone();
    }

    private static int[] copyImage(
            int[] samples,
            int width,
            int height,
            int levels,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        if (samples == null) {
            throw new NullPointerException("samples");
        }
        if (limits == null) {
            throw new NullPointerException("limits");
        }
        if (levels < 0 || levels > 32) {
            throw new Jpeg2000Exception("JPEG 2000 decomposition level count is outside 0..32");
        }
        int bytes = limits.checkedSampleBufferBytes(width, height, 1, Integer.BYTES);
        if (bytes / Integer.BYTES != samples.length) {
            throw new Jpeg2000Exception("JPEG 2000 reversible transform dimensions do not match samples");
        }
        return samples.clone();
    }

    private static void forward2D(
            int[] data,
            int width,
            int height,
            int stride,
            boolean evenX,
            boolean evenY) {
        if (height > 0) {
            int[] column = new int[height];
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    column[y] = data[y * stride + x];
                }
                forwardLine(column, evenY);
                for (int y = 0; y < height; y++) {
                    data[y * stride + x] = column[y];
                }
            }
        }
        if (width > 0) {
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                System.arraycopy(data, y * stride, row, 0, width);
                forwardLine(row, evenX);
                System.arraycopy(row, 0, data, y * stride, width);
            }
        }
    }

    private static void inverse2D(
            int[] data,
            int width,
            int height,
            int stride,
            boolean evenX,
            boolean evenY) {
        if (width > 0) {
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                System.arraycopy(data, y * stride, row, 0, width);
                inverseLine(row, evenX);
                System.arraycopy(row, 0, data, y * stride, width);
            }
        }
        if (height > 0) {
            int[] column = new int[height];
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    column[y] = data[y * stride + x];
                }
                inverseLine(column, evenY);
                for (int y = 0; y < height; y++) {
                    data[y * stride + x] = column[y];
                }
            }
        }
    }

    private static void forwardLine(int[] data, boolean even) {
        int length = data.length;
        if (even) {
            if (length <= 1) {
                return;
            }
            int lowCount = (length + 1) >> 1;
            int highCount = length - lowCount;
            int[] output = new int[length];
            int index = 0;
            for (; index < lowCount - 1; index++) {
                output[lowCount + index] = data[2 * index + 1]
                        - ((data[2 * index] + data[2 * (index + 1)]) >> 1);
            }
            if ((length & 1) == 0) {
                output[lowCount + index] = data[2 * index + 1] - data[2 * index];
            }
            output[0] = data[0] + ((output[lowCount] + output[lowCount] + 2) >> 2);
            for (index = 1; index < highCount; index++) {
                output[index] = data[2 * index]
                        + ((output[lowCount + index - 1] + output[lowCount + index] + 2) >> 2);
            }
            if ((length & 1) != 0) {
                output[index] = data[2 * index]
                        + ((output[lowCount + index - 1] + output[lowCount + index - 1] + 2) >> 2);
            }
            System.arraycopy(output, 0, data, 0, length);
            return;
        }
        if (length == 1) {
            data[0] *= 2;
            return;
        }
        int lowCount = length >> 1;
        int highCount = length - lowCount;
        int[] output = new int[length];
        output[lowCount] = data[0] - data[1];
        int index = 1;
        for (; index < lowCount; index++) {
            output[lowCount + index] = data[2 * index]
                    - ((data[2 * index + 1] + data[2 * (index - 1) + 1]) >> 1);
        }
        if ((length & 1) != 0) {
            output[lowCount + index] = data[2 * index] - data[2 * (index - 1) + 1];
        }
        for (index = 0; index < highCount - 1; index++) {
            output[index] = data[2 * index + 1]
                    + ((output[lowCount + index] + output[lowCount + index + 1] + 2) >> 2);
        }
        if ((length & 1) == 0) {
            output[index] = data[2 * index + 1]
                    + ((output[lowCount + index] + output[lowCount + index] + 2) >> 2);
        }
        System.arraycopy(output, 0, data, 0, length);
    }

    private static void inverseLine(int[] data, boolean even) {
        int length = data.length;
        if (even) {
            if (length <= 1) {
                return;
            }
            int lowCount = (length + 1) >> 1;
            int[] output = new int[length];
            int nextLow = data[0];
            int nextHigh = data[lowCount];
            int currentLow = nextLow - ((nextHigh + 1) >> 1);
            int outputIndex = 0;
            int lowIndex = 1;
            for (; outputIndex < length - 3; outputIndex += 2, lowIndex++) {
                int currentHigh = nextHigh;
                int previousLow = currentLow;
                nextLow = data[lowIndex];
                nextHigh = data[lowCount + lowIndex];
                currentLow = nextLow - ((currentHigh + nextHigh + 2) >> 2);
                output[outputIndex] = previousLow;
                output[outputIndex + 1] = currentHigh + ((previousLow + currentLow) >> 1);
            }
            output[outputIndex] = currentLow;
            if ((length & 1) != 0) {
                output[length - 1] = data[(length - 1) >> 1] - ((nextHigh + 1) >> 1);
                output[length - 2] = nextHigh + ((currentLow + output[length - 1]) >> 1);
            } else {
                output[length - 1] = nextHigh + currentLow;
            }
            System.arraycopy(output, 0, data, 0, length);
            return;
        }
        if (length == 1) {
            data[0] /= 2;
            return;
        }
        if (length == 2) {
            int second = data[0] - ((data[1] + 1) >> 1);
            int first = data[1] + second;
            data[0] = first;
            data[1] = second;
            return;
        }
        int lowCount = length >> 1;
        int[] output = new int[length];
        int high = data[lowCount + 1];
        int difference = data[0] - ((data[lowCount] + high + 2) >> 2);
        output[0] = data[lowCount] + difference;
        int limit = length - 2 - ((length & 1) == 0 ? 1 : 0);
        int outputIndex = 1;
        int lowIndex = 1;
        for (; outputIndex < limit; outputIndex += 2, lowIndex++) {
            int nextHigh = data[lowCount + lowIndex + 1];
            int nextDifference = data[lowIndex] - ((high + nextHigh + 2) >> 2);
            output[outputIndex] = difference;
            output[outputIndex + 1] = high + ((nextDifference + difference) >> 1);
            difference = nextDifference;
            high = nextHigh;
        }
        output[outputIndex] = difference;
        if ((length & 1) == 0) {
            int nextDifference = data[(length >> 1) - 1] - ((high + 1) >> 1);
            output[length - 2] = high + ((nextDifference + difference) >> 1);
            output[length - 1] = nextDifference;
        } else {
            output[length - 1] = high + difference;
        }
        System.arraycopy(output, 0, data, 0, length);
    }

    private static int splitLength(int length, boolean even) {
        return even ? (length + 1) >> 1 : length >> 1;
    }
}
