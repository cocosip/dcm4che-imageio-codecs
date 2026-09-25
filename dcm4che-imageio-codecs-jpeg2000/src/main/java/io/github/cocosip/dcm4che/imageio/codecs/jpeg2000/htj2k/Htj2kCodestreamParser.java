package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.IIOException;
import javax.imageio.stream.ImageInputStream;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodestreamReader;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Marker;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000MarkerSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000SizeSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000StartOfTileSegment;

final class Htj2kCodestreamParser {
    private static final byte[] JP2_SIGNATURE = {
            0, 0, 0, 12, 0x6a, 0x50, 0x20, 0x20, 0x0d, 0x0a, (byte) 0x87, 0x0a
    };
    private static final long HT_CAPABILITY_BIT = 0x00020000L;
    private static final int MAX_TILE_PARTS = 1_000_000;

    private final Jpeg2000CodestreamReader reader;
    private final Jpeg2000Limits limits;
    private final Htj2kFrameCodec codec;

    Htj2kCodestreamParser(ImageInputStream input, long frameLength,
            Jpeg2000Limits limits, Htj2kFrameCodec codec) throws IOException {
        this.reader = new Jpeg2000CodestreamReader(input, frameLength, limits);
        this.limits = limits;
        this.codec = codec;
    }

    Htj2kCodestream parse() throws IOException {
        if (reader.startsWith(JP2_SIGNATURE)) {
            throw new IIOException("HTJ2K JP2 wrapper is unsupported; expected raw SOC codestream");
        }
        Jpeg2000MarkerSegment segment = reader.readNext();
        if (segment.marker() != Jpeg2000Marker.SOC || segment.offset() != 0) {
            throw error(segment, "raw codestream must begin with SOC");
        }
        segment = reader.readNext();
        if (segment.marker() != Jpeg2000Marker.SIZ) {
            throw error(segment, "SIZ must immediately follow SOC");
        }
        Jpeg2000SizeSegment size;
        try {
            size = Jpeg2000SizeSegment.parse(segment, limits);
        } catch (Jpeg2000Exception failure) {
            throw error(segment, failure.getMessage());
        }
        if (size.components().size() != 1 && size.components().size() != 3) {
            throw error(segment, "initial HT profile supports one or three components");
        }
        if (size.capabilities() != 0 && size.capabilities() != 0x4000) {
            throw error(segment, "unsupported HT Rsiz/profile flags 0x"
                    + Integer.toHexString(size.capabilities()));
        }
        for (Jpeg2000SizeSegment.Component component : size.components()) {
            if (component.precision() > 16) {
                throw error(segment, "initial HT profile supports at most 16-bit components");
            }
        }

        boolean hasCap = false;
        CodingStyle coding = null;
        boolean hasQcd = false;
        int nextTlmIndex = 0;
        List<TileLength> tileLengths = new ArrayList<TileLength>();
        segment = reader.readNext();
        while (segment.marker() != Jpeg2000Marker.SOT) {
            switch (segment.marker()) {
                case Jpeg2000Marker.CAP:
                    if (hasCap) {
                        throw error(segment, "duplicate CAP");
                    }
                    parseCap(segment);
                    hasCap = true;
                    break;
                case Jpeg2000Marker.COD:
                    if (coding != null) {
                        throw error(segment, "duplicate COD");
                    }
                    coding = parseCoding(segment, size);
                    break;
                case Jpeg2000Marker.QCD:
                    if (coding == null || hasQcd) {
                        throw error(segment, "QCD requires one preceding COD and may occur only once");
                    }
                    validateQuantization(segment, coding);
                    hasQcd = true;
                    break;
                case Jpeg2000Marker.TLM:
                    nextTlmIndex = parseTlm(segment, size, nextTlmIndex, tileLengths);
                    break;
                case Jpeg2000Marker.COM:
                    if (segment.payload().length < 2) {
                        throw error(segment, "COM registration field is truncated");
                    }
                    break;
                case Jpeg2000Marker.RGN:
                case Jpeg2000Marker.PPM:
                case Jpeg2000Marker.PPT:
                    throw error(segment, "unsupported HT marker");
                default:
                    throw error(segment, "unsupported or misplaced HT main-header marker");
            }
            segment = reader.readNext();
        }
        if (coding == null || !hasQcd) {
            throw error(segment, "HT main header requires COD and QCD");
        }

        Map<Integer, TileState> states = new HashMap<Integer, TileState>();
        int partCount = 0;
        while (segment.marker() == Jpeg2000Marker.SOT) {
            if (partCount == MAX_TILE_PARTS) {
                throw error(segment, "tile-part count exceeds limit " + MAX_TILE_PARTS);
            }
            Jpeg2000StartOfTileSegment start;
            try {
                start = Jpeg2000StartOfTileSegment.parse(segment, size);
            } catch (Jpeg2000Exception failure) {
                throw error(segment, failure.getMessage());
            }
            if (start.tilePartLength() == 0) {
                throw error(segment, "Psot=0 is unsupported because the tile-part boundary is ambiguous");
            }
            if (!tileLengths.isEmpty()) {
                if (partCount >= tileLengths.size()) {
                    throw error(segment, "SOT has no matching TLM entry");
                }
                TileLength expected = tileLengths.get(partCount);
                if ((expected.tileIndex >= 0 && expected.tileIndex != start.tileIndex())
                        || expected.length != start.tilePartLength()) {
                    throw error(segment, "SOT Isot/Psot disagrees with TLM");
                }
            }
            TileState state = states.get(start.tileIndex());
            if (state == null) {
                state = new TileState();
                states.put(start.tileIndex(), state);
            }
            if (start.tilePartIndex() != state.nextIndex) {
                throw error(segment, "TPsot must begin at zero and increase per tile");
            }
            if (start.tilePartCount() != 0) {
                if (state.declaredCount != 0 && state.declaredCount != start.tilePartCount()) {
                    throw error(segment, "inconsistent TNsot for tile " + start.tileIndex());
                }
                state.declaredCount = start.tilePartCount();
            }
            state.nextIndex++;

            long partEnd = segment.offset() + start.tilePartLength();
            if (partEnd < segment.offset()) {
                throw error(segment, "Psot overflows codestream position");
            }
            Jpeg2000MarkerSegment sod = reader.readNext();
            if (sod.marker() != Jpeg2000Marker.SOD) {
                throw error(sod, "SOD must follow SOT in the initial HT profile");
            }
            long dataLength = partEnd - reader.position();
            if (dataLength < 0) {
                throw error(segment, "Psot is shorter than its tile-part header");
            }
            reader.skipRaw(dataLength, "HT tile-part data");
            partCount++;
            segment = reader.readNext();
        }
        if (segment.marker() != Jpeg2000Marker.EOC) {
            throw error(segment, "only SOT or EOC may follow tile-part data");
        }
        if (!tileLengths.isEmpty() && tileLengths.size() != partCount) {
            throw error(segment, "TLM has entries without matching tile-parts");
        }
        if (states.size() != size.tileCount()) {
            throw error(segment, "one or more SIZ tiles have no tile-part");
        }
        for (Map.Entry<Integer, TileState> entry : states.entrySet()) {
            TileState state = entry.getValue();
            if (state.declaredCount != 0 && state.nextIndex != state.declaredCount) {
                throw error(segment, "tile " + entry.getKey() + " is missing declared tile-parts");
            }
        }
        long logicalLength = reader.position();
        if (reader.remaining() > 1
                || (reader.remaining() == 1 && reader.readRaw(1, "DICOM padding")[0] != 0)) {
            throw error(segment, "unexpected bytes after EOC");
        }
        return new Htj2kCodestream(size, coding.progression, coding.reversible,
                coding.mct, partCount, logicalLength);
    }

