package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodingStyleSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodestreamReader;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodestreamWriter;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ComponentTransform;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Dwt53;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Geometry;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Marker;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000MarkerSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionIterator;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000QuantizationSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000QuantizationStyle;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000SizeSegment;

/** One complete reversible JPEG 2000 frame, with an inline final lossless layer. */
public final class Jpeg2000LosslessCodec {
    private static final int LEVELS = 5;
    private static final int BLOCK_SIZE = 64;
    private static final int GUARD_BITS = 2;

    private Jpeg2000LosslessCodec() {
    }

    public static byte[] encode(Jpeg2000Raster raster) throws IOException {
        if (raster == null) {
            throw new NullPointerException("raster");
        }
        Jpeg2000Limits limits = Jpeg2000Limits.defaults();
        int components = raster.componentCount();
        int exponent = raster.precision() + (components == 3 ? 1 : 0);
        byte[] siz = sizePayload(raster);
        byte[] cod = codingPayload(components == 3);
        byte[] qcd = quantizationPayload(exponent);
        Jpeg2000SizeSegment size = Jpeg2000SizeSegment.parse(
                new Jpeg2000MarkerSegment(Jpeg2000Marker.SIZ, 0, siz), limits);
        Jpeg2000CodingStyleSegment coding = Jpeg2000CodingStyleSegment.parse(
                new Jpeg2000MarkerSegment(Jpeg2000Marker.COD, 0, cod), limits);
        Jpeg2000Geometry.Tile tile = Jpeg2000Geometry.create(size, coding, limits).tiles().get(0);

        int[][] samples = new int[components][];
        for (int c = 0; c < components; c++) {
            samples[c] = raster.levelShiftedComponent(c);
        }
        if (components == 3) {
            samples = Jpeg2000ComponentTransform.forwardReversible(
                    samples[0], samples[1], samples[2], limits);
        }
        int[][] transformed = new int[components][];
        for (int c = 0; c < components; c++) {
            transformed[c] = Jpeg2000Dwt53.forward(samples[c], raster.width(),
                    raster.height(), LEVELS, 0, 0, limits);
        }
        ByteArrayOutputStream tileBytes = new ByteArrayOutputStream();
        for (Jpeg2000ProgressionIterator.PacketCoordinate coordinate :
                Jpeg2000ProgressionIterator.enumerate(coding.progressionOrder(), 1, tile, limits)) {
            Jpeg2000Geometry.Component component = tile.components().get(coordinate.component());
            Jpeg2000Geometry.Resolution resolution =
                    component.resolutions().get(coordinate.resolution());
            Jpeg2000Geometry.Precinct precinct = resolution.precincts().get(coordinate.precinct());
            writePacket(tileBytes, precinct, transformed[coordinate.component()],
                    raster.width(), exponent);
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(bytes);
        Jpeg2000CodestreamWriter writer = new Jpeg2000CodestreamWriter(stream, limits);
        writer.writeStandalone(Jpeg2000Marker.SOC);
        writer.writeSegment(Jpeg2000Marker.SIZ, siz);
        writer.writeSegment(Jpeg2000Marker.COD, cod);
        writer.writeSegment(Jpeg2000Marker.QCD, qcd);
        byte[] data = tileBytes.toByteArray();
        writer.writeSegment(Jpeg2000Marker.SOT, sotPayload(data.length + 14L));
        writer.writeStandalone(Jpeg2000Marker.SOD);
        writer.writeRaw(data);
        writer.writeStandalone(Jpeg2000Marker.EOC);
        stream.flush();
        return bytes.toByteArray();
    }

    public static Jpeg2000Raster decode(byte[] bytes) throws IOException {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        Jpeg2000Limits limits = Jpeg2000Limits.defaults();
        limits.requireFrameLength(bytes.length);
        MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(bytes));
        Jpeg2000ClassicCodestream codestream = new Jpeg2000ClassicCodestreamParser(
                new Jpeg2000CodestreamReader(input, bytes.length, limits), limits).parse();
        int logicalLength = (int) codestream.logicalLength();
        if (bytes.length != logicalLength
                && (bytes.length != logicalLength + 1 || bytes[logicalLength] != 0)) {
            throw new Jpeg2000Exception("JPEG 2000 frame has bytes after EOC beyond DICOM padding");
        }
        Jpeg2000SizeSegment size = codestream.size();
        Jpeg2000CodingStyleSegment coding = codestream.codingStyle();
        Jpeg2000QuantizationSegment quantization = codestream.quantization();
        if (size.capabilities() != 0 || coding.transformation() != 1
                || coding.hasStartOfPacketMarkers()
                || coding.hasEndOfPacketHeaderMarkers() || coding.codeBlockStyle() != 0
                || !codestream.componentCodingStyles().isEmpty()
                || !codestream.componentQuantizations().isEmpty()
                || quantization.style() != Jpeg2000QuantizationStyle.NO_QUANTIZATION) {
            throw new Jpeg2000Exception("JPEG 2000 lossless frame uses an unsupported coding profile");
        }
        int count = size.components().size();
        if (count != 1 && count != 3 || coding.multipleComponentTransform() != (count == 3)) {
            throw new Jpeg2000Exception("JPEG 2000 component count or RCT does not match the lossless profile");
        }
        int precision = size.components().get(0).precision();
        boolean signed = size.components().get(0).signed();
        if (precision < 1 || precision > 16) {
            throw new Jpeg2000Exception("JPEG 2000 precision is outside the DICOM lossless profile");
        }
        for (Jpeg2000SizeSegment.Component component : size.components()) {
            if (component.precision() != precision || component.signed() != signed) {
                throw new Jpeg2000Exception("JPEG 2000 component precisions or signedness differ");
            }
        }
        int width = Math.toIntExact(size.referenceGridWidth() - size.imageOffsetX());
        int height = Math.toIntExact(size.referenceGridHeight() - size.imageOffsetY());
        limits.checkedSampleBufferBytes(width, height, count, Integer.BYTES);
        Jpeg2000Geometry.Image geometry = Jpeg2000Geometry.create(size, coding, limits);
        Map<Integer, ByteArrayOutputStream> tileData = new HashMap<Integer, ByteArrayOutputStream>();
        for (Jpeg2000ClassicTilePart part : codestream.tileParts()) {
            int index = part.startOfTile().tileIndex();
            ByteArrayOutputStream joined = tileData.get(index);
            if (joined == null) {
                joined = new ByteArrayOutputStream();
                tileData.put(index, joined);
            }
            joined.write(part.data());
        }
        int[][] output = new int[count][width * height];
        for (Jpeg2000Geometry.Tile tile : geometry.tiles()) {
            int tileWidth = Math.toIntExact(tile.bounds().width());
            int tileHeight = Math.toIntExact(tile.bounds().height());
            int[][] coefficients = new int[count][tileWidth * tileHeight];
            byte[] data = tileData.get(tile.index()).toByteArray();
            int position = 0;
            Map<String, BandPacket> packetBands = new HashMap<String, BandPacket>();
            for (Jpeg2000ProgressionIterator.PacketCoordinate coordinate :
                    Jpeg2000ProgressionIterator.enumerate(coding.progressionOrder(),
                            coding.qualityLayers(), tile, limits)) {
                Jpeg2000Geometry.Resolution resolution = tile.components()
                        .get(coordinate.component()).resolutions().get(coordinate.resolution());
                Jpeg2000Geometry.Precinct precinct = resolution.precincts().get(coordinate.precinct());
                position = readPacket(data, position, coordinate, precinct,
                        packetBands, quantization);
            }
            if (position != data.length) {
                throw new Jpeg2000Exception("JPEG 2000 tile " + tile.index()
                        + " contains bytes after its final packet");
            }
            for (BandPacket band : packetBands.values()) {
                for (int i = 0; i < band.blocks.size(); i++) {
                    BlockState block = band.states[i];
                    if (block.passCount == 0) {
                        continue;
                    }
                    Jpeg2000Geometry.CodeBlock geometryBlock = band.blocks.get(i);
                    byte[] payload = block.data.toByteArray();
                    List<Jpeg2000EbcotPass> passes = new ArrayList<Jpeg2000EbcotPass>();
                    int bitPlane = block.maxBitPlane;
                    int passType = 2;
                    for (int p = 0; p < block.passCount; p++) {
                        Jpeg2000EbcotPass.Type type = passType == 0
                                ? Jpeg2000EbcotPass.Type.SIGNIFICANCE_PROPAGATION
                                : passType == 1 ? Jpeg2000EbcotPass.Type.MAGNITUDE_REFINEMENT
                                : Jpeg2000EbcotPass.Type.CLEANUP;
                        passes.add(new Jpeg2000EbcotPass(type, bitPlane, payload.length));
                        if (passType == 2) {
                            passType = 0;
                            bitPlane--;
                        } else {
                            passType++;
                        }
                    }
                    Jpeg2000EbcotEncodedBlock encoded = new Jpeg2000EbcotEncodedBlock(
                            Math.toIntExact(geometryBlock.bounds().width()),
                            Math.toIntExact(geometryBlock.bounds().height()),
                            band.orientation, 0, block.maxBitPlane, payload, passes);
                    int[] values = Jpeg2000EbcotDecoder.decode(encoded, block.passCount);
                    writeBlock(coefficients[band.component], tileWidth, geometryBlock, values);
                }
            }
            int[][] tileSamples = new int[count][];
            for (int c = 0; c < count; c++) {
                tileSamples[c] = Jpeg2000Dwt53.inverse(coefficients[c], tileWidth,
                        tileHeight, coding.decompositionLevels(), tile.bounds().x0(),
                        tile.bounds().y0(), limits);
            }
            if (count == 3) {
                tileSamples = Jpeg2000ComponentTransform.inverseReversible(
                        tileSamples[0], tileSamples[1], tileSamples[2], limits);
            }
            int shift = signed ? 0 : 1 << (precision - 1);
            for (int y = 0; y < tileHeight; y++) {
                int destination = (Math.toIntExact(tile.bounds().y0() - size.imageOffsetY()) + y)
                        * width + Math.toIntExact(tile.bounds().x0() - size.imageOffsetX());
                for (int c = 0; c < count; c++) {
                    for (int x = 0; x < tileWidth; x++) {
                        output[c][destination + x] = tileSamples[c][y * tileWidth + x] + shift;
                    }
                }
            }
        }
        return Jpeg2000Raster.of(width, height, precision <= 8 ? 8 : 16,
                precision, signed, count == 1 ? "MONOCHROME2" : "RGB", output, limits);
    }

