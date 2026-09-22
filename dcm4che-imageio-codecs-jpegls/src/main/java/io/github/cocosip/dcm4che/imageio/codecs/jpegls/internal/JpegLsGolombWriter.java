package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

final class JpegLsGolombWriter {
    private final JpegLsBitWriter bits = new JpegLsBitWriter();

    void write(int value, int parameter) throws JpegLsException {
        validate(value, parameter);
        int quotient = value >>> parameter;
        for (int i = 0; i < quotient; i++) bits.writeBit(0);
        bits.writeBit(1);
        if (parameter > 0) bits.writeBits(value & ((1 << parameter) - 1), parameter);
    }

    void writeBit(int value) throws JpegLsException {
        bits.writeBit(value);
    }

    void writeBits(int value, int count) throws JpegLsException {
        bits.writeBits(value, count);
    }

    void writeMapped(int mappedError, int parameter, int limit, int quantizedBitsPerPixel)
            throws JpegLsException {
        if (mappedError < 0 || parameter < 0 || parameter > 30 || limit <= 0
                || quantizedBitsPerPixel < 1 || quantizedBitsPerPixel > 31) {
            throw new JpegLsException("invalid JPEG-LS mapped Golomb parameters");
        }
        int highBits = mappedError >>> parameter;
        if (highBits < limit - (quantizedBitsPerPixel + 1)) {
            for (int i = 0; i < highBits; i++) bits.writeBit(0);
            bits.writeBit(1);
            if (parameter > 0) bits.writeBits(mappedError & ((1 << parameter) - 1), parameter);
        } else {
            int escape = (1 << quantizedBitsPerPixel) - 1;
            for (int i = 0; i < limit - quantizedBitsPerPixel - 1; i++) bits.writeBit(0);
            bits.writeBit(1);
            bits.writeBits((mappedError - 1) & escape, quantizedBitsPerPixel);
        }
    }

    byte[] toByteArray() { return bits.toByteArray(); }

    private static void validate(int value, int parameter) throws JpegLsException {
        if (value < 0 || parameter < 0 || parameter > 30) {
            throw new JpegLsException("invalid JPEG-LS Golomb parameters");
        }
    }
}
