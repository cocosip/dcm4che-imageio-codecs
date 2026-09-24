package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;

/** Stateful inline packet encoder for one classic precinct subband. */
public final class Jpeg2000PacketEncoder {
    private final int gridWidth;
    private final int gridHeight;
    private final List<CodeBlock> blocks;
    private final Jpeg2000TagTree inclusion;
    private final Jpeg2000TagTree zeroBitPlanes;
    private final BlockState[] states;
    private int nextLayer;

    public Jpeg2000PacketEncoder(int gridWidth, int gridHeight, List<CodeBlock> blocks) {
        if (gridWidth <= 0 || gridHeight <= 0
                || (long) gridWidth * gridHeight > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("JPEG 2000 packet code-block grid is invalid");
        }
        if (blocks == null) {
            throw new NullPointerException("blocks");
        }
        if (blocks.size() != gridWidth * gridHeight) {
            throw new IllegalArgumentException(
                    "JPEG 2000 packet requires one code-block for every grid position");
        }
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.blocks = Collections.unmodifiableList(new ArrayList<CodeBlock>(blocks));
        inclusion = new Jpeg2000TagTree(gridWidth, gridHeight);
        zeroBitPlanes = new Jpeg2000TagTree(gridWidth, gridHeight);
        states = new BlockState[blocks.size()];

        boolean[] occupied = new boolean[blocks.size()];
        for (int index = 0; index < blocks.size(); index++) {
            CodeBlock block = blocks.get(index);
            if (block == null || block.x < 0 || block.x >= gridWidth
                    || block.y < 0 || block.y >= gridHeight) {
                throw new IllegalArgumentException("JPEG 2000 packet code-block coordinate is invalid");
            }
            int position = block.y * gridWidth + block.x;
            if (occupied[position]) {
                throw new IllegalArgumentException("Duplicate JPEG 2000 packet code-block coordinate");
            }
            if (position != index) {
                throw new IllegalArgumentException(
                        "JPEG 2000 packet code-blocks must use raster grid order");
            }
            occupied[position] = true;
            zeroBitPlanes.setValue(block.x, block.y, block.zeroBitPlanes);
            states[index] = new BlockState();
        }
    }

    public EncodedPacket encodeLayer(int layer, int[] cumulativePassCounts)
            throws Jpeg2000Exception {
        if (layer != nextLayer) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 packet layer sequence expected " + nextLayer + " but was " + layer);
        }
        if (cumulativePassCounts == null || cumulativePassCounts.length != blocks.size()) {
            throw new IllegalArgumentException(
                    "JPEG 2000 packet pass targets must match the code-block count");
        }

        boolean present = false;
        for (int index = 0; index < blocks.size(); index++) {
            int target = cumulativePassCounts[index];
            BlockState state = states[index];
            if (target < state.passCount || target > blocks.get(index).passCount()) {
                throw new Jpeg2000Exception(
                        "Invalid JPEG 2000 cumulative pass target for code-block " + index);
            }
            if (target > state.passCount) {
                present = true;
            }
        }

