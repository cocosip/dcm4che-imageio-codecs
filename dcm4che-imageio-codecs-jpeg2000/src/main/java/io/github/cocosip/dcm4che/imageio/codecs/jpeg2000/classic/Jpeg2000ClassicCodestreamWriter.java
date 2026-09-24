package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import java.io.IOException;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodestreamWriter;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CommentSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ComponentCodingStyleSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ComponentQuantizationSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Marker;

public final class Jpeg2000ClassicCodestreamWriter {
    private final Jpeg2000CodestreamWriter writer;

    public Jpeg2000ClassicCodestreamWriter(Jpeg2000CodestreamWriter writer) {
        if (writer == null) {
            throw new NullPointerException("writer");
        }
        this.writer = writer;
    }

    public void write(Jpeg2000ClassicCodestream codestream) throws IOException {
        if (codestream == null) {
            throw new NullPointerException("codestream");
        }
        byte[] tileData = codestream.tileData();
        long psot = 14L + tileData.length;
        if (psot > 0xffffffffL) {
            throw new IllegalArgumentException("JPEG 2000 tile-part is too large for Psot");
        }

        writer.writeStandalone(Jpeg2000Marker.SOC);
        writer.writeSegment(Jpeg2000Marker.SIZ, codestream.size().payload());
        writer.writeSegment(Jpeg2000Marker.COD, codestream.codingStyle().payload());
        for (Jpeg2000ComponentCodingStyleSegment component : codestream.componentCodingStyles()) {
            writer.writeSegment(Jpeg2000Marker.COC, component.payload());
        }
        writer.writeSegment(Jpeg2000Marker.QCD, codestream.quantization().payload());
        for (Jpeg2000ComponentQuantizationSegment component : codestream.componentQuantizations()) {
            writer.writeSegment(Jpeg2000Marker.QCC, component.payload());
        }
        for (Jpeg2000CommentSegment comment : codestream.comments()) {
            writer.writeSegment(Jpeg2000Marker.COM, comment.payload());
        }
        writer.writeSegment(Jpeg2000Marker.SOT, startOfTilePayload(psot));
        writer.writeStandalone(Jpeg2000Marker.SOD);
        writer.writeRaw(tileData);
        writer.writeStandalone(Jpeg2000Marker.EOC);
    }

    private static byte[] startOfTilePayload(long psot) {
        return new byte[] {
                0,
                0,
                (byte) (psot >>> 24),
                (byte) (psot >>> 16),
                (byte) (psot >>> 8),
                (byte) psot,
                0,
                1
        };
    }
}
