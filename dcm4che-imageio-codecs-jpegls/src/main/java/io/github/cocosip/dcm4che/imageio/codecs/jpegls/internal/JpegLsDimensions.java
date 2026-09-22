package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

final class JpegLsDimensions {
    private long height;
    private long width;

    private JpegLsDimensions(long height, long width) {
        this.height = height;
        this.width = width;
    }

    static JpegLsDimensions fromFrame(int height, int width) {
        return new JpegLsDimensions(height, width);
    }

    void applyOversize(byte[] payload) throws JpegLsException {
        if (payload.length < 6 || unsigned(payload[0]) != 4) {
            throw new JpegLsException("invalid JPEG-LS oversize dimension segment");
        }
        int byteWidth = unsigned(payload[1]);
        if (byteWidth < 2 || byteWidth > 4 || payload.length != 2 + 2 * byteWidth) {
            throw new JpegLsException("invalid JPEG-LS oversize dimension width: " + byteWidth);
        }
        if (height != 0 || width != 0) {
            throw new JpegLsException("JPEG-LS oversize dimensions require zero SOF dimensions");
        }
        long extendedHeight = unsignedInteger(payload, 2, byteWidth);
        long extendedWidth = unsignedInteger(payload, 2 + byteWidth, byteWidth);
        if (extendedHeight == 0 || extendedWidth == 0) {
            throw new JpegLsException("JPEG-LS oversize dimensions must be non-zero");
        }
        height = extendedHeight;
        width = extendedWidth;
    }

    void applyDnl(byte[] payload) throws JpegLsException {
        if (height != 0) {
            throw new JpegLsException("JPEG-LS DNL cannot replace a non-zero height");
        }
        height = parseDnl(payload);
    }

    static long parseDnl(byte[] payload) throws JpegLsException {
        if (payload.length < 2 || payload.length > 4) {
            throw new JpegLsException("invalid JPEG-LS DNL payload length: " + payload.length);
        }
        long value = unsignedInteger(payload, 0, payload.length);
        if (value == 0) {
            throw new JpegLsException("JPEG-LS DNL height must be non-zero");
        }
        return value;
    }

    long height() {
        return height;
    }

    long width() {
        return width;
    }

    private static long unsignedInteger(byte[] values, int offset, int length) {
        long value = 0;
        for (int i = 0; i < length; i++) {
            value = value << 8 | unsigned(values[offset + i]);
        }
        return value;
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }
}