        Jpeg2000PacketBitWriter header = new Jpeg2000PacketBitWriter();
        header.writeBit(present ? 1 : 0);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (present) {
            for (int index = 0; index < blocks.size(); index++) {
                BlockState state = states[index];
                if (!state.included && cumulativePassCounts[index] > state.passCount) {
                    CodeBlock block = blocks.get(index);
                    inclusion.setValue(block.x, block.y, layer);
                }
            }

            for (int index = 0; index < blocks.size(); index++) {
                CodeBlock block = blocks.get(index);
                BlockState state = states[index];
                int targetPasses = cumulativePassCounts[index];
                int passDelta = targetPasses - state.passCount;
                boolean contributes = passDelta > 0;
                if (!state.included) {
                    inclusion.encode(header, block.x, block.y, layer + 1);
                    if (!contributes) {
                        continue;
                    }
                    zeroBitPlanes.encode(header, block.x, block.y, 64);
                    state.included = true;
                    state.numLengthBits = 3;
                } else {
                    header.writeBit(contributes ? 1 : 0);
                    if (!contributes) {
                        continue;
                    }
                }

                int targetLength = block.byteLength(targetPasses);
                int byteDelta = targetLength - state.byteLength;
                encodePassCount(header, passDelta);
                int required = requiredLengthBits(byteDelta, passDelta);
                while (state.numLengthBits < required) {
                    header.writeBit(1);
                    state.numLengthBits++;
                }
                header.writeBit(0);
                int lengthBits = state.numLengthBits + floorLog2(passDelta);
                header.writeBits(byteDelta, lengthBits);
                body.write(block.data, state.byteLength, byteDelta);
                state.passCount = targetPasses;
                state.byteLength = targetLength;
            }
        }
        header.align();
        nextLayer++;
        return new EncodedPacket(header.toByteArray(), body.toByteArray());
    }

    static void encodePassCount(Jpeg2000PacketBitWriter writer, int passCount) {
        if (writer == null) {
            throw new NullPointerException("writer");
        }
        if (passCount < 1 || passCount > 164) {
            throw new IllegalArgumentException("JPEG 2000 packet pass count must be 1..164");
        }
        if (passCount == 1) {
            writer.writeBit(0);
        } else if (passCount == 2) {
            writer.writeBits(2, 2);
        } else if (passCount <= 5) {
            writer.writeBits(3, 2);
            writer.writeBits(passCount - 3, 2);
        } else if (passCount <= 36) {
            writer.writeBits(15, 4);
            writer.writeBits(passCount - 6, 5);
        } else {
            writer.writeBits(15, 4);
            writer.writeBits(31, 5);
            writer.writeBits(passCount - 37, 7);
        }
    }

    private static int requiredLengthBits(int length, int passCount) {
        int bits = length == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(length);
        return Math.max(3, bits - floorLog2(passCount));
    }

    static int floorLog2(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("value must be positive");
        }
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    private static final class BlockState {
        private boolean included;
        private int passCount;
        private int byteLength;
        private int numLengthBits;
    }

    public static final class CodeBlock {
        private final int x;
        private final int y;
        private final int zeroBitPlanes;
        private final byte[] data;
        private final int[] passLengths;

        public CodeBlock(
                int x, int y, int zeroBitPlanes, byte[] data, int[] passLengths) {
            if (x < 0 || y < 0) {
                throw new IllegalArgumentException("JPEG 2000 packet code-block coordinate cannot be negative");
            }
            if (zeroBitPlanes < 0 || zeroBitPlanes > 63) {
                throw new IllegalArgumentException("JPEG 2000 zero-bit-plane count must be 0..63");
            }
            if (data == null || passLengths == null) {
                throw new NullPointerException(data == null ? "data" : "passLengths");
            }
            if (passLengths.length > 164) {
                throw new IllegalArgumentException("JPEG 2000 code-block pass count exceeds 164");
            }
            if (passLengths.length == 0 && data.length != 0) {
                throw new IllegalArgumentException(
                        "JPEG 2000 code-block payload requires pass metadata");
            }
            int previous = 0;
            for (int length : passLengths) {
                if (length < previous || length > data.length) {
                    throw new IllegalArgumentException("JPEG 2000 code-block pass lengths are invalid");
                }
                previous = length;
            }
            if (passLengths.length > 0 && previous != data.length) {
                throw new IllegalArgumentException(
                        "JPEG 2000 final pass length must equal the code-block payload length");
            }
            this.x = x;
            this.y = y;
            this.zeroBitPlanes = zeroBitPlanes;
            this.data = data.clone();
            this.passLengths = passLengths.clone();
        }

        public static CodeBlock fromEbcot(
                int x, int y, int zeroBitPlanes, Jpeg2000EbcotEncodedBlock encoded) {
            if (encoded == null) {
                throw new NullPointerException("encoded");
            }
            int[] lengths = new int[encoded.passes().size()];
            for (int index = 0; index < lengths.length; index++) {
                lengths[index] = encoded.passes().get(index).byteLength();
            }
            return new CodeBlock(x, y, zeroBitPlanes, encoded.data(), lengths);
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int zeroBitPlanes() {
            return zeroBitPlanes;
        }

        public int passCount() {
            return passLengths.length;
        }

        public byte[] data() {
            return data.clone();
        }

        public int[] passLengths() {
            return passLengths.clone();
        }

        int byteLength(int passes) {
            return passes == 0 ? 0 : passLengths[passes - 1];
        }
    }

    public static final class EncodedPacket {
        private final byte[] header;
        private final byte[] body;

        private EncodedPacket(byte[] header, byte[] body) {
            this.header = header;
            this.body = body;
        }

        public byte[] header() {
            return header.clone();
        }

        public byte[] body() {
            return body.clone();
        }
    }
}
