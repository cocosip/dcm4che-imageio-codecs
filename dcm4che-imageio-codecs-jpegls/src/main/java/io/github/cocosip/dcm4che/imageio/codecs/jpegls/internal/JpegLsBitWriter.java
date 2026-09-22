package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import java.io.ByteArrayOutputStream;

final class JpegLsBitWriter {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private int currentByte;
    private int bitCount;

    void writeBit(int bit) throws JpegLsException {
        writeBits(bit & 1, 1);
    }

    void writeBits(int value, int count) throws JpegLsException {
        if (count < 0 || count > 31) throw new JpegLsException("invalid JPEG-LS bit count");
        for (int bit = count - 1; bit >= 0; bit--) {
            currentByte |= ((value >>> bit) & 1) << (7 - bitCount);
            bitCount++;
            if (bitCount == 8) {
                output.write(currentByte);
                if (currentByte == 0xff) {
                    currentByte = 0;
                    bitCount = 1;
                } else {
                    currentByte = 0;
                    bitCount = 0;
                }
            }
        }
    }

    byte[] toByteArray() {
        if (bitCount > 0) output.write(currentByte);
        currentByte = 0;
        bitCount = 0;
        return output.toByteArray();
    }
}
