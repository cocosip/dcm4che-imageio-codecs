package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.io.IOException;

final class HuffmanCodec {
    private HuffmanCodec() {
    }

    static void encodeSymbol(BitWriter writer, HuffmanTable table, int symbol)
            throws IOException {
        writer.writeBits(table.codeFor(symbol), table.sizeFor(symbol));
    }

    static int decodeSymbol(BitReader reader, HuffmanTable table) throws IOException {
        int code = 0;
        for (int length = 1; length <= 16; length++) {
            code = (code << 1) | reader.readBits(1);
            int symbol = table.symbolFor(length, code);
            if (symbol >= 0) {
                return symbol;
            }
        }
        throw new JpegException("invalid JPEG Huffman code");
    }
}
