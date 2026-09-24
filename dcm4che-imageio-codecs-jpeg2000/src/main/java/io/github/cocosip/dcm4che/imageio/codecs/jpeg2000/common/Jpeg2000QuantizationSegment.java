package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import java.util.Arrays;

public final class Jpeg2000QuantizationSegment {
    private final Jpeg2000QuantizationStyle style;
    private final int guardBits;
    private final int[] stepSizes;
    private final byte[] payload;

    private Jpeg2000QuantizationSegment(
            Jpeg2000QuantizationStyle style,
            int guardBits,
            int[] stepSizes,
            byte[] payload) {
        this.style = style;
        this.guardBits = guardBits;
        this.stepSizes = Arrays.copyOf(stepSizes, stepSizes.length);
        this.payload = Jpeg2000Payload.copy(payload);
    }

    public static Jpeg2000QuantizationSegment parse(
            Jpeg2000MarkerSegment segment,
            Jpeg2000CodingStyleSegment codingStyle) throws Jpeg2000Exception {
        Jpeg2000Payload.requireMarker(segment, Jpeg2000Marker.QCD);
        return parsePayload(segment.payload(), codingStyle, "QCD");
    }

    static Jpeg2000QuantizationSegment parsePayload(
            byte[] payload,
            Jpeg2000CodingStyleSegment codingStyle,
            String context) throws Jpeg2000Exception {
        if (codingStyle == null) {
            throw new NullPointerException("codingStyle");
        }
        if (payload.length == 0) {
            throw new Jpeg2000Exception("JPEG 2000 " + context + " payload is empty");
        }
        int sq = payload[0] & 0xff;
        Jpeg2000QuantizationStyle style = Jpeg2000QuantizationStyle.fromCode(sq & 0x1f);
        int guardBits = sq >>> 5;
        int subbands = 1 + 3 * codingStyle.decompositionLevels();
        int expectedLength;
        if (style == Jpeg2000QuantizationStyle.NO_QUANTIZATION) {
            expectedLength = 1 + subbands;
        } else if (style == Jpeg2000QuantizationStyle.SCALAR_DERIVED) {
            expectedLength = 3;
        } else {
            expectedLength = 1 + subbands * 2;
        }
        if (payload.length != expectedLength) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 " + context + " step-size payload length is invalid");
        }
        int count = style == Jpeg2000QuantizationStyle.SCALAR_DERIVED ? 1 : subbands;
        int[] steps = new int[count];
        int offset = 1;
        for (int index = 0; index < count; index++) {
            if (style == Jpeg2000QuantizationStyle.NO_QUANTIZATION) {
                steps[index] = payload[offset++] & 0xff;
            } else {
                steps[index] = Jpeg2000Payload.unsignedShort(payload, offset, context + " step size");
                offset += 2;
            }
        }
        return new Jpeg2000QuantizationSegment(style, guardBits, steps, payload);
    }

    public Jpeg2000QuantizationStyle style() {
        return style;
    }

    public int guardBits() {
        return guardBits;
    }

    public int[] stepSizes() {
        return Arrays.copyOf(stepSizes, stepSizes.length);
    }

    public byte[] payload() {
        return Jpeg2000Payload.copy(payload);
    }
}
