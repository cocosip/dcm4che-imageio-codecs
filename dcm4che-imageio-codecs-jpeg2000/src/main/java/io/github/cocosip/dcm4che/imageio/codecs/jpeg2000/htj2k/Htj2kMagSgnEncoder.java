package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.util.Arrays;

import javax.imageio.IIOException;

/** LSB-first MagSgn codeword writer with HT terminal fill. */
final class Htj2kMagSgnEncoder {
    private final Htj2kForwardBitStream.Writer bits;

    Htj2kMagSgnEncoder(int maxBytes) {
        bits = new Htj2kForwardBitStream.Writer(maxBytes, false, "MagSgn");
    }

    void encode(int codeword, int bitCount) throws IIOException {
        bits.writeBits(codeword, bitCount);
    }

    byte[] finish() throws IIOException {
        byte[] encoded = bits.finish(true);
        return encoded.length > 0 && (encoded[encoded.length - 1] & 0xff) == 0xff
                ? Arrays.copyOf(encoded, encoded.length - 1) : encoded;
    }

    int bitPosition() {
        return bits.bitPosition();
    }
}
