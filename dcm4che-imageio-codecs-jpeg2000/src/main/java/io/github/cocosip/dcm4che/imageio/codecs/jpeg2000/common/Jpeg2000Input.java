package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import java.io.IOException;

import javax.imageio.stream.ImageInputStream;

public final class Jpeg2000Input {
    private final ImageInputStream stream;
    private final long start;
    private final long end;

    public Jpeg2000Input(ImageInputStream stream, long frameLength, Jpeg2000Limits limits)
            throws IOException {
        if (stream == null) {
            throw new NullPointerException("stream");
        }
        if (limits == null) {
            throw new NullPointerException("limits");
        }
        int checkedLength = limits.requireFrameLength(frameLength);
        this.stream = stream;
        this.start = stream.getStreamPosition();
        if (start > Long.MAX_VALUE - checkedLength) {
            throw new Jpeg2000Exception("JPEG 2000 frame end offset overflow");
        }
        this.end = start + checkedLength;
    }

    public int readUnsignedByte(String context) throws IOException {
        requireAvailable(1, context);
        int value = stream.read();
        if (value < 0) {
            throw truncated(context);
        }
        return value;
    }

    public int readUnsignedShort(String context) throws IOException {
        int high = readUnsignedByte(context);
        int low = readUnsignedByte(context);
        return (high << 8) | low;
    }

    public long readUnsignedInt(String context) throws IOException {
        long high = readUnsignedShort(context);
        long low = readUnsignedShort(context);
        return (high << 16) | low;
    }

    public byte[] readFully(int length, String context) throws IOException {
        if (length < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }
        requireAvailable(length, context);
        byte[] bytes = new byte[length];
        try {
            stream.readFully(bytes);
        } catch (IOException error) {
            throw new Jpeg2000Exception("Truncated JPEG 2000 " + context + " at offset " + position(), error);
        }
        return bytes;
    }

    public byte[] peek(int length, String context) throws IOException {
        long original = stream.getStreamPosition();
        byte[] bytes = readFully(length, context);
        stream.seek(original);
        return bytes;
    }

    public long position() throws IOException {
        return stream.getStreamPosition() - start;
    }

    public long remaining() throws IOException {
        return end - stream.getStreamPosition();
    }

    private void requireAvailable(long count, String context) throws IOException {
        long current = stream.getStreamPosition();
        if (count < 0 || current > end || count > end - current) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 " + context + " exceeds the declared frame at offset " + (current - start));
        }
    }

    private Jpeg2000Exception truncated(String context) throws IOException {
        return new Jpeg2000Exception("Truncated JPEG 2000 " + context + " at offset " + position());
    }
}
