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

    private static int exponent(double gain) {
        return (int) Math.ceil(Math.log(gain) / Math.log(2));
    }
}
