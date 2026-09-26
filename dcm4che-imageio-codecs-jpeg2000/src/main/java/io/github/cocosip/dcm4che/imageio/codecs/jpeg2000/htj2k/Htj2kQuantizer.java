package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import javax.imageio.IIOException;

/** Reversible HT QCD profile and code-block magnitude budget. */
final class Htj2kQuantizer {
    private static final double[] LOW_53 = {
            1.0, 1.5, 1.625, 1.6875, 1.6963, 1.7067
    };
    private static final double[] HIGH_53 = {
            2.0, 2.5, 2.75, 2.8047, 2.8198
    };
    private static final float[] LOW_97 = {
            1.0f, 1.4021f, 2.0304f, 2.9012f, 4.1153f, 5.8245f
    };
    private static final float[] HIGH_97 = {
            1.4425f, 1.9669f, 2.8839f, 4.1475f, 5.8946f
    };

    private Htj2kQuantizer() {
    }

    static byte[] reversiblePayload(int precision, int components, int levels)
            throws IIOException {
        if (precision < 2 || precision > 16 || (components != 1 && components != 3)
                || levels < 0 || levels > 5) {
            throw new IIOException("Invalid HT reversible quantization profile");
        }
        int base = precision + (components == 3 ? 1 : 0);
        int[] exponents = new int[1 + 3 * levels];
        exponents[0] = base + exponent(LOW_53[levels] * LOW_53[levels]);
        for (int d = levels, index = 1; d > 0; d--) {
            int lowHigh = exponent(LOW_53[d] * HIGH_53[d - 1]);
            exponents[index++] = base + lowHigh;
            exponents[index++] = base + lowHigh;
            exponents[index++] = base + exponent(HIGH_53[d - 1] * HIGH_53[d - 1]);
        }
        int maximum = 0;
        for (int value : exponents) {
            maximum = Math.max(maximum, value);
        }
        int guard = Math.max(1, maximum - 31);
        byte[] payload = new byte[1 + exponents.length];
        payload[0] = (byte) (guard << 5);
        for (int i = 0; i < exponents.length; i++) {
            int stepExponent = exponents[i] - guard;
            if (stepExponent < 0 || stepExponent > 31) {
                throw new IIOException("HT QCD exponent is outside 0..31");
            }
            payload[i + 1] = (byte) (stepExponent << 3);
        }
        return payload;
    }

    static int kmax(byte[] qcd, int resolution, int orientation)
            throws IIOException {
        if (qcd == null || qcd.length < 2 || (qcd[0] & 0x1f) != 0
                || (qcd[0] & 0xe0) == 0) {
            throw new IIOException("Invalid HT reversible QCD");
        }
        int index = resolution == 0 ? 0 : 1 + 3 * (resolution - 1) + orientation - 1;
        if (index < 0 || index + 1 >= qcd.length || (resolution > 0
                && (orientation < 1 || orientation > 3))) {
            throw new IIOException("HT QCD has no step for subband");
        }
        int guard = (qcd[0] & 0xff) >>> 5;
        int exponent = (qcd[index + 1] & 0xff) >>> 3;
        int kmax = exponent + guard - 1;
        if (kmax < 2 || kmax > 30) {
            throw new IIOException("HT subband Kmax is outside the 32-bit profile");
        }
        return kmax;
    }

    static int reversibleCcap15(byte[] qcd) throws IIOException {
        int maximum = 0;
        for (int i = 1; i < qcd.length; i++) {
            int resolution = i == 1 ? 0 : 1 + (i - 2) / 3;
            int orientation = i == 1 ? 0 : 1 + (i - 2) % 3;
            maximum = Math.max(maximum, kmax(qcd, resolution, orientation));
        }
        return maximum <= 8 ? 0 : maximum < 28 ? maximum - 8
                : 13 + (maximum >>> 2);
    }

    static byte[] irreversiblePayload(int precision, int levels) throws IIOException {
        return irreversiblePayload(precision, levels, 0);
    }