    private void parseCap(Jpeg2000MarkerSegment segment) throws IIOException {
        byte[] payload = segment.payload();
        if (payload.length < 6) {
            throw error(segment, "CAP is shorter than Pcap and Ccap15");
        }
        long pcap = unsignedInt(payload, 0);
        if (pcap != HT_CAPABILITY_BIT || payload.length != 4 + 2 * Long.bitCount(pcap)) {
            throw error(segment, "CAP capability bits or Ccap length are unsupported");
        }
    }

    private CodingStyle parseCoding(Jpeg2000MarkerSegment segment,
            Jpeg2000SizeSegment size) throws IOException {
        byte[] payload = segment.payload();
        if (payload.length < 10 || (payload[0] & ~0x07) != 0) {
            throw error(segment, "COD length or coding-style flags are invalid");
        }
        Jpeg2000ProgressionOrder progression;
        try {
            progression = Jpeg2000ProgressionOrder.fromCode(payload[1] & 0xff);
        } catch (Jpeg2000Exception failure) {
            throw error(segment, failure.getMessage());
        }
        if (!codec.selectableProgression() && progression != Jpeg2000ProgressionOrder.RPCL) {
            throw error(segment, "this transfer syntax requires RPCL progression");
        }
        int layers = unsignedShort(payload, 2);
        try {
            limits.requireQualityLayerCount(layers);
        } catch (Jpeg2000Exception failure) {
            throw error(segment, failure.getMessage());
        }
        if (layers != 1) {
            throw error(segment, "initial HT profile requires exactly one layer");
        }
        int mct = payload[4] & 0xff;
        if (mct > 1 || (mct == 1 && size.components().size() != 3)) {
            throw error(segment, "COD MCT state is invalid for component count");
        }
        int levels = payload[5] & 0xff;
        int widthExponent = payload[6] & 0xff;
        int heightExponent = payload[7] & 0xff;
        if (levels > 32 || widthExponent > 8 || heightExponent > 8
                || widthExponent + heightExponent > 8) {
            throw error(segment, "COD decomposition or code-block geometry is invalid");
        }
        if ((payload[8] & 0xff) != 0x40) {
            throw error(segment, "COD code-block style must select HT coding (0x40)");
        }
        int transform = payload[9] & 0xff;
        if (transform > 1 || (transform == 1) != codec.reversible()) {
            throw error(segment, "COD transform disagrees with transfer syntax");
        }
        int expected = 10 + (((payload[0] & 1) != 0) ? levels + 1 : 0);
        if (payload.length != expected) {
            throw error(segment, "COD precinct payload length is invalid");
        }
        return new CodingStyle(progression, transform == 1, mct == 1, levels);
    }

