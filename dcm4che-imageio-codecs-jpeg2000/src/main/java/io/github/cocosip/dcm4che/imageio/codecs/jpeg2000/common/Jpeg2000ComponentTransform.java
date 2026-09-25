package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

public final class Jpeg2000ComponentTransform {
    private Jpeg2000ComponentTransform() {
    }

    public static double[][] forwardIrreversible(int[] red, int[] green, int[] blue,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        int length = validate(red, green, blue, limits);
        double[][] result = new double[][] {new double[length], new double[length], new double[length]};
        for (int i = 0; i < length; i++) {
            result[0][i] = 0.299 * red[i] + 0.587 * green[i] + 0.114 * blue[i];
            result[1][i] = -0.16875 * red[i] - 0.33126 * green[i] + 0.5 * blue[i];
            result[2][i] = 0.5 * red[i] - 0.41869 * green[i] - 0.08131 * blue[i];
        }
        return result;
    }

    public static int[][] inverseIrreversible(double[] luminance, double[] blueDifference,
            double[] redDifference, Jpeg2000Limits limits) throws Jpeg2000Exception {
        if (luminance == null || blueDifference == null || redDifference == null) {
            throw new NullPointerException("components");
        }
        if (luminance.length != blueDifference.length || luminance.length != redDifference.length) {
            throw new Jpeg2000Exception("JPEG 2000 ICT component lengths differ");
        }
        limits.checkedSampleBufferBytes(luminance.length, 1, 3, Double.BYTES);
        int length = luminance.length;
        int[][] result = new int[][] {new int[length], new int[length], new int[length]};
        for (int i = 0; i < length; i++) {
            result[0][i] = rounded(luminance[i] + 1.402 * redDifference[i]);
            result[1][i] = rounded(luminance[i] - 0.34413 * blueDifference[i]
                    - 0.71414 * redDifference[i]);
            result[2][i] = rounded(luminance[i] + 1.772 * blueDifference[i]);
        }
        return result;
    }

    private static int rounded(double value) throws Jpeg2000Exception {
        if (!Double.isFinite(value) || value <= Integer.MIN_VALUE || value >= Integer.MAX_VALUE) {
            throw new Jpeg2000Exception("JPEG 2000 ICT coefficient exceeds Java range");
        }
        return (int) Math.round(value);
    }

    public static int[][] forwardReversible(
            int[] red,
            int[] green,
            int[] blue,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        int length = validate(red, green, blue, limits);
        int[][] result = new int[][] {new int[length], new int[length], new int[length]};
        for (int index = 0; index < length; index++) {
            long luminance = Math.floorDiv(
                    (long) red[index] + 2L * green[index] + blue[index], 4L);
            result[0][index] = checkedInt(luminance, "RCT luminance");
            result[1][index] = checkedInt((long) blue[index] - green[index], "RCT blue difference");
            result[2][index] = checkedInt((long) red[index] - green[index], "RCT red difference");
        }
        return result;
    }

    public static int[][] inverseReversible(
            int[] luminance,
            int[] blueDifference,
            int[] redDifference,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        int length = validate(luminance, blueDifference, redDifference, limits);
        int[][] result = new int[][] {new int[length], new int[length], new int[length]};
        for (int index = 0; index < length; index++) {
            long green = (long) luminance[index]
                    - Math.floorDiv((long) blueDifference[index] + redDifference[index], 4L);
            result[0][index] = checkedInt((long) redDifference[index] + green, "inverse RCT red");
            result[1][index] = checkedInt(green, "inverse RCT green");
            result[2][index] = checkedInt((long) blueDifference[index] + green, "inverse RCT blue");
        }
        return result;
    }

    private static int validate(
            int[] first,
            int[] second,
            int[] third,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        if (first == null || second == null || third == null) {
            throw new NullPointerException("components");
        }
        if (limits == null) {
            throw new NullPointerException("limits");
        }
        if (first.length != second.length || first.length != third.length) {
            throw new Jpeg2000Exception("JPEG 2000 component transform lengths differ");
        }
        limits.checkedSampleBufferBytes(first.length, 1, 3, Integer.BYTES);
        return first.length;
    }

    private static int checkedInt(long value, String context) throws Jpeg2000Exception {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new Jpeg2000Exception("JPEG 2000 " + context + " exceeds the coefficient range");
        }
        return (int) value;
    }
}
