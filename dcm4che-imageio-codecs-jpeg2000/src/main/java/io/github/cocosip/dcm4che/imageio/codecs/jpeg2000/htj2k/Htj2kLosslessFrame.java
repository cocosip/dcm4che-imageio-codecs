package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.IIOException;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodestreamWriter;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Dwt53;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Geometry;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Marker;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionIterator;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000SizeSegment;

/** Initial one-tile, one-component reversible HT frame profile. */
final class Htj2kLosslessFrame {
    private static final int LEVELS = 5;
    private static final int BLOCK_SIZE = 64;

    private Htj2kLosslessFrame() {
    }

    static byte[] encode(Jpeg2000Raster raster) throws IOException {
        if (raster == null) {
            throw new NullPointerException("raster");
        }
        if (raster.componentCount() != 1 || raster.precision() < 2
                || raster.precision() > 16) {
            throw new IIOException("HTJ2K initial lossless frame requires one 2..16-bit component");
        }
        Jpeg2000Limits limits = Jpeg2000Limits.defaults();
        byte[] qcd = Htj2kQuantizer.reversiblePayload(raster.precision(), 1, LEVELS);
        Jpeg2000Geometry.Tile tile = geometry(raster.width(), raster.height(), limits);
        int[] transformed = Jpeg2000Dwt53.forward(raster.levelShiftedComponent(0),
                raster.width(), raster.height(), LEVELS, 0, 0, limits);
        ByteArrayOutputStream[] parts = new ByteArrayOutputStream[LEVELS + 1];
        for (int r = 0; r < parts.length; r++) {
            parts[r] = new ByteArrayOutputStream();
        }
        for (Jpeg2000ProgressionIterator.PacketCoordinate coordinate :
                Jpeg2000ProgressionIterator.enumerate(Jpeg2000ProgressionOrder.RPCL,
                        1, tile, limits)) {
            Jpeg2000Geometry.Precinct precinct = tile.components().get(0)
                    .resolutions().get(coordinate.resolution()).precincts()
                    .get(coordinate.precinct());
            List<Htj2kPacketCodec.Band> bands = new ArrayList<Htj2kPacketCodec.Band>();
            for (Jpeg2000Geometry.PrecinctSubband band : precinct.subbands()) {
                List<Jpeg2000Geometry.CodeBlock> blocks = band.codeBlocks();
                if (blocks.isEmpty()) {
                    continue;
                }
                int kmax = Htj2kQuantizer.kmax(qcd, coordinate.resolution(),
                        band.orientation().ordinal());
                List<Htj2kPacketCodec.Contribution> contributions =
                        new ArrayList<Htj2kPacketCodec.Contribution>();
                for (Jpeg2000Geometry.CodeBlock block : blocks) {
                    int[] coefficients = readBlock(transformed, raster.width(), block);
                    boolean zero = true;
                    for (int coefficient : coefficients) {
                        zero &= coefficient == 0;
                    }
                    byte[] cleanup = zero ? new byte[0]
                            : Htj2kCleanupPassEncoder.encode(new Htj2kCodeBlock(
                                    (int) block.bounds().width(),
                                    (int) block.bounds().height(), kmax, coefficients));
                    contributions.add(new Htj2kPacketCodec.Contribution(
                            kmax - 1, cleanup));
                }
                int gridWidth = gridWidth(blocks);
                bands.add(new Htj2kPacketCodec.Band(gridWidth,
                        blocks.size() / gridWidth, contributions));
            }
            byte[] packet = Htj2kPacketCodec.encode(bands);
            parts[coordinate.resolution()].write(packet, 0, packet.length);
        }
        return writeCodestream(raster, qcd, parts, limits);
    }

