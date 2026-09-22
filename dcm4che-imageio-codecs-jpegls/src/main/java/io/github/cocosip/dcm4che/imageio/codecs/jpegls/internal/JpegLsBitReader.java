package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

final class JpegLsBitReader {
    private final byte[] bytes;
    private int position;
    private int currentByte;
    private int bitsRemaining;
    private boolean previousByteWasFf;

    JpegLsBitReader(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    int readBit() throws JpegLsException {
        if (bitsRemaining == 0) fillByte();
        bitsRemaining--;
        return (currentByte >>> bitsRemaining) & 1;
    }

    int readBits(int count) throws JpegLsException {
        if (count < 0 || count > 31) throw new JpegLsException("invalid JPEG-LS bit count");
        int value = 0;
        for (int i = 0; i < count; i++) value = value << 1 | readBit();
        return value;
    }

    private void fillByte() throws JpegLsException {
        if (position >= bytes.length) throw new JpegLsException("truncated JPEG-LS bit stream");
        currentByte = bytes[position++] & 0xff;
        bitsRemaining = previousByteWasFf ? 7 : 8;
        previousByteWasFf = currentByte == 0xff;
    }
}
