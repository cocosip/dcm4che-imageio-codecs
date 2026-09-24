package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

public enum Jpeg2000ProgressionOrder {
    LRCP(0),
    RLCP(1),
    RPCL(2),
    PCRL(3),
    CPRL(4);

    private final int code;

    Jpeg2000ProgressionOrder(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    static Jpeg2000ProgressionOrder fromCode(int code) throws Jpeg2000Exception {
        for (Jpeg2000ProgressionOrder value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new Jpeg2000Exception("JPEG 2000 COD progression order " + code + " is invalid");
    }
}