    static Jpeg2000Raster decode(byte[] bytes, Htj2kFrameCodec codec) throws IOException {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        Jpeg2000Limits limits = Jpeg2000Limits.defaults();
        limits.requireFrameLength(bytes.length);
        Htj2kCodestream stream = codec.inspect(new MemoryCacheImageInputStream(
                new ByteArrayInputStream(bytes)), bytes.length, limits);
        Jpeg2000SizeSegment size = stream.size();
        if (size.tileCount() != 1 || size.components().size() != 1
                || !stream.reversible() || stream.multipleComponentTransform()) {
            throw new IIOException("HTJ2K initial decoder requires one reversible grayscale tile");
        }
        Jpeg2000SizeSegment.Component component = size.components().get(0);
        byte[] cod = stream.codingPayload();
        if (component.separationX() != 1 || component.separationY() != 1
                || cod[0] != 0 || (cod[5] & 0xff) != LEVELS
                || (cod[6] & 0xff) != 4 || (cod[7] & 0xff) != 4) {
            throw new IIOException("Unsupported HTJ2K grayscale geometry or packet markers");
        }
        int width = Math.toIntExact(size.referenceGridWidth() - size.imageOffsetX());
        int height = Math.toIntExact(size.referenceGridHeight() - size.imageOffsetY());
        limits.checkedSampleBufferBytes(width, height, 1, Integer.BYTES);
        Jpeg2000Geometry.Tile tile = Jpeg2000Geometry.create(
                size.imageOffsetX(), size.imageOffsetY(), size.referenceGridWidth(),
                size.referenceGridHeight(), size.tileOffsetX(), size.tileOffsetY(),
                size.tileWidth(), size.tileHeight(), 1, LEVELS, BLOCK_SIZE,
                BLOCK_SIZE, limits).tiles().get(0);
        byte[] qcd = stream.quantizationPayload();
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (Htj2kCodestream.TilePart part : stream.tileParts()) {
            joined.write(bytes, part.dataOffset, part.dataLength);
        }
        byte[] data = joined.toByteArray();
        int[] coefficients = new int[width * height];
        int position = 0;
        for (Jpeg2000ProgressionIterator.PacketCoordinate coordinate :
                Jpeg2000ProgressionIterator.enumerate(stream.progression(),
                        1, tile, limits)) {
            Jpeg2000Geometry.Precinct precinct = tile.components().get(0)
                    .resolutions().get(coordinate.resolution()).precincts()
                    .get(coordinate.precinct());
            List<Jpeg2000Geometry.PrecinctSubband> bands = nonemptyBands(precinct);
            List<int[]> grids = new ArrayList<int[]>();
            for (Jpeg2000Geometry.PrecinctSubband band : bands) {
                int gridWidth = gridWidth(band.codeBlocks());
                grids.add(new int[] {gridWidth, band.codeBlocks().size() / gridWidth});
            }
            Htj2kPacketCodec.Decoded packet = Htj2kPacketCodec.decodeNext(
                    data, position, data.length, grids);
            position += packet.bytesConsumed;
            for (int bandIndex = 0; bandIndex < bands.size(); bandIndex++) {
                Jpeg2000Geometry.PrecinctSubband band = bands.get(bandIndex);
                int kmax = Htj2kQuantizer.kmax(qcd, coordinate.resolution(),
                        band.orientation().ordinal());
                for (int blockIndex = 0; blockIndex < band.codeBlocks().size(); blockIndex++) {
                    Htj2kPacketCodec.Contribution contribution =
                            packet.bands.get(bandIndex).blocks.get(blockIndex);
                    if (contribution.data.length == 0) {
                        continue;
                    }
                    if (contribution.missingMsbs != kmax - 1) {
                        throw new IIOException("Unsupported HTJ2K cleanup bitplane count");
                    }
                    Jpeg2000Geometry.CodeBlock block = band.codeBlocks().get(blockIndex);
                    Htj2kCodeBlock decoded = Htj2kCleanupPassDecoder.decode(
                            contribution.data, (int) block.bounds().width(),
                            (int) block.bounds().height(), kmax);
                    writeBlock(coefficients, width, block, decoded);
                }
            }
        }
        if (position != data.length) {
            throw new IIOException("HTJ2K tile-part packet data has trailing bytes");
        }
        int[] samples = Jpeg2000Dwt53.inverse(coefficients, width, height,
                LEVELS, 0, 0, limits);
        if (!component.signed()) {
            int shift = 1 << (component.precision() - 1);
            for (int i = 0; i < samples.length; i++) {
                samples[i] += shift;
            }
        }
        return Jpeg2000Raster.of(width, height,
                component.precision() <= 8 ? 8 : 16, component.precision(),
                component.signed(), "MONOCHROME2", new int[][] {samples}, limits);
    }

