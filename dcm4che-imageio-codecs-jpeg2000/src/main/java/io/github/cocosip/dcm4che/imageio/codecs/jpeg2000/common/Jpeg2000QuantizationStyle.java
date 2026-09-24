package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

public enum Jpeg2000QuantizationStyle {
    NO_QUANTIZATION(0),
    SCALAR_DERIVED(1),
    SCALAR_EXPOUNDED(2);

    private final int code;

    Jpeg2000QuantizationStyle(int code) {
        this.code = code;
    }

    static Jpeg2000QuantizationStyle fromCode(int code) throws Jpeg2000Exception {
        for (Jpeg2000QuantizationStyle value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new Jpeg2000Exception("JPEG 2000 quantization style " + code + " is invalid");
    }
}
