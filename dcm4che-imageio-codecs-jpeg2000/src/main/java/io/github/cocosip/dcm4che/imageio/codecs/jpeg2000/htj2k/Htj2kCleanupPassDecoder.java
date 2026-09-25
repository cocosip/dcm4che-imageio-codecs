package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import javax.imageio.IIOException;

/** Decodes one HT cleanup pass into signed integer coefficients. */
final class Htj2kCleanupPassDecoder {
    private Htj2kCleanupPassDecoder() {
    }

    static Htj2kCodeBlock decode(byte[] data, int width, int height, int kmax)
            throws IIOException {
        if (width <= 0 || height <= 0 || width > 64 || height > 64
                || (long) width * height > 4096 || kmax < 2 || kmax > 30) {
            throw new IIOException("Invalid HTJ2K cleanup geometry or precision");
        }
        if (data == null || data.length < 2 || data.length > 32768) {
            throw new IIOException("Invalid HTJ2K cleanup length");
        }
        int scup = ((data[data.length - 1] & 0xff) << 4)
                | (data[data.length - 2] & 0xf);
        if (scup < 2 || scup > 4079 || scup > data.length) {
            throw new IIOException("Invalid HTJ2K cleanup Scup=" + scup);
        }
        int suffix = data.length - scup;
        Htj2kMelDecoder mel = new Htj2kMelDecoder(data, suffix, scup - 1);
        Htj2kReverseBitStream.Reader vlc = new Htj2kReverseBitStream.Reader(
                data, suffix, scup, "cleanup VLC", true);
        MagSgnCursor magSgn = new MagSgnCursor(data, suffix);
        int[] result = new int[width * height];
        int quads = (width + 1) / 2;
        int[] previousExponents = new int[quads + 1];
        int[] previousSignificance = new int[quads + 1];
        for (int y = 0; y < height; y += 2) {
            int[] nextExponents = new int[quads + 1];
            int[] nextSignificance = new int[quads + 1];
            int previousRho = 0;
            boolean firstRow = y == 0;
            for (int quad = 0; quad < quads; quad += 2) {
                int context0 = firstRow ? initialContext(previousRho)
                        : subsequentContext(previousSignificance, quad, previousRho);
                int first = readSymbol(firstRow, context0, mel, vlc);
                int second = 0;
                if (quad + 1 < quads) {
                    int context1 = firstRow ? initialContext(Htj2kVlcDecoder.rho(first))
                            : subsequentContext(previousSignificance, quad + 1,
                                    Htj2kVlcDecoder.rho(first));
                    second = readSymbol(firstRow, context1, mel, vlc);
                }
                boolean u0 = Htj2kVlcDecoder.uOffset(first) != 0;
                boolean u1 = Htj2kVlcDecoder.uOffset(second) != 0;
                boolean melEvent = firstRow && u0 && u1 && mel.nextEvent();
                int[] u = Htj2kUvlc.decodePair(firstRow, u0, u1, melEvent, vlc);
                decodeQuad(first, u[0], quad, y, firstRow, width, height,
                        previousExponents, nextExponents, nextSignificance,
                        magSgn, result);
                if (quad + 1 < quads) {
                    decodeQuad(second, u[1], quad + 1, y, firstRow, width, height,
                            previousExponents, nextExponents, nextSignificance,
                            magSgn, result);
                }
                previousRho = Htj2kVlcDecoder.rho(second);
            }
            previousExponents = nextExponents;
            previousSignificance = nextSignificance;
        }
        return new Htj2kCodeBlock(width, height, kmax, result);
    }

    private static int readSymbol(boolean firstRow, int context,
            Htj2kMelDecoder mel, Htj2kReverseBitStream.Reader vlc)
            throws IIOException {
        if (context == 0 && !mel.nextEvent()) {
            return 0;
        }
        int symbol = Htj2kVlcDecoder.symbol(firstRow, context, vlc.peekBits(7));
        vlc.readBits(Htj2kVlcDecoder.codeLength(symbol));
        return symbol;
    }

    private static void decodeQuad(int symbol, int u, int quad, int y,
            boolean firstRow, int width, int height, int[] previousExponents,
            int[] nextExponents, int[] nextSignificance, MagSgnCursor magSgn,
            int[] result) throws IIOException {
        int rho = Htj2kVlcDecoder.rho(symbol);
        int kappa = firstRow || Integer.bitCount(rho) < 2 ? 1
                : Math.max(1, Math.max(previousExponents[quad],
                        previousExponents[quad + 1]) - 1);
        int uq = kappa + u;
        if (uq < 1 || uq > 31) {
            throw new IIOException("Invalid HTJ2K cleanup Uq at quad " + quad);
        }
        int ek = Htj2kVlcDecoder.ek(symbol);
        int e1 = Htj2kVlcDecoder.e1(symbol);
        for (int sample = 0; sample < 4; sample++) {
            if ((rho & (1 << sample)) == 0) {
                continue;
            }
            int bits = uq - ((ek >>> sample) & 1);
            int signMagnitude = magSgn.readBits(bits)
                    | (((e1 >>> sample) & 1) << bits);
            int magnitude = (signMagnitude >>> 1) + 1;
            int coefficient = (signMagnitude & 1) == 0 ? magnitude : -magnitude;
            int x = quad * 2 + (sample >>> 1);
            int sy = y + (sample & 1);
            if (x >= width || sy >= height) {
                throw new IIOException("HTJ2K cleanup marks an outside sample significant");
            }
            result[sy * width + x] = coefficient;
            int exponent = 32 - Integer.numberOfLeadingZeros(2 * magnitude - 1);
            if (sample == 1) {
                nextExponents[quad] = Math.max(nextExponents[quad], exponent);
            } else if (sample == 3) {
                nextExponents[quad + 1] = Math.max(nextExponents[quad + 1], exponent);
            }
        }
        nextSignificance[quad] |= (rho >>> 1) & 1;
        nextSignificance[quad + 1] |= (rho >>> 3) & 1;
    }

    private static int initialContext(int rho) {
        return (rho >>> 1) | (rho & 1);
    }

    private static int subsequentContext(int[] above, int quad, int rho) {
        return above[quad] | (above[quad + 1] << 2)
                | ((rho & 4) >>> 1) | ((rho & 8) >>> 2);
    }

    private static final class MagSgnCursor {
        private final Htj2kMagSgnDecoder bits;

        private MagSgnCursor(byte[] data, int length) {
            bits = new Htj2kMagSgnDecoder(data, 0, length, true);
        }

        private int readBits(int count) throws IIOException {
            return bits.decode(count);
        }
    }
}