    private static void writePacket(ByteArrayOutputStream output,
            Jpeg2000Geometry.Precinct precinct,
            int[] coefficients, int stride, int exponent) throws IOException {
        List<BandPacket> bands = new ArrayList<BandPacket>();
        boolean present = false;
        for (Jpeg2000Geometry.PrecinctSubband precinctBand : precinct.subbands()) {
            BandPacket packet = new BandPacket(precinctBand.codeBlocks());
            for (int i = 0; i < packet.blocks.size(); i++) {
                Jpeg2000Geometry.CodeBlock block = packet.blocks.get(i);
                int[] values = readBlock(coefficients, stride, block);
                int orientation = precinctBand.orientation().ordinal();
                Jpeg2000EbcotEncodedBlock encoded = new Jpeg2000EbcotEncoder(
                        Math.toIntExact(block.bounds().width()),
                        Math.toIntExact(block.bounds().height()), orientation, 0)
                        .encode(values, 164);
                packet.encoded.add(encoded);
                if (!encoded.passes().isEmpty()) {
                    present = true;
                    int zeroPlanes = exponent + gain(orientation) + GUARD_BITS
                            - 2 - encoded.maxBitPlane();
                    if (zeroPlanes < 0 || zeroPlanes > 63) {
                        throw new Jpeg2000Exception("JPEG 2000 code-block exceeds quantization bit planes");
                    }
                    packet.inclusion.setValue(i % packet.gridWidth, i / packet.gridWidth, 0);
                    packet.zeroPlanes.setValue(i % packet.gridWidth, i / packet.gridWidth, zeroPlanes);
                } else {
                    packet.inclusion.setValue(i % packet.gridWidth, i / packet.gridWidth, 1);
                }
            }
            bands.add(packet);
        }
        Jpeg2000PacketBitWriter header = new Jpeg2000PacketBitWriter();
        header.writeBit(present ? 1 : 0);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (present) {
            for (BandPacket band : bands) {
                for (int i = 0; i < band.blocks.size(); i++) {
                    int x = i % band.gridWidth;
                    int y = i / band.gridWidth;
                    Jpeg2000EbcotEncodedBlock encoded = band.encoded.get(i);
                    band.inclusion.encode(header, x, y, 1);
                    if (encoded.passes().isEmpty()) {
                        continue;
                    }
                    band.zeroPlanes.encode(header, x, y, 64);
                    int passes = encoded.passes().size();
                    Jpeg2000PacketEncoder.encodePassCount(header, passes);
                    int length = encoded.data().length;
                    int lengthBits = Math.max(3,
                            (length == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(length))
                                    - Jpeg2000PacketEncoder.floorLog2(passes));
                    for (int increment = 3; increment < lengthBits; increment++) {
                        header.writeBit(1);
                    }
                    header.writeBit(0);
                    header.writeBits(length, lengthBits + Jpeg2000PacketEncoder.floorLog2(passes));
                    body.write(encoded.data());
                }
            }
        }
        header.align();
        output.write(header.toByteArray());
        output.write(body.toByteArray());
    }

