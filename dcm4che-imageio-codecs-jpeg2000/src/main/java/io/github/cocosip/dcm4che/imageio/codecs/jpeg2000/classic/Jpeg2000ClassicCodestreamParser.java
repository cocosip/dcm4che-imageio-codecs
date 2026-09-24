package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

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
        List<Jpeg2000CommentSegment> comments = new ArrayList<Jpeg2000CommentSegment>();
        Set<Integer> codingComponents = new HashSet<Integer>();
        Set<Integer> quantizationComponents = new HashSet<Integer>();
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
                Jpeg2000ComponentQuantizationSegment parsed =
                        Jpeg2000ComponentQuantizationSegment.parse(segment, size, coding);
                if (!quantizationComponents.add(parsed.componentIndex())) {
                    throw markerError(segment, "duplicate QCC component override");
                }
                componentQuantization.add(parsed);
            } else if (marker == Jpeg2000Marker.COM) {
                requireSize(size, segment);
                comments.add(Jpeg2000CommentSegment.parse(segment));
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

        List<Jpeg2000ClassicTilePart> tileParts = new ArrayList<Jpeg2000ClassicTilePart>();
        Map<Integer, TilePartState> tileStates = new HashMap<Integer, TilePartState>();
        Jpeg2000MarkerSegment current = startSegment;
        while (current.marker() == Jpeg2000Marker.SOT) {
            Jpeg2000StartOfTileSegment startOfTile = Jpeg2000StartOfTileSegment.parse(current, size);
            validateTilePartOrder(current, startOfTile, tileStates);
            if (startOfTile.tilePartLength() == 0) {
                throw markerError(current, "Psot=0 is unsupported because the tile-part boundary is ambiguous");
            }

            Jpeg2000MarkerSegment sod = reader.readNext();
            if (sod.marker() != Jpeg2000Marker.SOD) {
                throw markerError(sod, "SOD must immediately follow SOT in the supported classic profile");
            }
            long tilePartEnd = checkedAdd(
                    current.offset(), startOfTile.tilePartLength(), "SOT Psot");
            long tileDataLength = tilePartEnd - reader.position();
            if (tileDataLength < 0 || tileDataLength > Integer.MAX_VALUE) {
                throw markerError(current, "SOT Psot is shorter than its tile-part header");
            }
            byte[] tileData = reader.readRaw((int) tileDataLength, "tile-part data");
            tileParts.add(new Jpeg2000ClassicTilePart(startOfTile, tileData));
            current = reader.readNext();
        }
        if (current.marker() != Jpeg2000Marker.EOC) {
            throw markerError(current, "only SOT or EOC may follow tile-part data");
        }
        validateTilePartCompleteness(size, tileStates, current);
        return new Jpeg2000ClassicCodestream(
                size,
                coding,
                componentCoding,
                quantization,
                componentQuantization,
                comments,
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