    static byte[] irreversiblePayload(int precision, int levels, double targetRatio)
            throws IIOException {
        if (precision < 2 || precision > 16 || levels < 0 || levels > 5) {
            throw new IIOException("Invalid HT irreversible quantization profile");
        }
        if (!Double.isFinite(targetRatio)
                || (targetRatio != 0 && targetRatio <= 1)) {
            throw new IIOException("HT target ratio must be zero or greater than one");
        }
        byte[] payload = new byte[1 + 2 * (1 + 3 * levels)];
        payload[0] = 0x22;
        float baseDelta = Math.scalb(1.0f, -precision);
        if (targetRatio > 1) {
            baseDelta *= (100 - qualityHint(targetRatio)) / 4.0f;
        }
        int index = 1;
        index = writeStep(payload, index, baseDelta / (LOW_97[levels] * LOW_97[levels]));
        for (int d = levels; d > 0; d--) {
            float lowHigh = baseDelta / (LOW_97[d] * HIGH_97[d - 1]);
            index = writeStep(payload, index, lowHigh);
            index = writeStep(payload, index, lowHigh);
            index = writeStep(payload, index,
                    baseDelta / (HIGH_97[d - 1] * HIGH_97[d - 1]));
        }
        return payload;
    }

    static int qualityHint(double targetRatio) throws IIOException {
        if (!Double.isFinite(targetRatio) || targetRatio <= 1) {
            throw new IIOException("HT quality hint requires a target ratio greater than one");
        }
        double tolerance = Math.max(1, Math.ceil(targetRatio - 1));
        return (int) Math.max(30, Math.min(95, 96 - 4 * tolerance));
    }

    static int irreversibleKmax(byte[] qcd, int resolution, int orientation)
            throws IIOException {
        int step = irreversibleStepValue(qcd, resolution, orientation);
        int kmax = (step >>> 11) + ((qcd[0] & 0xff) >>> 5) - 1;
        if (kmax < 2 || kmax > 30) {
            throw new IIOException("HT irreversible Kmax is outside the 32-bit profile");
        }
        return kmax;
    }

    static double irreversibleStep(byte[] qcd, int resolution, int orientation,
            int precision) throws IIOException {
        if (precision < 2 || precision > 16) {
            throw new IIOException("HT irreversible precision is invalid");
        }
        int value = irreversibleStepValue(qcd, resolution, orientation);
        int exponent = value >>> 11;
        int mantissa = value & 0x7ff;
        // The shared 9/7 DWT stores high-pass coefficients at half the
        // OpenJPH amplitude per high-pass dimension.
        return (1.0 + mantissa / 2048.0)
                * Math.scalb(1.0, precision - exponent);
    }

    static int irreversibleCcap15(byte[] qcd) throws IIOException {
        if (qcd == null || qcd.length < 3 || (qcd.length - 1) % 2 != 0) {
            throw new IIOException("Invalid HT irreversible QCD");
        }
        int maximum = 0;
        int subbands = (qcd.length - 1) / 2;
        int levels = (subbands - 1) / 3;
        for (int i = 0; i < subbands; i++) {
            int resolution = i == 0 ? 0 : 1 + (i - 1) / 3;
            int orientation = i == 0 ? 0 : 1 + (i - 1) % 3;
            int decomposition = levels - (i == 0 ? 0 : (i - 1) / 3);
            maximum = Math.max(maximum,
                    irreversibleKmax(qcd, resolution, orientation) + 1 - decomposition);
        }
        int magnitude = maximum <= 8 ? 0 : maximum < 28 ? maximum - 8
                : 13 + (maximum >>> 2);
        return 0x20 | magnitude;
    }

    private static int writeStep(byte[] payload, int index, float delta)
            throws IIOException {
        int exponent = 0;
        while (delta < 1.0f && exponent <= 31) {
            exponent++;
            delta *= 2.0f;
        }
        if (exponent > 31 || !Float.isFinite(delta)) {
            throw new IIOException("HT irreversible quantization step is invalid");
        }
        int mantissa = Math.round(delta * 2048.0f) - 2048;
        mantissa = Math.min(mantissa, 0x7ff);
        int step = (exponent << 11) | mantissa;
        payload[index++] = (byte) (step >>> 8);
        payload[index++] = (byte) step;
        return index;
    }

    private static int irreversibleStepValue(byte[] qcd, int resolution,
            int orientation) throws IIOException {
        if (qcd == null || qcd.length < 3 || (qcd[0] & 0x1f) != 2
                || (qcd.length - 1) % 2 != 0) {
            throw new IIOException("Invalid HT irreversible QCD");
        }
        int index = resolution == 0 ? 0 : 1 + 3 * (resolution - 1) + orientation - 1;
        if (index < 0 || 2 + 2 * index >= qcd.length || (resolution > 0
                && (orientation < 1 || orientation > 3))) {
            throw new IIOException("HT QCD has no irreversible step for subband");
        }
        int offset = 1 + 2 * index;
        return ((qcd[offset] & 0xff) << 8) | (qcd[offset + 1] & 0xff);
    }

    private static int exponent(double gain) {
        return (int) Math.ceil(Math.log(gain) / Math.log(2));
    }
}
