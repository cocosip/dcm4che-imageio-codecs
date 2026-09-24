package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

public final class Jpeg2000ComponentTransform {
    private Jpeg2000ComponentTransform() {
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
