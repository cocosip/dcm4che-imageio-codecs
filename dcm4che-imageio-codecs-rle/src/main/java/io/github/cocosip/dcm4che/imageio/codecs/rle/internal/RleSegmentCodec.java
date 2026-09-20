package io.github.cocosip.dcm4che.imageio.codecs.rle.internal;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import javax.imageio.IIOException;

final class RleSegmentCodec {
    private static final int MAX_RUN_LENGTH = 128;

    private RleSegmentCodec() {
    }

    static byte[] decode(byte[] encoded, int expectedLength) throws IIOException {
        if (encoded == null) {
            throw new NullPointerException("encoded");
        }
        if (expectedLength < 0) {
            throw new IllegalArgumentException("expectedLength must not be negative");
        }

        byte[] decoded = new byte[expectedLength];
        int inputIndex = 0;
        int outputIndex = 0;
        while (inputIndex < encoded.length) {
            int control = encoded[inputIndex++];
            if (control >= 0) {
                int literalLength = control + 1;
                if (inputIndex + literalLength > encoded.length) {
                    throw error("literal run exceeds segment input");
                }
                if (outputIndex + literalLength > decoded.length) {
                    throw error("literal run exceeds segment output");
                }
                System.arraycopy(encoded, inputIndex, decoded, outputIndex, literalLength);
                inputIndex += literalLength;
                outputIndex += literalLength;
            } else if (control != -128) {
                int repeatLength = 1 - control;
                if (inputIndex >= encoded.length) {
                    throw error("repeat run is missing its repeated byte");
                }
                if (outputIndex + repeatLength > decoded.length) {
                    throw error("repeat run exceeds segment output");
                }
                Arrays.fill(decoded, outputIndex, outputIndex + repeatLength,
                        encoded[inputIndex++]);
                outputIndex += repeatLength;
            }
        }

        if (outputIndex != expectedLength) {
            throw error("decoded segment length " + outputIndex
                    + " does not match expected length " + expectedLength);
        }
        return decoded;
    }

    static byte[] encode(byte[] source) {
        if (source == null) {
            throw new NullPointerException("source");
        }

        ByteArrayOutputStream encoded = new ByteArrayOutputStream(source.length + 1);
        byte[] literals = new byte[132];
        int literalLength = 0;
        int previous = -1;
        int repeatLength = 0;

        for (byte sourceByte : source) {
            int value = sourceByte & 0xff;
            if (value == previous) {
                repeatLength++;
                if (repeatLength > 2 && literalLength > 0) {
                    writeLiterals(encoded, literals, literalLength);
                    literalLength = 0;
                } else if (repeatLength > MAX_RUN_LENGTH) {
                    writeRepeat(encoded, previous, MAX_RUN_LENGTH);
                    repeatLength -= MAX_RUN_LENGTH;
                }
                continue;
            }

            if (repeatLength == 1) {
                literals[literalLength++] = (byte) previous;
            } else if (repeatLength == 2) {
                literals[literalLength++] = (byte) previous;
                literals[literalLength++] = (byte) previous;
            } else if (repeatLength > 2) {
                writeRepeats(encoded, previous, repeatLength);
            }

            while (literalLength > MAX_RUN_LENGTH) {
                writeLiteral(encoded, literals, MAX_RUN_LENGTH);
                System.arraycopy(literals, MAX_RUN_LENGTH, literals, 0,
                        literalLength - MAX_RUN_LENGTH);
                literalLength -= MAX_RUN_LENGTH;
            }
            previous = value;
            repeatLength = 1;
        }

        if (repeatLength < 2) {
            while (repeatLength > 0) {
                literals[literalLength++] = (byte) previous;
                repeatLength--;
            }
        }
        writeLiterals(encoded, literals, literalLength);
        if (repeatLength >= 2) {
            writeRepeats(encoded, previous, repeatLength);
        }
        return encoded.toByteArray();
    }

    private static void writeLiterals(
            ByteArrayOutputStream output, byte[] literals, int literalLength) {
        int offset = 0;
        while (literalLength > 0) {
            int count = Math.min(MAX_RUN_LENGTH, literalLength);
            output.write(count - 1);
            output.write(literals, offset, count);
            offset += count;
            literalLength -= count;
        }
    }

    private static void writeLiteral(
            ByteArrayOutputStream output, byte[] literals, int count) {
        output.write(count - 1);
        output.write(literals, 0, count);
    }

    private static void writeRepeats(ByteArrayOutputStream output, int value, int count) {
        while (count > 0) {
            int runLength = Math.min(MAX_RUN_LENGTH, count);
            writeRepeat(output, value, runLength);
            count -= runLength;
        }
    }

    private static void writeRepeat(ByteArrayOutputStream output, int value, int count) {
        output.write(1 - count);
        output.write(value);
    }

    private static IIOException error(String message) {
        return new IIOException("RLE Lossless " + message);
    }
}