    private static byte[] writeCodestream(Jpeg2000Raster raster, byte[] qcd,
            ByteArrayOutputStream[] parts, Jpeg2000Limits limits) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes);
        Jpeg2000CodestreamWriter writer = new Jpeg2000CodestreamWriter(output, limits);
        writer.writeStandalone(Jpeg2000Marker.SOC);
        writer.writeSegment(Jpeg2000Marker.SIZ, sizePayload(raster));
        int ccap = Htj2kQuantizer.reversibleCcap15(qcd);
        writer.writeSegment(Jpeg2000Marker.CAP, new byte[] {
                0, 2, 0, 0, (byte) (ccap >>> 8), (byte) ccap
        });
        writer.writeSegment(Jpeg2000Marker.COD, new byte[] {
                0, (byte) Jpeg2000ProgressionOrder.RPCL.code(), 0, 1, 0,
                LEVELS, 4, 4, 0x40, 1
        });
        writer.writeSegment(Jpeg2000Marker.QCD, qcd);
        byte[] tlm = new byte[2 + 4 * parts.length];
        tlm[1] = 0x40;
        for (int i = 0; i < parts.length; i++) {
            long length = 14L + parts[i].size();
            if (length > 0xffffffffL) {
                throw new IIOException("HTJ2K tile-part exceeds Psot");
            }
            int offset = 2 + 4 * i;
            tlm[offset] = (byte) (length >>> 24);
            tlm[offset + 1] = (byte) (length >>> 16);
            tlm[offset + 2] = (byte) (length >>> 8);
            tlm[offset + 3] = (byte) length;
        }
        writer.writeSegment(Jpeg2000Marker.TLM, tlm);
        for (int i = 0; i < parts.length; i++) {
            long length = 14L + parts[i].size();
            writer.writeSegment(Jpeg2000Marker.SOT, new byte[] {
                    0, 0, (byte) (length >>> 24), (byte) (length >>> 16),
                    (byte) (length >>> 8), (byte) length, (byte) i,
                    (byte) parts.length
            });
            writer.writeStandalone(Jpeg2000Marker.SOD);
            writer.writeRaw(parts[i].toByteArray());
        }
        writer.writeStandalone(Jpeg2000Marker.EOC);
        output.flush();
        limits.requireFrameLength(bytes.size());
        return bytes.toByteArray();
    }

    private static byte[] sizePayload(Jpeg2000Raster raster) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeShort(0x4000);
        output.writeInt(raster.width());
        output.writeInt(raster.height());
        output.writeInt(0);
        output.writeInt(0);
        output.writeInt(raster.width());
        output.writeInt(raster.height());
        output.writeInt(0);
        output.writeInt(0);
        output.writeShort(1);
        output.writeByte((raster.precision() - 1) | (raster.signed() ? 0x80 : 0));
        output.writeByte(1);
        output.writeByte(1);
        return bytes.toByteArray();
    }

    private static Jpeg2000Geometry.Tile geometry(int width, int height,
            Jpeg2000Limits limits) throws IOException {
        return Jpeg2000Geometry.create(0, 0, width, height, 0, 0,
                width, height, 1, LEVELS, BLOCK_SIZE, BLOCK_SIZE, limits)
                .tiles().get(0);
    }

    private static int gridWidth(List<Jpeg2000Geometry.CodeBlock> blocks)
            throws IIOException {
        long firstY = blocks.get(0).bounds().y0();
        int width = 0;
        for (Jpeg2000Geometry.CodeBlock block : blocks) {
            if (block.bounds().y0() != firstY) {
                break;
            }
            width++;
        }
        if (width == 0 || blocks.size() % width != 0) {
            throw new IIOException("HTJ2K precinct block grid is incomplete");
        }
        return width;
    }

    private static List<Jpeg2000Geometry.PrecinctSubband> nonemptyBands(
            Jpeg2000Geometry.Precinct precinct) {
        List<Jpeg2000Geometry.PrecinctSubband> result =
                new ArrayList<Jpeg2000Geometry.PrecinctSubband>();
        for (Jpeg2000Geometry.PrecinctSubband band : precinct.subbands()) {
            if (!band.codeBlocks().isEmpty()) {
                result.add(band);
            }
        }
        return result;
    }

    private static int[] readBlock(int[] coefficients, int stride,
            Jpeg2000Geometry.CodeBlock block) {
        int width = (int) block.bounds().width();
        int height = (int) block.bounds().height();
        int[] result = new int[width * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(coefficients, (block.offsetY() + y) * stride
                    + block.offsetX(), result, y * width, width);
        }
        return result;
    }

    private static void writeBlock(int[] coefficients, int stride,
            Jpeg2000Geometry.CodeBlock block, Htj2kCodeBlock decoded) {
        int width = decoded.width();
        for (int y = 0; y < decoded.height(); y++) {
            for (int x = 0; x < width; x++) {
                coefficients[(block.offsetY() + y) * stride + block.offsetX() + x] =
                        decoded.coefficient(x, y);
            }
        }
    }
}
