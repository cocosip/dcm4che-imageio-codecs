package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodingStyleSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodestreamReader;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CommentSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ComponentCodingStyleSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ComponentQuantizationSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Marker;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000MarkerSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000QuantizationSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000SizeSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000StartOfTileSegment;

public final class Jpeg2000ClassicCodestreamParser {
    private static final byte[] JP2_SIGNATURE = new byte[] {
            0, 0, 0, 12, 0x6a, 0x50, 0x20, 0x20, 0x0d, 0x0a, (byte) 0x87, 0x0a
    };

    private final Jpeg2000CodestreamReader reader;
    private final Jpeg2000Limits limits;

    public Jpeg2000ClassicCodestreamParser(
            Jpeg2000CodestreamReader reader,
            Jpeg2000Limits limits) {
        if (reader == null) {
            throw new NullPointerException("reader");
        }
        if (limits == null) {
            throw new NullPointerException("limits");
        }
        this.reader = reader;
        this.limits = limits;
    }

    public Jpeg2000ClassicCodestream parse() throws IOException {
        if (reader.startsWith(JP2_SIGNATURE)) {
            throw new Jpeg2000Exception("JPEG 2000 JP2 wrapper is unsupported; a raw J2K codestream is required");
        }
        Jpeg2000MarkerSegment first;
        try {
            first = reader.readNext();
        } catch (Jpeg2000Exception error) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 raw codestream SOC marker was not found at offset 0",
                    error);
        }
        if (first.offset() != 0 || first.marker() != Jpeg2000Marker.SOC) {
            throw new Jpeg2000Exception("JPEG 2000 raw codestream must start with SOC at offset 0");
        }

        Jpeg2000SizeSegment size = null;
        Jpeg2000CodingStyleSegment coding = null;
        Jpeg2000QuantizationSegment quantization = null;
        List<Jpeg2000ComponentCodingStyleSegment> componentCoding =
                new ArrayList<Jpeg2000ComponentCodingStyleSegment>();
        List<Jpeg2000ComponentQuantizationSegment> componentQuantization =
                new ArrayList<Jpeg2000ComponentQuantizationSegment>();
        List<Jpeg2000MarkerSegment> deferredQcc = new ArrayList<Jpeg2000MarkerSegment>();
        List<Jpeg2000CommentSegment> comments = new ArrayList<Jpeg2000CommentSegment>();
        List<Jpeg2000ProgressionChange> progressionChanges =
                new ArrayList<Jpeg2000ProgressionChange>();
        ByteArrayOutputStream ppm = new ByteArrayOutputStream();
        int nextPpmIndex = 0;
        int nextTlmIndex = 0;
        List<TileLength> tileLengths = new ArrayList<TileLength>();
        Set<Integer> codingComponents = new HashSet<Integer>();
        Set<Integer> quantizationComponents = new HashSet<Integer>();
        Map<Integer, Integer> regionShifts = new HashMap<Integer, Integer>();
        Jpeg2000MarkerSegment startSegment = null;

        while (startSegment == null) {
            Jpeg2000MarkerSegment segment = reader.readNext();
            int marker = segment.marker();
            if (marker == Jpeg2000Marker.SIZ) {
                if (size != null || coding != null || quantization != null || !comments.isEmpty()) {
                    throw markerError(segment, "SIZ must occur once immediately after SOC");
                }
                size = Jpeg2000SizeSegment.parse(segment, limits);
            } else if (marker == Jpeg2000Marker.COD) {
                requireSize(size, segment);
                if (coding != null) {
                    throw markerError(segment, "duplicate COD marker");
                }
                coding = Jpeg2000CodingStyleSegment.parse(segment, limits);
            } else if (marker == Jpeg2000Marker.COC) {
                requireSize(size, segment);
                Jpeg2000ComponentCodingStyleSegment parsed =
                        Jpeg2000ComponentCodingStyleSegment.parse(segment, size);
                if (!codingComponents.add(parsed.componentIndex())) {
                    throw markerError(segment, "duplicate COC component override");
                }
                componentCoding.add(parsed);
            } else if (marker == Jpeg2000Marker.QCD) {
                requireSize(size, segment);
                if (coding == null) {
                    throw markerError(segment, "QCD marker requires COD first");
                }
                if (quantization != null) {
                    throw markerError(segment, "duplicate QCD marker");
                }
                quantization = Jpeg2000QuantizationSegment.parse(segment, coding);
            } else if (marker == Jpeg2000Marker.QCC) {
                requireSize(size, segment);
                if (coding == null) {
                    throw markerError(segment, "QCC marker requires COD first");
                }
                deferredQcc.add(segment);
            } else if (marker == Jpeg2000Marker.RGN) {
                requireSize(size, segment);
                byte[] payload = segment.payload();
                int indexBytes = size.components().size() < 257 ? 1 : 2;
                if (payload.length != indexBytes + 2) {
                    throw markerError(segment, "RGN payload length is invalid");
                }
                int component = indexBytes == 1 ? payload[0] & 0xff
                        : ((payload[0] & 0xff) << 8) | (payload[1] & 0xff);
                if (component >= size.components().size() || (payload[indexBytes] & 0xff) != 0
                        || (payload[indexBytes + 1] & 0xff) > 30
                        || regionShifts.put(component, payload[indexBytes + 1] & 0xff) != null) {
                    throw markerError(segment, "RGN Maxshift component, style, shift, or duplicate is invalid");
                }
            } else if (marker == Jpeg2000Marker.COM) {
                requireSize(size, segment);
                comments.add(Jpeg2000CommentSegment.parse(segment));
            } else if (marker == Jpeg2000Marker.POC) {
                requireSize(size, segment);
                if (coding == null) {
                    throw markerError(segment, "POC marker requires COD first");
                }
                progressionChanges.addAll(Jpeg2000ProgressionChange.parse(segment.payload(),
                        size.components().size(), coding.decompositionLevels() + 1,
                        coding.qualityLayers()));
            } else if (marker == Jpeg2000Marker.PPM) {
                requireSize(size, segment);
                byte[] payload = segment.payload();
                if (payload.length < 2 || (payload[0] & 0xff) != nextPpmIndex++) {
                    throw markerError(segment, "PPM segment index or payload is invalid");
                }
                ppm.write(payload, 1, payload.length - 1);
                limits.requireFrameLength(ppm.size());
            } else if (marker == Jpeg2000Marker.TLM) {
                requireSize(size, segment);
                if ((segment.payload().length < 2)
                        || (segment.payload()[0] & 0xff) != nextTlmIndex++) {
                    throw markerError(segment, "TLM segment index or payload is invalid");
                }
                byte[] payload = segment.payload();
                int stlm = payload[1] & 0xff;
                int indexBytes = (stlm >>> 4) & 3;
                int lengthBytes = ((stlm >>> 6) & 1) == 0 ? 2 : 4;
                if ((stlm & 0x8f) != 0 || indexBytes == 3
                        || (payload.length - 2) % (indexBytes + lengthBytes) != 0) {
                    throw markerError(segment, "TLM field sizes or entry length are invalid");
                }
                for (int offset = 2; offset < payload.length; offset += indexBytes + lengthBytes) {
                    int tile = -1;
                    if (indexBytes == 1) {
                        tile = payload[offset] & 0xff;
                    } else if (indexBytes == 2) {
                        tile = ((payload[offset] & 0xff) << 8) | (payload[offset + 1] & 0xff);
                    }
                    long length = 0;
                    for (int i = 0; i < lengthBytes; i++) {
                        length = (length << 8) | (payload[offset + indexBytes + i] & 0xff);
                    }
                    if ((tile >= size.tileCount()) || length < 14) {
                        throw markerError(segment, "TLM tile index or tile-part length is invalid");
                    }
                    tileLengths.add(new TileLength(tile, length));
                    if (tileLengths.size() > 1_000_000) {
                        throw markerError(segment, "TLM entry count exceeds the decoder limit");
                    }
                }
            } else if (marker == Jpeg2000Marker.SOT) {
                requireRequiredHeader(size, coding, quantization);
                startSegment = segment;
            } else if (marker == Jpeg2000Marker.EOC) {
                requireRequiredHeader(size, coding, quantization);
                throw markerError(segment, "EOC occurred before SOT/SOD tile data");
            } else {
                String kind = Jpeg2000Marker.isKnown(marker)
                        ? "unsupported classic marker " + Jpeg2000Marker.name(marker)
                        : "unknown semantic marker " + Jpeg2000Marker.name(marker);
                throw markerError(segment, kind);
            }
        }

        int[] shifts = new int[size.components().size()];
        for (Map.Entry<Integer, Integer> region : regionShifts.entrySet()) {
            shifts[region.getKey()] = region.getValue();
        }

        for (Jpeg2000MarkerSegment segment : deferredQcc) {
            byte[] payload = segment.payload();
            if (payload.length < (size.components().size() < 257 ? 1 : 2)) {
                throw markerError(segment, "QCC component index is truncated");
            }
            int component = size.components().size() < 257 ? payload[0] & 0xff
                    : ((payload[0] & 0xff) << 8) | (payload[1] & 0xff);
            Jpeg2000CodingStyleSegment resolved = coding;
            for (Jpeg2000ComponentCodingStyleSegment override : componentCoding) {
                if (override.componentIndex() == component) {
                    resolved = override.resolve(coding, size, limits);
                    break;
                }
            }
            Jpeg2000ComponentQuantizationSegment parsed =
                    Jpeg2000ComponentQuantizationSegment.parse(segment, size, resolved);
            if (!quantizationComponents.add(parsed.componentIndex())) {
                throw markerError(segment, "duplicate QCC component override");
            }
            componentQuantization.add(parsed);
        }

        List<Jpeg2000ClassicTilePart> tileParts = new ArrayList<Jpeg2000ClassicTilePart>();
        Map<Integer, TilePartState> tileStates = new HashMap<Integer, TilePartState>();
        Jpeg2000MarkerSegment current = startSegment;
        while (current.marker() == Jpeg2000Marker.SOT) {
            Jpeg2000StartOfTileSegment startOfTile = Jpeg2000StartOfTileSegment.parse(current, size);
            if (!tileLengths.isEmpty()) {
                if (tileParts.size() >= tileLengths.size()) {
                    throw markerError(current, "SOT has no matching TLM entry");
                }
                TileLength expected = tileLengths.get(tileParts.size());
                if ((expected.tileIndex >= 0 && expected.tileIndex != startOfTile.tileIndex())
                        || expected.length != startOfTile.tilePartLength()) {
                    throw markerError(current, "SOT tile index or Psot differs from TLM");
                }
            }
            validateTilePartOrder(current, startOfTile, tileStates);
            if (startOfTile.tilePartLength() == 0) {
                throw markerError(current, "Psot=0 is unsupported because the tile-part boundary is ambiguous");
            }

            ByteArrayOutputStream ppt = new ByteArrayOutputStream();
            int nextPptIndex = 0;
            Jpeg2000MarkerSegment sod = reader.readNext();
            while (sod.marker() == Jpeg2000Marker.PPT) {
                byte[] payload = sod.payload();
                if (payload.length < 2 || (payload[0] & 0xff) != nextPptIndex++) {
                    throw markerError(sod, "PPT segment index or payload is invalid");
                }
                ppt.write(payload, 1, payload.length - 1);
                limits.requireFrameLength(ppt.size());
                sod = reader.readNext();
            }
            if (sod.marker() != Jpeg2000Marker.SOD) {
                throw markerError(sod, "SOD must follow SOT/PPT in the supported classic profile");
            }
            long tilePartEnd = checkedAdd(
                    current.offset(), startOfTile.tilePartLength(), "SOT Psot");
            long tileDataLength = tilePartEnd - reader.position();
            if (tileDataLength < 0 || tileDataLength > Integer.MAX_VALUE) {
                throw markerError(current, "SOT Psot is shorter than its tile-part header");
            }
            byte[] tileData = reader.readRaw((int) tileDataLength, "tile-part data");
            tileParts.add(new Jpeg2000ClassicTilePart(startOfTile, tileData,
                    ppt.toByteArray()));
            current = reader.readNext();
        }
        if (current.marker() != Jpeg2000Marker.EOC) {
            throw markerError(current, "only SOT or EOC may follow tile-part data");
        }
        if (!tileLengths.isEmpty() && tileParts.size() != tileLengths.size()) {
            throw markerError(current, "TLM has entries without matching tile-parts");
        }
        validateTilePartCompleteness(size, tileStates, current);
        if (ppm.size() > 0) {
            byte[] packed = ppm.toByteArray();
            int position = 0;
            for (int i = 0; i < tileParts.size(); i++) {
                if (packed.length - position < 4) {
                    throw new Jpeg2000Exception("JPEG 2000 PPM Nppm field is truncated");
                }
                long length = ((long) (packed[position] & 0xff) << 24)
                        | ((long) (packed[position + 1] & 0xff) << 16)
                        | ((long) (packed[position + 2] & 0xff) << 8)
                        | (packed[position + 3] & 0xff);
                position += 4;
                if (length == 0 || length > packed.length - position) {
                    throw new Jpeg2000Exception("JPEG 2000 PPM Nppm length is invalid");
                }
                Jpeg2000ClassicTilePart part = tileParts.get(i);
                if (part.packedHeaders().length != 0) {
                    throw new Jpeg2000Exception("JPEG 2000 PPM and PPT cannot both supply tile-part headers");
                }
                byte[] header = new byte[(int) length];
                System.arraycopy(packed, position, header, 0, header.length);
                position += header.length;
                tileParts.set(i, new Jpeg2000ClassicTilePart(part.startOfTile(),
                        part.data(), header));
            }
            if (position != packed.length) {
                throw new Jpeg2000Exception("JPEG 2000 PPM contains extra packed header bytes");
            }
        }
        return new Jpeg2000ClassicCodestream(
                size,
                coding,
                componentCoding,
                quantization,
                componentQuantization,
                comments,
                progressionChanges,
                shifts,
                tileParts,
                reader.position());
    }

    private static void validateTilePartOrder(
            Jpeg2000MarkerSegment segment,
            Jpeg2000StartOfTileSegment start,
            Map<Integer, TilePartState> states) throws Jpeg2000Exception {
        Integer tile = Integer.valueOf(start.tileIndex());
        TilePartState state = states.get(tile);
        if (state == null) {
            state = new TilePartState();
            states.put(tile, state);
        }
        if (start.tilePartIndex() != state.nextIndex) {
            throw markerError(segment, "TPsot must start at zero and increase by one for each tile");
        }
        int count = start.tilePartCount();
        if (count != 0) {
            if (state.declaredCount != 0 && state.declaredCount != count) {
                throw markerError(segment, "TNsot is inconsistent with an earlier tile-part");
            }
            state.declaredCount = count;
        }
        state.nextIndex++;
    }

    private static void validateTilePartCompleteness(
            Jpeg2000SizeSegment size,
            Map<Integer, TilePartState> states,
            Jpeg2000MarkerSegment eoc) throws Jpeg2000Exception {
        if (states.size() != size.tileCount()) {
            throw markerError(eoc, "one or more SIZ tiles have no tile-part");
        }
        for (Map.Entry<Integer, TilePartState> entry : states.entrySet()) {
            TilePartState state = entry.getValue();
            if (state.declaredCount != 0 && state.nextIndex != state.declaredCount) {
                throw markerError(eoc, "tile " + entry.getKey()
                        + " is missing one or more tile-parts declared by TNsot");
            }
        }
    }

    private static final class TilePartState {
        private int nextIndex;
        private int declaredCount;
    }

    private static final class TileLength {
        private final int tileIndex;
        private final long length;

        private TileLength(int tileIndex, long length) {
            this.tileIndex = tileIndex;
            this.length = length;
        }
    }

    private static void requireSize(Jpeg2000SizeSegment size, Jpeg2000MarkerSegment segment)
            throws Jpeg2000Exception {
        if (size == null) {
            throw markerError(segment, Jpeg2000Marker.name(segment.marker()) + " marker occurred before SIZ");
        }
    }

    private static void requireRequiredHeader(
            Jpeg2000SizeSegment size,
            Jpeg2000CodingStyleSegment coding,
            Jpeg2000QuantizationSegment quantization) throws Jpeg2000Exception {
        if (size == null) {
            throw new Jpeg2000Exception("JPEG 2000 classic codestream is missing required SIZ marker");
        }
        if (coding == null) {
            throw new Jpeg2000Exception("JPEG 2000 classic codestream is missing required COD marker");
        }
        if (quantization == null) {
            throw new Jpeg2000Exception("JPEG 2000 classic codestream is missing required QCD marker");
        }
    }

    private static long checkedAdd(long left, long right, String context) throws Jpeg2000Exception {
        if (left > Long.MAX_VALUE - right) {
            throw new Jpeg2000Exception("JPEG 2000 " + context + " overflow");
        }
        return left + right;
    }

    private static Jpeg2000Exception markerError(Jpeg2000MarkerSegment segment, String message) {
        return new Jpeg2000Exception(
                "JPEG 2000 " + Jpeg2000Marker.name(segment.marker()) + " at offset "
                        + segment.offset() + ": " + message);
    }
}
