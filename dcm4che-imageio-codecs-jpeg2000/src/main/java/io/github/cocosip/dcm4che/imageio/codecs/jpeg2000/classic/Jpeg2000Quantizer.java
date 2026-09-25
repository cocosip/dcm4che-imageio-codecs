package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000QuantizationSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000QuantizationStyle;

/** Part 1 scalar quantization step encoding and coefficient conversion. */
public final class Jpeg2000Quantizer {
    private Jpeg2000Quantizer() {
    }

    public static byte[] expoundedPayload(int precision, int levels, int guardBits,
            int stepPower) throws Jpeg2000Exception {
        if (precision < 1 || precision > 17 || levels < 0 || levels > 32
                || guardBits < 0 || guardBits > 7 || stepPower < 0 || stepPower > 15) {
            throw new Jpeg2000Exception("JPEG 2000 quantization profile is invalid");
        }
        byte[] payload = new byte[1 + 2 * (1 + 3 * levels)];
        payload[0] = (byte) ((guardBits << 5) | 2);
        for (int index = 0; index < 1 + 3 * levels; index++) {
            int exponent = precision - stepPower;
            if (exponent < 0 || exponent > 31) {
                throw new Jpeg2000Exception("JPEG 2000 quantization exponent is outside 0..31");
            }
            int value = exponent << 11;
            payload[1 + index * 2] = (byte) (value >>> 8);
            payload[2 + index * 2] = (byte) value;
        }
        return payload;
    }

    public static double step(Jpeg2000QuantizationSegment quantization,
            int index, int precision) throws Jpeg2000Exception {
        if (quantization == null) {
            throw new NullPointerException("quantization");
        }
        int[] values = quantization.stepSizes();
        if (index < 0 || index >= values.length || precision < 1 || precision > 17) {
            throw new Jpeg2000Exception("JPEG 2000 quantization subband or precision is invalid");
        }
        if (quantization.style() == Jpeg2000QuantizationStyle.NO_QUANTIZATION) {
            return 1.0;
        }
        int exponent = values[index] >>> 11;
        int mantissa = values[index] & 0x7ff;
        return (1.0 + mantissa / 2048.0)
                * Math.scalb(1.0, precision - exponent);
    }

    public static int quantize(double coefficient, double step) throws Jpeg2000Exception {
        if (!Double.isFinite(coefficient) || !Double.isFinite(step) || step <= 0) {
            throw new Jpeg2000Exception("JPEG 2000 quantization input is invalid");
        }
        double magnitude = Math.floor(Math.abs(coefficient) / step);
        if (magnitude >= Integer.MAX_VALUE) {
            throw new Jpeg2000Exception("JPEG 2000 quantized coefficient exceeds Java range");
        }
        return (int) Math.copySign(magnitude, coefficient);
    }

    public static double dequantize(int coefficient, double step) throws Jpeg2000Exception {
        if (!Double.isFinite(step) || step <= 0 || coefficient == Integer.MIN_VALUE) {
            throw new Jpeg2000Exception("JPEG 2000 dequantization input is invalid");
        }
        return coefficient * step;
    }

}
