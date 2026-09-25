package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import javax.imageio.IIOException;

/** Strict bounded MagSgn codeword reader. */
final class Htj2kMagSgnDecoder {
    private final Htj2kForwardBitStream.Reader bits;

    Htj2kMagSgnDecoder(byte[] data, int offset, int length) {
        this(data, offset, length, false);
    }

    Htj2kMagSgnDecoder(byte[] data, int offset, int length, boolean terminalFill) {
        bits = new Htj2kForwardBitStream.Reader(data, offset, length,
                false, "MagSgn", terminalFill);
    }

    int decode(int bitCount) throws IIOException {
        return bits.readBits(bitCount);
    }

    int bitPosition() {
        return bits.bitPosition();
    }
}