    private static void validateQuantization(Jpeg2000MarkerSegment segment,
            CodingStyle coding) throws IIOException {
        byte[] payload = segment.payload();
        if (payload.length == 0) {
            throw error(segment, "QCD is empty");
        }
        int style = payload[0] & 0x1f;
        int subbands = 1 + 3 * coding.levels;
        int expected = coding.reversible ? 1 + subbands : 1 + 2 * subbands;
        if (style != (coding.reversible ? 0 : 2) || payload.length != expected) {
            throw error(segment, "QCD quantization style or subband count disagrees with COD");
        }
    }

    private static int parseTlm(Jpeg2000MarkerSegment segment, Jpeg2000SizeSegment size,
            int nextIndex, List<TileLength> lengths) throws IIOException {
        byte[] payload = segment.payload();
        if (payload.length < 2 || (payload[0] & 0xff) != nextIndex || nextIndex > 255) {
            throw error(segment, "TLM index or length is invalid");
        }
        int stlm = payload[1] & 0xff;
        int indexBytes = (stlm >>> 4) & 3;
        int lengthBytes = (stlm & 0x40) == 0 ? 2 : 4;
        int entryBytes = indexBytes + lengthBytes;
        if ((stlm & 0x8f) != 0 || indexBytes == 3
                || (payload.length - 2) % entryBytes != 0) {
            throw error(segment, "TLM field sizes or entry length are invalid");
        }
        for (int offset = 2; offset < payload.length; offset += entryBytes) {
            int tile = indexBytes == 0 ? -1
                    : indexBytes == 1 ? payload[offset] & 0xff : unsignedShort(payload, offset);
            long length = 0;
            for (int i = 0; i < lengthBytes; i++) {
                length = (length << 8) | (payload[offset + indexBytes + i] & 0xff);
            }
            if (tile >= size.tileCount() || length < 14 || lengths.size() == MAX_TILE_PARTS) {
                throw error(segment, "TLM tile index, Psot, or entry count is invalid");
            }
            lengths.add(new TileLength(tile, length));
        }
        return nextIndex + 1;
    }

    private static int unsignedShort(byte[] payload, int offset) {
        return ((payload[offset] & 0xff) << 8) | (payload[offset + 1] & 0xff);
    }

    private static long unsignedInt(byte[] payload, int offset) {
        return ((long) unsignedShort(payload, offset) << 16) | unsignedShort(payload, offset + 2);
    }

    private static IIOException error(Jpeg2000MarkerSegment segment, String message) {
        return new IIOException("HTJ2K " + Jpeg2000Marker.name(segment.marker())
                + " at offset " + segment.offset() + ": " + message);
    }

    private static final class CodingStyle {
        private final Jpeg2000ProgressionOrder progression;
        private final boolean reversible;
        private final boolean mct;
        private final int levels;

        private CodingStyle(Jpeg2000ProgressionOrder progression, boolean reversible,
                boolean mct, int levels) {
            this.progression = progression;
            this.reversible = reversible;
            this.mct = mct;
            this.levels = levels;
        }
    }

    private static final class TileLength {
        private final int tileIndex;
        private final long length;

        private TileLength(int tileIndex, long length) {
            this.tileIndex = tileIndex;
            this.length = length;
        }
    }

    private static final class TileState {
        private int nextIndex;
        private int declaredCount;
    }
}
