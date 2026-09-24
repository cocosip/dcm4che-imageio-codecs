package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;

/** Stateful inline packet decoder for one classic precinct subband. */
public final class Jpeg2000PacketDecoder {
    private final int gridWidth;
    private final Jpeg2000TagTree inclusion;
    private final Jpeg2000TagTree zeroBitPlanes;
    private final BlockState[] states;
    private int nextLayer;

    public Jpeg2000PacketDecoder(int gridWidth, int gridHeight) {
        if (gridWidth <= 0 || gridHeight <= 0
                || (long) gridWidth * gridHeight > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("JPEG 2000 packet code-block grid is invalid");
        }
        this.gridWidth = gridWidth;
        inclusion = new Jpeg2000TagTree(gridWidth, gridHeight);
        zeroBitPlanes = new Jpeg2000TagTree(gridWidth, gridHeight);
        states = new BlockState[gridWidth * gridHeight];
        for (int index = 0; index < states.length; index++) {
            states[index] = new BlockState();
        }
    }

    public List<DecodedContribution> decodeLayer(int layer, byte[] header, byte[] body)
            throws Jpeg2000Exception {
        if (layer != nextLayer) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 packet layer sequence expected " + nextLayer + " but was " + layer);
        }
        if (header == null || body == null) {
            throw new NullPointerException(header == null ? "header" : "body");
        }
        Jpeg2000PacketBitReader reader = new Jpeg2000PacketBitReader(header, 0, header.length);
        boolean present = reader.readBit() != 0;
        List<PendingContribution> pending = new ArrayList<PendingContribution>();
        if (present) {
            for (int index = 0; index < states.length; index++) {
                int x = index % gridWidth;
                int y = index / gridWidth;
                BlockState state = states[index];
                boolean contributes = state.included
                        ? reader.readBit() != 0
                        : inclusion.decode(reader, x, y, layer + 1);
                if (!contributes) {
                    continue;
                }
                boolean firstInclusion = !state.included;
                int zeroPlanes = state.zeroBitPlanes;
                int numLengthBits = state.numLengthBits;
                if (firstInclusion) {
                    zeroPlanes = zeroBitPlanes.decodeValue(reader, x, y);
                    numLengthBits = 3;
                }
                int passCount = decodePassCount(reader);
                int increment = 0;
                while (reader.readBit() != 0) {
                    if (++increment > 29) {
                        throw new Jpeg2000Exception("JPEG 2000 Lblock increment is too large");
                    }
                }
                numLengthBits += increment;
                int lengthBits = numLengthBits + Jpeg2000PacketEncoder.floorLog2(passCount);
                if (lengthBits > 32) {
                    throw new Jpeg2000Exception("JPEG 2000 packet length field exceeds 32 bits");
                }
                int byteLength = reader.readBits(lengthBits);
                if (byteLength < 0) {
                    throw new Jpeg2000Exception("JPEG 2000 packet contribution length is too large");
                }
                pending.add(new PendingContribution(
                        index, zeroPlanes, state.passCount, passCount,
                        byteLength, numLengthBits, firstInclusion));
            }
        }
        reader.align();
        if (reader.bytesRead() != header.length) {
            throw new Jpeg2000Exception("JPEG 2000 packet header has trailing bytes");
        }

        long requiredBodyLength = 0;
        for (PendingContribution contribution : pending) {
            requiredBodyLength += contribution.byteLength;
            if (requiredBodyLength > body.length) {
                throw new Jpeg2000Exception("Truncated JPEG 2000 packet body");
            }
        }
        if (requiredBodyLength != body.length) {
            throw new Jpeg2000Exception("JPEG 2000 packet body has trailing bytes");
        }

        List<DecodedContribution> decoded = new ArrayList<DecodedContribution>(pending.size());
        int bodyOffset = 0;
        for (PendingContribution contribution : pending) {
            BlockState state = states[contribution.blockIndex];
            if (contribution.firstInclusion) {
                state.included = true;
                state.zeroBitPlanes = contribution.zeroBitPlanes;
            }
            state.numLengthBits = contribution.numLengthBits;
            state.passCount += contribution.passCount;
            byte[] bytes = new byte[contribution.byteLength];
            System.arraycopy(body, bodyOffset, bytes, 0, bytes.length);
            bodyOffset += bytes.length;
            decoded.add(new DecodedContribution(
                    contribution.blockIndex,
                    contribution.zeroBitPlanes,
                    contribution.firstPass,
                    contribution.passCount,
                    bytes));
        }
        nextLayer++;
        return Collections.unmodifiableList(decoded);
    }

    static int decodePassCount(Jpeg2000PacketBitReader reader) throws Jpeg2000Exception {
        if (reader.readBit() == 0) {
            return 1;
        }
        if (reader.readBit() == 0) {
            return 2;
        }
        int value = reader.readBits(2);
        if (value != 3) {
            return 3 + value;
        }
        value = reader.readBits(5);
        if (value != 31) {
            return 6 + value;
        }
        return 37 + reader.readBits(7);
    }

    private static final class BlockState {
        private boolean included;
        private int zeroBitPlanes;
        private int passCount;
        private int numLengthBits;
    }

    private static final class PendingContribution {
        private final int blockIndex;
        private final int zeroBitPlanes;
        private final int firstPass;
        private final int passCount;
        private final int byteLength;
        private final int numLengthBits;
        private final boolean firstInclusion;

        private PendingContribution(
                int blockIndex,
                int zeroBitPlanes,
                int firstPass,
                int passCount,
                int byteLength,
                int numLengthBits,
                boolean firstInclusion) {
            this.blockIndex = blockIndex;
            this.zeroBitPlanes = zeroBitPlanes;
            this.firstPass = firstPass;
            this.passCount = passCount;
            this.byteLength = byteLength;
            this.numLengthBits = numLengthBits;
            this.firstInclusion = firstInclusion;
        }
    }

    public static final class DecodedContribution {
        private final int blockIndex;
        private final int zeroBitPlanes;
        private final int firstPass;
        private final int passCount;
        private final byte[] data;

        private DecodedContribution(
                int blockIndex,
                int zeroBitPlanes,
                int firstPass,
                int passCount,
                byte[] data) {
            this.blockIndex = blockIndex;
            this.zeroBitPlanes = zeroBitPlanes;
            this.firstPass = firstPass;
            this.passCount = passCount;
            this.data = data;
        }

        public int blockIndex() {
            return blockIndex;
        }

        public int zeroBitPlanes() {
            return zeroBitPlanes;
        }

        public int firstPass() {
            return firstPass;
        }

        public int passCount() {
            return passCount;
        }

        public byte[] data() {
            return data.clone();
        }
    }
}
