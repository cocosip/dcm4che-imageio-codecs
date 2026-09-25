package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.io.ByteArrayOutputStream;

import javax.imageio.IIOException;

/** Single HT cleanup pass for signed integer coefficients. */
final class Htj2kCleanupPassEncoder {
    private Htj2kCleanupPassEncoder() {
    }

    static byte[] encode(Htj2kCodeBlock block) throws IIOException {
        int samples = block.width() * block.height();
        int maxBytes = samples * 8 + 32;
        Htj2kMelEncoder mel = new Htj2kMelEncoder(Math.min(maxBytes, 4095));
        Htj2kMagSgnEncoder magSgn = new Htj2kMagSgnEncoder(maxBytes);
        Htj2kReverseBitStream.Writer vlc =
                new Htj2kReverseBitStream.Writer(Math.min(maxBytes, 4095), "VLC", true);

        int quads = (block.width() + 1) / 2;
        int[] previousExponents = new int[quads + 1];
        int[] previousSignificance = new int[quads + 1];
        for (int y = 0; y < block.height(); y += 2) {
            int[] nextExponents = new int[quads + 1];
            int[] nextSignificance = new int[quads + 1];
            int previousRho = 0;
            boolean firstRow = y == 0;
            for (int quad = 0; quad < quads; quad += 2) {
                int context0 = firstRow ? initialContext(previousRho)
                        : subsequentContext(previousSignificance, quad, previousRho);
                Quad first = encodeQuad(block, quad, y, firstRow, context0,
                        previousExponents, nextExponents, nextSignificance, mel, magSgn, vlc);

                Quad second = Quad.ZERO;
                if (quad + 1 < quads) {
                    int context1 = firstRow ? initialContext(first.rho)
                            : subsequentContext(previousSignificance, quad + 1, first.rho);
                    second = encodeQuad(block, quad + 1, y, firstRow, context1,
                            previousExponents, nextExponents, nextSignificance,
                            mel, magSgn, vlc);
                }
                if (firstRow && first.u > 0 && second.u > 0) {
                    mel.encode(Htj2kUvlc.initialMelEvent(first.u, second.u));
                }
                Htj2kUvlc.encodePair(firstRow, first.u, second.u, vlc);
                previousRho = second.rho;
            }
            previousExponents = nextExponents;
            previousSignificance = nextSignificance;
        }
        return assemble(mel, vlc, magSgn);
    }

    private static Quad encodeQuad(Htj2kCodeBlock block, int quad, int y,
            boolean firstRow, int context, int[] previousExponents,
            int[] nextExponents, int[] nextSignificance, Htj2kMelEncoder mel,
            Htj2kMagSgnEncoder magSgn, Htj2kReverseBitStream.Writer vlc)
            throws IIOException {
        int x = quad * 2;
        int[] exponents = new int[4];
        int[] signMagnitude = new int[4];
        int rho = 0;
        int maximumExponent = 0;
        for (int sample = 0; sample < 4; sample++) {
            int sx = x + (sample >>> 1);
            int sy = y + (sample & 1);
            if (sx >= block.width() || sy >= block.height()) {
                continue;
            }
            int coefficient = block.coefficient(sx, sy);
            if (coefficient == 0) {
                continue;
            }
            int magnitude = (int) Math.abs((long) coefficient);
            int doubledMinusOne = 2 * magnitude - 1;
            exponents[sample] = 32 - Integer.numberOfLeadingZeros(doubledMinusOne);
            signMagnitude[sample] = 2 * (magnitude - 1) + (coefficient < 0 ? 1 : 0);
            maximumExponent = Math.max(maximumExponent, exponents[sample]);
            rho |= 1 << sample;
        }

        int kappa = firstRow || Integer.bitCount(rho) < 2 ? 1
                : Math.max(1, Math.max(previousExponents[quad],
                        previousExponents[quad + 1]) - 1);
        int uq = Math.max(maximumExponent, kappa);
        int u = uq - kappa;
        int emb = 0;
        if (u > 0) {
            for (int sample = 0; sample < 4; sample++) {
                if (exponents[sample] == maximumExponent) {
                    emb |= 1 << sample;
                }
            }
        }

        nextExponents[quad] = Math.max(nextExponents[quad], exponents[1]);
        nextExponents[quad + 1] = Math.max(nextExponents[quad + 1], exponents[3]);
        nextSignificance[quad] |= (rho >>> 1) & 1;
        nextSignificance[quad + 1] |= (rho >>> 3) & 1;

        int tuple = context == 0 && rho == 0 ? 0
                : Htj2kVlcEncoder.codeword(firstRow, context, rho, emb);
        if (tuple != 0) {
            vlc.writeBits(Htj2kVlcEncoder.code(tuple), Htj2kVlcEncoder.codeLength(tuple));
        }
        if (context == 0) {
            mel.encode(rho != 0);
        }
        for (int sample = 0; sample < 4; sample++) {
            if ((rho & (1 << sample)) != 0) {
                int bits = uq - ((Htj2kVlcEncoder.ek(tuple) >>> sample) & 1);
                if (bits < 0 || bits > 31) {
                    throw new IIOException("Invalid HTJ2K MagSgn width in quad " + quad);
                }
                magSgn.encode(signMagnitude[sample], bits);
            }
        }
        return new Quad(rho, u);
    }

    private static int initialContext(int previousRho) {
        return (previousRho >>> 1) | (previousRho & 1);
    }

    private static int subsequentContext(int[] previousSignificance, int quad,
            int previousRho) {
        return previousSignificance[quad] | (previousSignificance[quad + 1] << 2)
                | ((previousRho & 4) >>> 1) | ((previousRho & 8) >>> 2);
    }

    private static byte[] assemble(Htj2kMelEncoder mel,
            Htj2kReverseBitStream.Writer vlc, Htj2kMagSgnEncoder magSgn)
            throws IIOException {
        mel.terminateRun();
        int melPending = mel.pendingByteAligned();
        int melMask = (0xff << mel.remainingBits()) & 0xff;
        int vlcPending = vlc.pendingByte();
        int vlcMask = 0xff >>> (8 - vlc.usedBits());
        int fused = melPending | vlcPending;
        boolean canFuse = (((fused ^ melPending) & melMask)
                | ((fused ^ vlcPending) & vlcMask)) == 0
                && fused != 0xff && vlc.emittedCount() > 1;

        byte[] melFull = mel.emittedBytes();
        byte[] vlcBytes = canFuse ? vlc.emittedBytes() : vlc.finish();
        byte[] magSgnBytes = magSgn.finish();
        int scup = melFull.length + 1 + vlcBytes.length;
        if (scup < 2 || scup > 4095) {
            throw new IIOException("HTJ2K cleanup Scup is outside 2..4095");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(magSgnBytes, 0, magSgnBytes.length);
        output.write(melFull, 0, melFull.length);
        output.write(canFuse ? fused : melPending);
        output.write(vlcBytes, 0, vlcBytes.length);
        byte[] result = output.toByteArray();
        result[result.length - 1] = (byte) (scup >>> 4);
        result[result.length - 2] = (byte) ((result[result.length - 2] & 0xf0) | (scup & 0xf));
        return result;
    }

    private static final class Quad {
        private static final Quad ZERO = new Quad(0, 0);

        private final int rho;
        private final int u;

        private Quad(int rho, int u) {
            this.rho = rho;
            this.u = u;
        }
    }
}
