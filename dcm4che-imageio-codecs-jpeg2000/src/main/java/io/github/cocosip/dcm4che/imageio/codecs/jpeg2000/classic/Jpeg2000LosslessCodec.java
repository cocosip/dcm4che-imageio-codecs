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
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ComponentCodingStyleSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ComponentQuantizationSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Dwt53;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Dwt97;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Geometry;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Marker;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000MarkerSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
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
        return encode(raster, false, new double[] {0}, Jpeg2000ProgressionOrder.LRCP);
    }

    public static byte[] encode(Jpeg2000Raster raster, boolean irreversible) throws IOException {
        return encode(raster, irreversible, new double[] {0}, Jpeg2000ProgressionOrder.LRCP);
    }

    public static byte[] encode(Jpeg2000Raster raster, boolean irreversible,
            double[] layerRatios, Jpeg2000ProgressionOrder order) throws IOException {
        return encode(raster, irreversible, layerRatios, order, false, false);
    }

    static byte[] encode(Jpeg2000Raster raster, boolean irreversible,
            double[] layerRatios, Jpeg2000ProgressionOrder order,
            boolean sop, boolean eph) throws IOException {
        return encode(raster, irreversible, layerRatios, order, sop, eph, 0);
    }

    static byte[] encode(Jpeg2000Raster raster, boolean irreversible,
            double[] layerRatios, Jpeg2000ProgressionOrder order,
            boolean sop, boolean eph, int packedMode) throws IOException {
        return encode(raster, irreversible, layerRatios, order, sop, eph, packedMode, 0);
    }

    static byte[] encode(Jpeg2000Raster raster, boolean irreversible,
            double[] layerRatios, Jpeg2000ProgressionOrder order,
            boolean sop, boolean eph, int packedMode, int regionShift) throws IOException {
        if (raster == null) {
            throw new NullPointerException("raster");
        }
        if (layerRatios == null || layerRatios.length < 1 || layerRatios.length > 65535
                || order == null || packedMode < 0 || packedMode > 2
                || regionShift < 0 || regionShift > 8 || (regionShift != 0 && raster.componentCount() != 1)) {
            throw new IllegalArgumentException("JPEG 2000 layer profile is invalid");
        }
        Jpeg2000Limits limits = Jpeg2000Limits.defaults();
        int components = raster.componentCount();
        int exponent = raster.precision() + (components == 3 ? 1 : 0);
        byte[] siz = sizePayload(raster);
        byte[] cod = codingPayload(components == 3, irreversible,
                layerRatios.length, order, sop, eph);
        byte[] qcd = irreversible
                ? Jpeg2000Quantizer.expoundedPayload(raster.precision(), LEVELS, GUARD_BITS, 0)
                : quantizationPayload(exponent);
        Jpeg2000SizeSegment size = Jpeg2000SizeSegment.parse(
                new Jpeg2000MarkerSegment(Jpeg2000Marker.SIZ, 0, siz), limits);
        Jpeg2000CodingStyleSegment coding = Jpeg2000CodingStyleSegment.parse(
                new Jpeg2000MarkerSegment(Jpeg2000Marker.COD, 0, cod), limits);
        Jpeg2000Geometry.Tile tile = Jpeg2000Geometry.create(size, coding, limits).tiles().get(0);

        int[][] samples = new int[components][];
        for (int c = 0; c < components; c++) {
            samples[c] = raster.levelShiftedComponent(c);
        }
        if (components == 3 && !irreversible) {
            samples = Jpeg2000ComponentTransform.forwardReversible(
                    samples[0], samples[1], samples[2], limits);
        }
        int[][] transformed = new int[components][];
        if (irreversible) {
            double[][] color = components == 3
                    ? Jpeg2000ComponentTransform.forwardIrreversible(
                            samples[0], samples[1], samples[2], limits) : null;
            Jpeg2000QuantizationSegment quantization = Jpeg2000QuantizationSegment.parse(
                    new Jpeg2000MarkerSegment(Jpeg2000Marker.QCD, 0, qcd), coding);
            for (int c = 0; c < components; c++) {
                double[] source = color == null ? toDouble(samples[c]) : color[c];
                double[] wavelet = Jpeg2000Dwt97.forward(source, raster.width(),
                        raster.height(), LEVELS, 0, 0, limits);
                transformed[c] = quantizeTile(wavelet, raster.width(),
                        tile.components().get(c), quantization, raster.precision());
            }
        } else {
            for (int c = 0; c < components; c++) {
                transformed[c] = Jpeg2000Dwt53.forward(samples[c], raster.width(),
                        raster.height(), LEVELS, 0, 0, limits);
            }
        }
        if (regionShift != 0) {
            for (int i = 0; i < transformed[0].length; i++) {
                transformed[0][i] = Math.multiplyExact(transformed[0][i], 1 << regionShift);
            }
        }
        Map<String, BandPacket> packetBands = new HashMap<String, BandPacket>();
        List<Jpeg2000RateAllocator.Block> allocationBlocks =
                new ArrayList<Jpeg2000RateAllocator.Block>();
        List<BandAssignment> assignments = new ArrayList<BandAssignment>();
        for (int c = 0; c < components; c++) {
            Jpeg2000Geometry.Component component = tile.components().get(c);
            for (int r = 0; r < component.resolutions().size(); r++) {
                Jpeg2000Geometry.Resolution resolution = component.resolutions().get(r);
                for (int p = 0; p < resolution.precincts().size(); p++) {
                    Jpeg2000Geometry.Precinct precinct = resolution.precincts().get(p);
                    for (Jpeg2000Geometry.PrecinctSubband band : precinct.subbands()) {
                        BandPacket packet = new BandPacket(band.codeBlocks());
                        packetBands.put(bandKey(c, r, p, band.orientation().ordinal()), packet);
                        for (int i = 0; i < packet.blocks.size(); i++) {
                            Jpeg2000Geometry.CodeBlock block = packet.blocks.get(i);
                            int[] values = readBlock(transformed[c], raster.width(), block);
                            int orientation = band.orientation().ordinal();
                            Jpeg2000EbcotEncodedBlock encoded = new Jpeg2000EbcotEncoder(
                                    Math.toIntExact(block.bounds().width()),
                                    Math.toIntExact(block.bounds().height()), orientation, 0)
                                    .encode(values, 164);
                            packet.encoded.add(encoded);
                            int bitPlaneDepth = (irreversible ? raster.precision()
                                    : exponent + gain(orientation)) + regionShift;
                            int zeroPlanes = encoded.passes().isEmpty() ? 0
                                    : bitPlaneDepth + GUARD_BITS - 2 - encoded.maxBitPlane();
                            if (zeroPlanes < 0 || zeroPlanes > 63) {
                                throw new Jpeg2000Exception(
                                        "JPEG 2000 code-block exceeds quantization bit planes");
                            }
                            if (!encoded.passes().isEmpty()) {
                                packet.zeroPlanes.setValue(i % packet.gridWidth,
                                        i / packet.gridWidth, zeroPlanes);
                            }
                            allocationBlocks.add(new Jpeg2000RateAllocator.Block(encoded, values));
                            assignments.add(new BandAssignment(packet, i));
                        }
                    }
                }
            }
        }
        long uncompressedBytes = (long) raster.width() * raster.height()
                * components * raster.precision() / 8;
        int[][] targets = Jpeg2000RateAllocator.allocate(allocationBlocks,
                layerRatios, Math.max(1, uncompressedBytes));
        for (int i = 0; i < assignments.size(); i++) {
            BandAssignment assignment = assignments.get(i);
            BandPacket band = assignment.band;
            band.targets[assignment.index] = targets[i];
            int firstLayer = layerRatios.length;
            for (int layer = 0; layer < layerRatios.length; layer++) {
                if (targets[i][layer] > 0) {
                    firstLayer = layer;
                    break;
                }
            }
            band.inclusion.setValue(assignment.index % band.gridWidth,
                    assignment.index / band.gridWidth, firstLayer);
        }
        ByteArrayOutputStream tileBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream packedHeaders = packedMode == 0 ? null
                : new ByteArrayOutputStream();
        int packetSequence = 0;
        for (Jpeg2000ProgressionIterator.PacketCoordinate coordinate :
                Jpeg2000ProgressionIterator.enumerate(coding.progressionOrder(),
                        layerRatios.length, tile, limits)) {
            Jpeg2000Geometry.Component component = tile.components().get(coordinate.component());
            Jpeg2000Geometry.Resolution resolution =
                    component.resolutions().get(coordinate.resolution());
            Jpeg2000Geometry.Precinct precinct = resolution.precincts().get(coordinate.precinct());
            writePacket(tileBytes, packedHeaders, coordinate, precinct, packetBands,
                    packetSequence++, sop, eph);
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(bytes);
        Jpeg2000CodestreamWriter writer = new Jpeg2000CodestreamWriter(stream, limits);
        writer.writeStandalone(Jpeg2000Marker.SOC);
        writer.writeSegment(Jpeg2000Marker.SIZ, siz);
        writer.writeSegment(Jpeg2000Marker.COD, cod);
        writer.writeSegment(Jpeg2000Marker.QCD, qcd);
        if (regionShift != 0) {
            writer.writeSegment(Jpeg2000Marker.RGN, new byte[] {0, 0, (byte) regionShift});
        }
        byte[] headerData = packedHeaders == null ? new byte[0] : packedHeaders.toByteArray();
        if (packedMode == 2) {
            byte[] ppm = new byte[5 + headerData.length];
            int length = headerData.length;
            ppm[1] = (byte) (length >>> 24);
            ppm[2] = (byte) (length >>> 16);
            ppm[3] = (byte) (length >>> 8);
            ppm[4] = (byte) length;
            System.arraycopy(headerData, 0, ppm, 5, length);
            writer.writeSegment(Jpeg2000Marker.PPM, ppm);
        }
        byte[] data = tileBytes.toByteArray();
        byte[] ppt = null;
        if (packedMode == 1) {
            ppt = new byte[1 + headerData.length];
            System.arraycopy(headerData, 0, ppt, 1, headerData.length);
        }
        writer.writeSegment(Jpeg2000Marker.SOT,
                sotPayload(data.length + 14L + (ppt == null ? 0 : ppt.length + 4L)));
        if (ppt != null) {
            writer.writeSegment(Jpeg2000Marker.PPT, ppt);
        }
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
        if (size.capabilities() != 0) {
            throw new Jpeg2000Exception("JPEG 2000 SIZ capabilities are unsupported");
        }
        int count = size.components().size();
        if ((count != 1 && count != 3)
                || (count == 1 && coding.multipleComponentTransform())) {
            throw new Jpeg2000Exception("JPEG 2000 component count or RCT does not match the lossless profile");
        }
        List<Jpeg2000CodingStyleSegment> styles =
                new ArrayList<Jpeg2000CodingStyleSegment>(count);
        Jpeg2000QuantizationSegment[] quantizations = new Jpeg2000QuantizationSegment[count];
        for (int c = 0; c < count; c++) {
            styles.add(coding);
            quantizations[c] = quantization;
        }
        for (Jpeg2000ComponentCodingStyleSegment override : codestream.componentCodingStyles()) {
            styles.set(override.componentIndex(), override.resolve(coding, size, limits));
        }
        for (Jpeg2000ComponentQuantizationSegment override : codestream.componentQuantizations()) {
            quantizations[override.componentIndex()] = override.quantization();
        }
        for (int c = 0; c < count; c++) {
            Jpeg2000CodingStyleSegment style = styles.get(c);
            Jpeg2000QuantizationStyle quantizationStyle = quantizations[c].style();
            int expectedSubbands = 1 + 3 * style.decompositionLevels();
            if (quantizationStyle != Jpeg2000QuantizationStyle.SCALAR_DERIVED
                    && quantizations[c].stepSizes().length != expectedSubbands) {
                throw new Jpeg2000Exception("JPEG 2000 component " + c
                        + " quantization steps do not match its decomposition levels");
            }
            if ((style.codeBlockStyle() & ~Jpeg2000EbcotEncoder.SUPPORTED_CODE_BLOCK_STYLES) != 0
                    || (style.transformation() == 1
                            && quantizationStyle != Jpeg2000QuantizationStyle.NO_QUANTIZATION)
                    || (style.transformation() == 0
                            && quantizationStyle != Jpeg2000QuantizationStyle.SCALAR_EXPOUNDED)
                    || (coding.multipleComponentTransform()
                            && style.transformation() != coding.transformation())) {
                throw new Jpeg2000Exception("JPEG 2000 component " + c
                        + " uses an unsupported coding or quantization profile");
            }
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
        Jpeg2000Geometry.Image geometry = Jpeg2000Geometry.create(size, styles, limits);
        Map<Integer, ByteArrayOutputStream> tileData = new HashMap<Integer, ByteArrayOutputStream>();
        Map<Integer, ByteArrayOutputStream> tileHeaders =
                new HashMap<Integer, ByteArrayOutputStream>();
        for (Jpeg2000ClassicTilePart part : codestream.tileParts()) {
            int index = part.startOfTile().tileIndex();
            ByteArrayOutputStream joined = tileData.get(index);
            if (joined == null) {
                joined = new ByteArrayOutputStream();
                tileData.put(index, joined);
            }
            joined.write(part.data());
            ByteArrayOutputStream headers = tileHeaders.get(index);
            if (headers == null) {
                headers = new ByteArrayOutputStream();
                tileHeaders.put(index, headers);
            }
            headers.write(part.packedHeaders());
        }
        int[][] output = new int[count][width * height];
        for (Jpeg2000Geometry.Tile tile : geometry.tiles()) {
            int tileWidth = Math.toIntExact(tile.bounds().width());
            int tileHeight = Math.toIntExact(tile.bounds().height());
            int[][] coefficients = new int[count][tileWidth * tileHeight];
            byte[] data = tileData.get(tile.index()).toByteArray();
            byte[] packedHeaders = tileHeaders.get(tile.index()).toByteArray();
            PacketCursor cursor = new PacketCursor(0, 0);
            int packetSequence = 0;
            Map<String, BandPacket> packetBands = new HashMap<String, BandPacket>();
            for (Jpeg2000ProgressionIterator.PacketCoordinate coordinate :
                    Jpeg2000ProgressionChange.enumerate(codestream.progressionChanges(),
                            coding.progressionOrder(), coding.qualityLayers(), tile, limits)) {
                Jpeg2000Geometry.Resolution resolution = tile.components()
                        .get(coordinate.component()).resolutions().get(coordinate.resolution());
                Jpeg2000Geometry.Precinct precinct = resolution.precincts().get(coordinate.precinct());
                cursor = readPacket(data, packedHeaders, cursor, coordinate, precinct,
                        packetBands, quantizations[coordinate.component()], coding,
                        codestream.regionShift(coordinate.component()), packetSequence++);
            }
            if (cursor.bodyPosition != data.length
                    || (packedHeaders.length > 0
                            && cursor.headerPosition != packedHeaders.length)) {
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
                            band.orientation, styles.get(band.component).codeBlockStyle(),
                            block.maxBitPlane, payload, passes);
                    int[] values = Jpeg2000EbcotDecoder.decode(encoded, block.passCount);
                    if (styles.get(band.component).transformation() == 0) {
                        int lastPlane = block.maxBitPlane - (block.passCount + 1) / 3;
                        if (lastPlane > 0) {
                            int midpoint = 1 << (lastPlane - 1);
                            for (int value = 0; value < values.length; value++) {
                                if (values[value] != 0) {
                                    values[value] += values[value] < 0 ? -midpoint : midpoint;
                                }
                            }
                        }
                    }
                    int regionShift = codestream.regionShift(band.component);
                    if (regionShift != 0) {
                        long threshold = 1L << regionShift;
                        for (int value = 0; value < values.length; value++) {
                            if (Math.abs((long) values[value]) >= threshold) {
                                values[value] >>= regionShift;
                            }
                        }
                    }
                    writeBlock(coefficients[band.component], tileWidth, geometryBlock, values);
                }
            }
            int[][] tileSamples = new int[count][];
            double[][] reconstructed = new double[count][];
            for (int c = 0; c < count; c++) {
                Jpeg2000CodingStyleSegment style = styles.get(c);
                if (style.transformation() == 0) {
                    reconstructed[c] = Jpeg2000Dwt97.inverse(
                            dequantizeTile(coefficients[c], tileWidth,
                                    tile.components().get(c), quantizations[c], precision),
                            tileWidth, tileHeight, style.decompositionLevels(),
                            tile.bounds().x0(), tile.bounds().y0(), limits);
                    if (!coding.multipleComponentTransform()) {
                        tileSamples[c] = round(reconstructed[c]);
                    }
                } else {
                    tileSamples[c] = Jpeg2000Dwt53.inverse(coefficients[c], tileWidth,
                            tileHeight, style.decompositionLevels(), tile.bounds().x0(),
                            tile.bounds().y0(), limits);
                }
            }
            if (count == 3 && coding.multipleComponentTransform()) {
                tileSamples = coding.transformation() == 1
                        ? Jpeg2000ComponentTransform.inverseReversible(
                                tileSamples[0], tileSamples[1], tileSamples[2], limits)
                        : Jpeg2000ComponentTransform.inverseIrreversible(
                                reconstructed[0], reconstructed[1], reconstructed[2], limits);
            }
            int shift = signed ? 0 : 1 << (precision - 1);
            for (int y = 0; y < tileHeight; y++) {
                int destination = (Math.toIntExact(tile.bounds().y0() - size.imageOffsetY()) + y)
                        * width + Math.toIntExact(tile.bounds().x0() - size.imageOffsetX());
                for (int c = 0; c < count; c++) {
                    for (int x = 0; x < tileWidth; x++) {
                        int value = tileSamples[c][y * tileWidth + x] + shift;
                        output[c][destination + x] = styles.get(c).transformation() == 0
                                ? clamp(value, precision, signed) : value;
                    }
                }
            }
        }
        return Jpeg2000Raster.of(width, height, precision <= 8 ? 8 : 16,
                precision, signed, count == 1 ? "MONOCHROME2" : "RGB", output, limits);
    }

    private static void writePacket(ByteArrayOutputStream output,
            ByteArrayOutputStream packedHeaders,
            Jpeg2000ProgressionIterator.PacketCoordinate coordinate,
            Jpeg2000Geometry.Precinct precinct, Map<String, BandPacket> packetBands,
            int packetSequence, boolean sop, boolean eph)
            throws IOException {
        List<BandPacket> bands = new ArrayList<BandPacket>();
        boolean present = false;
        for (Jpeg2000Geometry.PrecinctSubband precinctBand : precinct.subbands()) {
            BandPacket band = packetBands.get(bandKey(coordinate.component(),
                    coordinate.resolution(), coordinate.precinct(),
                    precinctBand.orientation().ordinal()));
            bands.add(band);
            for (int i = 0; i < band.blocks.size(); i++) {
                if (band.targets[i][coordinate.layer()] > band.states[i].passCount) {
                    present = true;
                }
            }
        }
        Jpeg2000PacketBitWriter header = new Jpeg2000PacketBitWriter();
        header.writeBit(present ? 1 : 0);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (present) {
            for (BandPacket band : bands) {
                for (int i = 0; i < band.blocks.size(); i++) {
                    int x = i % band.gridWidth;
                    int y = i / band.gridWidth;
                    BlockState state = band.states[i];
                    Jpeg2000EbcotEncodedBlock encoded = band.encoded.get(i);
                    int target = band.targets[i][coordinate.layer()];
                    int passes = target - state.passCount;
                    if (!state.included) {
                        band.inclusion.encode(header, x, y, coordinate.layer() + 1);
                        if (passes == 0) {
                            continue;
                        }
                        band.zeroPlanes.encode(header, x, y, 64);
                        state.included = true;
                        state.lengthBits = 3;
                    } else {
                        header.writeBit(passes > 0 ? 1 : 0);
                        if (passes == 0) {
                            continue;
                        }
                    }
                    Jpeg2000PacketEncoder.encodePassCount(header, passes);
                    int length = encoded.passes().get(target - 1).byteLength() - state.byteLength;
                    int required = Math.max(3,
                            (length == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(length))
                                    - Jpeg2000PacketEncoder.floorLog2(passes));
                    while (state.lengthBits < required) {
                        header.writeBit(1);
                        state.lengthBits++;
                    }
                    header.writeBit(0);
                    header.writeBits(length,
                            state.lengthBits + Jpeg2000PacketEncoder.floorLog2(passes));
                    byte[] payload = encoded.data();
                    body.write(payload, state.byteLength, length);
                    state.passCount = target;
                    state.byteLength += length;
                }
            }
        }
        header.align();
        if (sop) {
            output.write(0xff);
            output.write(Jpeg2000Marker.SOP);
            output.write(0);
            output.write(4);
            output.write(packetSequence >>> 8);
            output.write(packetSequence);
        }
        ByteArrayOutputStream headerDestination = packedHeaders == null
                ? output : packedHeaders;
        headerDestination.write(header.toByteArray());
        if (eph) {
            headerDestination.write(0xff);
            headerDestination.write(Jpeg2000Marker.EPH);
        }
        output.write(body.toByteArray());
    }

    private static String bandKey(int component, int resolution, int precinct, int orientation) {
        return component + ":" + resolution + ":" + precinct + ":" + orientation;
    }

    private static PacketCursor readPacket(byte[] data, byte[] packedHeaders,
            PacketCursor cursor,
            Jpeg2000ProgressionIterator.PacketCoordinate coordinate,
            Jpeg2000Geometry.Precinct precinct, Map<String, BandPacket> packetBands,
            Jpeg2000QuantizationSegment quantization,
            Jpeg2000CodingStyleSegment coding, int regionShift,
            int packetSequence) throws Jpeg2000Exception {
        int position = cursor.bodyPosition;
        if (coding.hasStartOfPacketMarkers()) {
            if (data.length - position < 6 || (data[position] & 0xff) != 0xff
                    || (data[position + 1] & 0xff) != Jpeg2000Marker.SOP
                    || (data[position + 2] & 0xff) != 0
                    || (data[position + 3] & 0xff) != 4
                    || (((data[position + 4] & 0xff) << 8)
                            | (data[position + 5] & 0xff)) != (packetSequence & 0xffff)) {
                throw new Jpeg2000Exception("JPEG 2000 SOP marker or packet sequence is invalid");
            }
            position += 6;
        }
        boolean packed = packedHeaders.length > 0;
        byte[] headerData = packed ? packedHeaders : data;
        int headerOffset = packed ? cursor.headerPosition : position;
        Jpeg2000PacketBitReader header = new Jpeg2000PacketBitReader(
                headerData, headerOffset, headerData.length - headerOffset);
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
                        int exponent = quantization.style() == Jpeg2000QuantizationStyle.NO_QUANTIZATION
                                ? quantization.stepSizes()[stepIndex] >>> 3
                                : quantization.stepSizes()[stepIndex] >>> 11;
                        state.maxBitPlane = quantization.guardBits() + exponent - 2
                                + regionShift - zeroPlanes;
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
        int headerEnd = headerOffset + header.bytesRead();
        int bodyOffset = packed ? position : headerEnd;
        if (coding.hasEndOfPacketHeaderMarkers()) {
            if (headerData.length - headerEnd < 2
                    || (headerData[headerEnd] & 0xff) != 0xff
                    || (headerData[headerEnd + 1] & 0xff) != Jpeg2000Marker.EPH) {
                throw new Jpeg2000Exception("JPEG 2000 EPH marker is missing after packet header");
            }
            headerEnd += 2;
            if (!packed) {
                bodyOffset += 2;
            }
        }
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
        return new PacketCursor(bodyOffset, packed ? headerEnd : bodyOffset);
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

    private static double[] toDouble(int[] samples) {
        double[] result = new double[samples.length];
        for (int i = 0; i < samples.length; i++) {
            result[i] = samples[i];
        }
        return result;
    }

    private static int[] round(double[] samples) throws Jpeg2000Exception {
        int[] result = new int[samples.length];
        for (int i = 0; i < samples.length; i++) {
            if (!Double.isFinite(samples[i]) || samples[i] <= Integer.MIN_VALUE
                    || samples[i] >= Integer.MAX_VALUE) {
                throw new Jpeg2000Exception("JPEG 2000 inverse 9/7 sample exceeds Java range");
            }
            result[i] = (int) Math.round(samples[i]);
        }
        return result;
    }

    private static int clamp(int value, int precision, boolean signed) {
        int minimum = signed ? -(1 << (precision - 1)) : 0;
        int maximum = signed ? (1 << (precision - 1)) - 1 : (1 << precision) - 1;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int[] quantizeTile(double[] wavelet, int stride,
            Jpeg2000Geometry.Component component,
            Jpeg2000QuantizationSegment quantization, int precision) throws Jpeg2000Exception {
        int[] result = new int[wavelet.length];
        for (int resolution = 0; resolution < component.resolutions().size(); resolution++) {
            for (Jpeg2000Geometry.Precinct precinct : component.resolutions().get(resolution).precincts()) {
                for (Jpeg2000Geometry.PrecinctSubband band : precinct.subbands()) {
                    double step = Jpeg2000Quantizer.step(quantization,
                            stepIndex(resolution, band.orientation().ordinal()), precision);
                    for (Jpeg2000Geometry.CodeBlock block : band.codeBlocks()) {
                        for (int y = block.offsetY(); y < block.offsetY() + block.bounds().height(); y++) {
                            for (int x = block.offsetX(); x < block.offsetX() + block.bounds().width(); x++) {
                                int index = y * stride + x;
                                result[index] = Jpeg2000Quantizer.quantize(wavelet[index], step);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private static double[] dequantizeTile(int[] coefficients, int stride,
            Jpeg2000Geometry.Component component,
            Jpeg2000QuantizationSegment quantization, int precision) throws Jpeg2000Exception {
        double[] result = new double[coefficients.length];
        for (int resolution = 0; resolution < component.resolutions().size(); resolution++) {
            for (Jpeg2000Geometry.Precinct precinct : component.resolutions().get(resolution).precincts()) {
                for (Jpeg2000Geometry.PrecinctSubband band : precinct.subbands()) {
                    double step = Jpeg2000Quantizer.step(quantization,
                            stepIndex(resolution, band.orientation().ordinal()), precision);
                    for (Jpeg2000Geometry.CodeBlock block : band.codeBlocks()) {
                        for (int y = block.offsetY(); y < block.offsetY() + block.bounds().height(); y++) {
                            for (int x = block.offsetX(); x < block.offsetX() + block.bounds().width(); x++) {
                                int index = y * stride + x;
                                result[index] = Jpeg2000Quantizer.dequantize(coefficients[index], step);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private static int stepIndex(int resolution, int orientation) {
        return resolution == 0 ? 0 : 1 + 3 * (resolution - 1) + orientation - 1;
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

    private static byte[] codingPayload(boolean mct, boolean irreversible,
            int layers, Jpeg2000ProgressionOrder order, boolean sop, boolean eph) {
        return new byte[] {(byte) ((sop ? 2 : 0) | (eph ? 4 : 0)),
                (byte) order.code(), (byte) (layers >>> 8), (byte) layers,
                (byte) (mct ? 1 : 0),
                LEVELS, 4, 4, 0, (byte) (irreversible ? 0 : 1)};
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
        private final int[][] targets;
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
            targets = new int[blocks.size()][];
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
        private int byteLength;
    }

    private static final class PacketCursor {
        private final int bodyPosition;
        private final int headerPosition;

        private PacketCursor(int bodyPosition, int headerPosition) {
            this.bodyPosition = bodyPosition;
            this.headerPosition = headerPosition;
        }
    }

    private static final class BandAssignment {
        private final BandPacket band;
        private final int index;

        private BandAssignment(BandPacket band, int index) {
            this.band = band;
            this.index = index;
        }
    }
}
