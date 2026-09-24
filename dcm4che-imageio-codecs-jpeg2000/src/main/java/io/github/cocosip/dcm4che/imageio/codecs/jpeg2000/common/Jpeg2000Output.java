package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import java.io.IOException;

import javax.imageio.stream.ImageOutputStream;

public final class Jpeg2000Output {
    private final ImageOutputStream stream;
    private final long start;
    private final Jpeg2000Limits limits;

    public Jpeg2000Output(ImageOutputStream stream, Jpeg2000Limits limits) throws IOException {
        if (stream == null) {
            throw new NullPointerException("stream");
        }
        if (limits == null) {
            throw new NullPointerException("limits");
        }
        this.stream = stream;
        this.start = stream.getStreamPosition();
        this.limits = limits;
    }

    public void writeByte(int value) throws IOException {
        requireCapacity(1);
        stream.write(value);
    }

    public void writeShort(int value) throws IOException {
        requireCapacity(2);
        stream.write((value >>> 8) & 0xff);
        stream.write(value & 0xff);
    }

    public void writeInt(long value) throws IOException {
        requireCapacity(4);
        stream.write((int) (value >>> 24) & 0xff);
        stream.write((int) (value >>> 16) & 0xff);
        stream.write((int) (value >>> 8) & 0xff);
        stream.write((int) value & 0xff);
    }

    public void write(byte[] bytes) throws IOException {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        requireCapacity(bytes.length);
        stream.write(bytes);
    }

    public long position() throws IOException {
        return stream.getStreamPosition() - start;
    }

    private void requireCapacity(int additionalBytes) throws IOException {
        long currentLength = position();
        if (currentLength > Long.MAX_VALUE - additionalBytes) {
            throw new Jpeg2000Exception("JPEG 2000 output length overflow");
        }
        limits.requireFrameLength(currentLength + additionalBytes);
    }
}