    private static int readPacket(byte[] data, int position,
            Jpeg2000ProgressionIterator.PacketCoordinate coordinate,
            Jpeg2000Geometry.Precinct precinct, Map<String, BandPacket> packetBands,
            Jpeg2000QuantizationSegment quantization) throws Jpeg2000Exception {
        Jpeg2000PacketBitReader header = new Jpeg2000PacketBitReader(
                data, position, data.length - position);
        boolean present = header.readBit() != 0;
        List<PendingBlock> pending = new ArrayList<PendingBlock>();
        if (present) {
            for (Jpeg2000Geometry.PrecinctSubband precinctBand : precinct.subbands()) {
                String key = coordinate.component() + ":" + coordinate.resolution() + ":"
                        + coordinate.precinct() + ":" + precinctBand.orientation().ordinal();
                BandPacket band = packetBands.get(key);
                if (band == null) {
                    band = new BandPacket(precinctBand.codeBlocks(), coordinate.component(),
                            precinctBand.orientation().ordinal());
                    packetBands.put(key, band);
                }
                for (int i = 0; i < band.blocks.size(); i++) {
                    int x = i % band.gridWidth;
                    int y = i / band.gridWidth;
                    BlockState state = band.states[i];
                    boolean included = state.included
                            ? header.readBit() != 0
                            : band.inclusion.decode(header, x, y, coordinate.layer() + 1);
                    if (!included) {
                        continue;
                    }
                    if (!state.included) {
                        int zeroPlanes = band.zeroPlanes.decodeValue(header, x, y);
                        int stepIndex = coordinate.resolution() == 0 ? 0
                                : 1 + 3 * (coordinate.resolution() - 1)
                                        + precinctBand.orientation().ordinal() - 1;
                        int exponent = quantization.stepSizes()[stepIndex] >>> 3;
                        state.maxBitPlane = quantization.guardBits() + exponent - 2 - zeroPlanes;
                        if (state.maxBitPlane < 0 || state.maxBitPlane > 30) {
                            throw new Jpeg2000Exception(
                                    "JPEG 2000 code-block bit-plane metadata is invalid");
                        }
                        state.included = true;
                        state.lengthBits = 3;
                    }
                    int passes = Jpeg2000PacketDecoder.decodePassCount(header);
                    int lengthBits = state.lengthBits;
                    while (header.readBit() != 0) {
                        if (++lengthBits > 32) {
                            throw new Jpeg2000Exception("JPEG 2000 Lblock exceeds 32 bits");
                        }
                    }
                    lengthBits += Jpeg2000PacketEncoder.floorLog2(passes);
                    if (lengthBits > 32) {
                        throw new Jpeg2000Exception("JPEG 2000 packet length field exceeds 32 bits");
                    }
                    int length = header.readBits(lengthBits);
                    if (length < 0) {
                        throw new Jpeg2000Exception("JPEG 2000 packet body length overflows Java limits");
                    }
                    state.lengthBits = lengthBits - Jpeg2000PacketEncoder.floorLog2(passes);
                    pending.add(new PendingBlock(state, passes, length));
                }
            }
        }
        header.align();
        int bodyOffset = position + header.bytesRead();
        for (PendingBlock block : pending) {
            if (block.length > data.length - bodyOffset) {
                throw new Jpeg2000Exception("Truncated JPEG 2000 packet body");
            }
            block.state.data.write(data, bodyOffset, block.length);
            block.state.passCount += block.passes;
            if (block.state.passCount > 164) {
                throw new Jpeg2000Exception("JPEG 2000 code-block pass count exceeds 164");
            }
            bodyOffset += block.length;
        }
        return bodyOffset;
    }

