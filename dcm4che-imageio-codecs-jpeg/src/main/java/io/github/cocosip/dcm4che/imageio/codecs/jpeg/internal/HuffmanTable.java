package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import java.util.Arrays;

final class HuffmanTable {
    private final int[] codes = new int[256];
    private final int[] sizes = new int[256];
    private final int[][] symbolsByLength = new int[17][];
    private final int[][] codesByLength = new int[17][];

    private HuffmanTable(int[] counts, int[] values) {
        int total = 0;
        for (int count : counts) {
            if (count < 0) {
                throw new IllegalArgumentException("negative Huffman code count");
            }
            total += count;
        }
        if (counts.length != 16 || total != values.length || total == 0) {
            throw new IllegalArgumentException("invalid JPEG Huffman definition");
        }
        int code = 0;
        int valueIndex = 0;
        for (int length = 1; length <= 16; length++) {
            int count = counts[length - 1];
            int[] symbols = new int[count];
            int[] codesAtLength = new int[count];
            for (int i = 0; i < count; i++) {
                int symbol = values[valueIndex++];
                if (symbol < 0 || symbol > 255 || sizes[symbol] != 0) {
                    throw new IllegalArgumentException("duplicate or invalid Huffman symbol");
                }
                this.codes[symbol] = code;
                this.sizes[symbol] = length;
                symbols[i] = symbol;
                codesAtLength[i] = code;
                code++;
            }
            symbolsByLength[length] = symbols;
            codesByLength[length] = codesAtLength;
            if (length < 16) {
                code <<= 1;
                if (code > (1 << (length + 1))) {
                    throw new IllegalArgumentException("oversubscribed JPEG Huffman table");
                }
            }
        }
    }

    static HuffmanTable fromDefinition(int[] counts, int[] values) {
        return new HuffmanTable(Arrays.copyOf(counts, counts.length),
                Arrays.copyOf(values, values.length));
    }

    int codeFor(int symbol) {
        checkSymbol(symbol);
        if (sizes[symbol] == 0) {
            throw new IllegalArgumentException("Huffman symbol is not defined: " + symbol);
        }
        return codes[symbol];
    }

    int sizeFor(int symbol) {
        checkSymbol(symbol);
        if (sizes[symbol] == 0) {
            throw new IllegalArgumentException("Huffman symbol is not defined: " + symbol);
        }
        return sizes[symbol];
    }

    int symbolFor(int length, int code) {
        if (length < 1 || length > 16) {
            return -1;
        }
        int[] codesAtLength = codesByLength[length];
        int[] symbols = symbolsByLength[length];
        for (int i = 0; i < codesAtLength.length; i++) {
            if (codesAtLength[i] == code) {
                return symbols[i];
            }
        }
        return -1;
    }

    private static void checkSymbol(int symbol) {
        if (symbol < 0 || symbol > 255) {
            throw new IllegalArgumentException("Huffman symbol out of range: " + symbol);
        }
    }
}
