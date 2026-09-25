package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.imageio.IIOException;

import org.junit.jupiter.api.Test;

class Htj2kVlcTest {
    @Test
    void matchesFixedFirstAndSubsequentRowSymbols() throws Exception {
        int first = Htj2kVlcEncoder.codeword(true, 0, 1, 0);
        assertEquals(0x06, Htj2kVlcEncoder.code(first));
        assertEquals(4, Htj2kVlcEncoder.codeLength(first));
        int decodedFirst = Htj2kVlcDecoder.symbol(true, 0, 0x06);
        assertEquals(1, Htj2kVlcDecoder.rho(decodedFirst));
        assertEquals(4, Htj2kVlcDecoder.codeLength(decodedFirst));

        int later = Htj2kVlcEncoder.codeword(false, 0, 1, 0);
        assertEquals(0, Htj2kVlcEncoder.code(later));
        assertEquals(3, Htj2kVlcEncoder.codeLength(later));
        int decodedLater = Htj2kVlcDecoder.symbol(false, 0, 0);
        assertEquals(1, Htj2kVlcDecoder.rho(decodedLater));
        assertEquals(3, Htj2kVlcDecoder.codeLength(decodedLater));
    }

    @Test
    void everyEncodableTableEntryDecodesToItsSignificanceAndEmb() throws Exception {
        int checked = 0;
        for (boolean firstRow : new boolean[] {true, false}) {
            for (int context = 0; context < 8; context++) {
                for (int rho = 0; rho < 16; rho++) {
                    for (int emb = 0; emb < 16; emb++) {
                        if ((emb & ~rho) != 0 || (context == 0 && rho == 0)) {
                            continue;
                        }
                        int encoded;
                        try {
                            encoded = Htj2kVlcEncoder.codeword(firstRow, context, rho, emb);
                        } catch (IIOException unsupported) {
                            continue;
                        }
                        int decoded = Htj2kVlcDecoder.symbol(firstRow, context,
                                Htj2kVlcEncoder.code(encoded));
                        assertEquals(rho, Htj2kVlcDecoder.rho(decoded));
                        assertEquals(emb == 0 ? 0 : 1, Htj2kVlcDecoder.uOffset(decoded));
                        assertEquals(Htj2kVlcEncoder.ek(encoded), Htj2kVlcDecoder.ek(decoded));
                        assertEquals(emb & Htj2kVlcEncoder.ek(encoded),
                                Htj2kVlcDecoder.e1(decoded));
                        assertEquals(Htj2kVlcEncoder.codeLength(encoded),
                                Htj2kVlcDecoder.codeLength(decoded));
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 1000);
    }

    @Test
    void reverseViewStuffingAndTruncationAreBounded() throws Exception {
        Htj2kReverseBitStream.Writer writer =
                new Htj2kReverseBitStream.Writer(2, "VLC");
        writer.writeBits(0xf0, 8);
        writer.writeBits(0x7f, 7);
        assertArrayEquals(new byte[] {0x7f, (byte) 0xf0}, writer.finish());

        Htj2kReverseBitStream.Reader reader = new Htj2kReverseBitStream.Reader(
                new byte[] {0x7f, (byte) 0xf0}, 0, 2, "VLC");
        assertEquals(0xf0, reader.readBits(8));
        assertEquals(0x7f, reader.readBits(7));
        assertEquals(15, reader.bitPosition());
        assertThrows(IIOException.class, reader::readBit);

        Htj2kReverseBitStream.Reader malformed = new Htj2kReverseBitStream.Reader(
                new byte[] {(byte) 0xff, (byte) 0xf0}, 0, 2, "VLC");
        malformed.readBits(8);
        assertTrue(assertThrows(IIOException.class, malformed::readBit)
                .getMessage().contains("reverse stuffed bit at bit position 8"));
    }
}