    private static int[] readBlock(int[] coefficients, int stride,
            Jpeg2000Geometry.CodeBlock block) {
        int width = Math.toIntExact(block.bounds().width());
        int height = Math.toIntExact(block.bounds().height());
        int[] result = new int[width * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(coefficients, (block.offsetY() + y) * stride + block.offsetX(),
                    result, y * width, width);
        }
        return result;
    }

    private static void writeBlock(int[] coefficients, int stride,
            Jpeg2000Geometry.CodeBlock block, int[] values) {
        int width = Math.toIntExact(block.bounds().width());
        int height = Math.toIntExact(block.bounds().height());
        for (int y = 0; y < height; y++) {
            System.arraycopy(values, y * width, coefficients,
                    (block.offsetY() + y) * stride + block.offsetX(), width);
        }
    }

    private static int gain(int orientation) {
        return orientation == 0 ? 0 : orientation == 3 ? 2 : 1;
    }

    private static byte[] sizePayload(Jpeg2000Raster raster) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeShort(0);
        output.writeInt(raster.width());
        output.writeInt(raster.height());
        output.writeInt(0);
        output.writeInt(0);
        output.writeInt(raster.width());
        output.writeInt(raster.height());
        output.writeInt(0);
        output.writeInt(0);
        output.writeShort(raster.componentCount());
        for (int c = 0; c < raster.componentCount(); c++) {
            output.writeByte((raster.precision() - 1) | (raster.signed() ? 0x80 : 0));
            output.writeByte(1);
            output.writeByte(1);
        }
        return bytes.toByteArray();
    }

    private static byte[] codingPayload(boolean rct) {
        return new byte[] {0, 0, 0, 1, (byte) (rct ? 1 : 0),
                LEVELS, 4, 4, 0, 1};
    }

    private static byte[] quantizationPayload(int exponent) throws Jpeg2000Exception {
        byte[] payload = new byte[2 + 3 * LEVELS];
        payload[0] = (byte) (GUARD_BITS << 5);
        for (int i = 0; i < payload.length - 1; i++) {
            int gain = i == 0 ? 0 : (i - 1) % 3 == 2 ? 2 : 1;
            int stepExponent = exponent + gain;
            if (stepExponent > 31) {
                throw new Jpeg2000Exception("JPEG 2000 quantization exponent exceeds 31");
            }
            payload[i + 1] = (byte) (stepExponent << 3);
        }
        return payload;
    }

    private static byte[] sotPayload(long length) throws Jpeg2000Exception {
        if (length > 0xffffffffL) {
            throw new Jpeg2000Exception("JPEG 2000 tile-part exceeds Psot limit");
        }
        return new byte[] {0, 0, (byte) (length >>> 24), (byte) (length >>> 16),
                (byte) (length >>> 8), (byte) length, 0, 1};
    }

    private static final class BandPacket {
        private final List<Jpeg2000Geometry.CodeBlock> blocks;
        private final int gridWidth;
        private final int component;
        private final int orientation;
        private final Jpeg2000TagTree inclusion;
        private final Jpeg2000TagTree zeroPlanes;
        private final BlockState[] states;
        private final List<Jpeg2000EbcotEncodedBlock> encoded =
                new ArrayList<Jpeg2000EbcotEncodedBlock>();

        private BandPacket(List<Jpeg2000Geometry.CodeBlock> blocks) {
            this(blocks, 0, 0);
        }

        private BandPacket(List<Jpeg2000Geometry.CodeBlock> blocks,
                int component, int orientation) {
            this.blocks = blocks;
            this.component = component;
            this.orientation = orientation;
            states = new BlockState[blocks.size()];
            for (int i = 0; i < states.length; i++) {
                states[i] = new BlockState();
            }
            if (blocks.isEmpty()) {
                gridWidth = 0;
                inclusion = null;
                zeroPlanes = null;
                return;
            }
            long firstY = blocks.get(0).bounds().y0();
            int width = 0;
            for (Jpeg2000Geometry.CodeBlock block : blocks) {
                if (block.bounds().y0() != firstY) {
                    break;
                }
                width++;
            }
            if (width == 0 || blocks.size() % width != 0) {
                throw new IllegalArgumentException("JPEG 2000 precinct code-block grid is incomplete");
            }
            gridWidth = width;
            inclusion = new Jpeg2000TagTree(width, blocks.size() / width);
            zeroPlanes = new Jpeg2000TagTree(width, blocks.size() / width);
        }
    }

    private static final class PendingBlock {
        private final BlockState state;
        private final int passes;
        private final int length;

        private PendingBlock(BlockState state, int passes, int length) {
            this.state = state;
            this.passes = passes;
            this.length = length;
        }
    }

    private static final class BlockState {
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        private boolean included;
        private int maxBitPlane;
        private int lengthBits;
        private int passCount;
    }
}
