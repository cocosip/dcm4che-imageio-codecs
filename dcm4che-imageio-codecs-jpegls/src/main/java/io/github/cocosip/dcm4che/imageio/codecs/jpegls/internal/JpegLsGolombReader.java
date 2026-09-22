package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

final class JpegLsGolombReader {
    private final JpegLsBitReader bits;

    JpegLsGolombReader(byte[] bytes) {
        this.bits = new JpegLsBitReader(bytes);
    }

    int readBit() throws JpegLsException {
        return bits.readBit();
    }

    int readBits(int count) throws JpegLsException {
        return bits.readBits(count);
    }

    int read(int parameter) throws JpegLsException {
        if (parameter < 0 || parameter > 30) throw new JpegLsException("invalid JPEG-LS Golomb parameter");
        int quotient = 0;
        while (bits.readBit() == 0) quotient++;
        return quotient << parameter | bits.readBits(parameter);
    }

    int readMapped(int parameter, int limit, int quantizedBitsPerPixel) throws JpegLsException {
        if (parameter < 0 || parameter > 30 || limit <= 0
                || quantizedBitsPerPixel < 1 || quantizedBitsPerPixel > 31) {
            throw new JpegLsException("invalid JPEG-LS mapped Golomb parameters");
        }
        int highBits = 0;
        while (bits.readBit() == 0) highBits++;
        if (highBits >= limit - (quantizedBitsPerPixel + 1)) {
            return bits.readBits(quantizedBitsPerPixel) + 1;
        }
        return parameter == 0 ? highBits : (highBits << parameter) | bits.readBits(parameter);
    }
}
