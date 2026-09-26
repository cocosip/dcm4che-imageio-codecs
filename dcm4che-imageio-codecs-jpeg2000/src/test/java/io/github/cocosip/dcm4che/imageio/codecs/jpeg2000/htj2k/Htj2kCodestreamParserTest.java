package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Marker;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;

class Htj2kCodestreamParserTest {
    @Test
    void inspectsLosslessFrameAndExcludesDicomPadding() throws Exception {
        byte[] frame = frame(0x4000, true, 2, 0x40, 14, 14, true, true);
        Htj2kCodestream parsed = inspect(Htj2kFrameCodec.LOSSLESS_UID, frame);

        assertEquals(8, parsed.size().referenceGridWidth());
        assertEquals(Jpeg2000ProgressionOrder.RPCL, parsed.progression());
        assertTrue(parsed.reversible());
        assertEquals(1, parsed.tilePartCount());
        assertEquals(frame.length - 1, parsed.logicalLength());
    }

    @Test
    void syntaxSelectionAllowsCprlOnlyForRpclTransferSyntax() throws Exception {
        byte[] frame = frame(0x4000, true, 4, 0x40, 14, 14, false, false);
        assertEquals(Jpeg2000ProgressionOrder.CPRL,
                inspect(Htj2kFrameCodec.LOSSLESS_RPCL_UID, frame).progression());
        assertRejected(Htj2kFrameCodec.LOSSLESS_UID, frame, "RPCL progression");
        assertThrows(IIOException.class,
                () -> Htj2kFrameCodec.forTransferSyntax("1.2.840.10008.1.2.4.90"));
    }

    @Test
    void lossySyntaxRequiresIrreversibleTransformAndQuantization() throws Exception {
        assertEquals(false, inspect(Htj2kFrameCodec.LOSSY_UID,
                frame(0x4000, false, 2, 0x40, 14, 14, false, false)).reversible());
        assertRejected(Htj2kFrameCodec.LOSSY_UID,
                frame(0x4000, true, 2, 0x40, 14, 14, false, false), "COD transform");
    }

    @Test
    void rejectsClassicStyleAndUnsupportedProfile() throws Exception {
        assertRejected(Htj2kFrameCodec.LOSSLESS_UID,
                frame(0x4000, true, 2, 0, 14, 14, false, false), "HT coding");
        assertRejected(Htj2kFrameCodec.LOSSLESS_UID,
                frame(0x0001, true, 2, 0x40, 14, 14, false, false), "Rsiz/profile");
        byte[] badCap = frame(0x4000, true, 2, 0x40, 14, 14, false, false);
        badCap[50] = 1;
        assertRejected(Htj2kFrameCodec.LOSSLESS_UID, badCap, "CAP capability");
        assertRejected(Htj2kFrameCodec.LOSSLESS_UID,
                frame(0x4000, true, 2, 0x40, 14, 14, false, false,
                        marker(Jpeg2000Marker.CAP, new byte[] {0, 2, 0, 0, 0, 0})),
                "duplicate CAP");
    }

    @Test
    void acceptsOptionalCapAndTlmOmission() throws Exception {
        byte[] frame = frame(0x4000, true, 2, 0x40, 14, 14, false, false);
        int capOffset = 45;
        byte[] noCap = new byte[frame.length - 10];
        System.arraycopy(frame, 0, noCap, 0, capOffset);
        System.arraycopy(frame, capOffset + 10, noCap, capOffset,
                frame.length - capOffset - 10);
        assertEquals(1, inspect(Htj2kFrameCodec.LOSSLESS_UID, noCap).tilePartCount());
    }

    @Test
    void checksCapTransformAgainstCod() throws Exception {
        byte[] wrongCap = frame(0x4000, true, 2, 0x40, 14, 14, false, false);
        wrongCap[54] = 0x20;
        assertRejected(Htj2kFrameCodec.LOSSLESS_UID, wrongCap,
                "CAP Ccap15 transform flag disagrees with COD");
        byte[] wrongMagnitude = frame(0x4000, true, 2, 0x40, 14, 14, false, false);
        wrongMagnitude[54] = 0;
        assertRejected(Htj2kFrameCodec.LOSSLESS_UID, wrongMagnitude,
                "CAP Ccap15 magnitude disagrees with QCD");
    }

    @Test
    void rejectsForbiddenMarkersAndJp2Wrapper() throws Exception {
        for (int marker : new int[] {
                Jpeg2000Marker.RGN, Jpeg2000Marker.PPM, Jpeg2000Marker.PPT}) {
            assertRejected(Htj2kFrameCodec.LOSSLESS_UID,
                    frame(0x4000, true, 2, 0x40, 14, 14, false, false,
                            marker(marker, new byte[] {0, 0})),
                    "unsupported HT marker");
        }
        assertRejected(Htj2kFrameCodec.LOSSLESS_UID,
                new byte[] {0, 0, 0, 12, 0x6a, 0x50, 0x20, 0x20,
                        0x0d, 0x0a, (byte) 0x87, 0x0a}, "JP2 wrapper");
    }

    @Test
    void checksTilePartLengthsAndFrameBoundary() throws Exception {
        assertRejected(Htj2kFrameCodec.LOSSLESS_UID,
                frame(0x4000, true, 2, 0x40, 15, 14, true, false), "TLM");
        assertRejected(Htj2kFrameCodec.LOSSLESS_UID,
                frame(0x4000, true, 2, 0x40, 15, 15, false, false), "marker prefix");
        assertRejected(Htj2kFrameCodec.LOSSLESS_UID,
                frame(0x4000, true, 2, 0x40, 1000, 1000, false, false), "declared frame");
        byte[] badPadding = frame(0x4000, true, 2, 0x40, 14, 14, false, true);
        badPadding[badPadding.length - 1] = 1;
        assertRejected(Htj2kFrameCodec.LOSSLESS_UID, badPadding, "unexpected bytes after EOC");
    }

    @Test
    void skipsBoundedNonEmptyTileData() throws Exception {
        byte[] empty = frame(0x4000, true, 2, 0x40, 15, 15, false, false);
        byte[] data = new byte[empty.length + 1];
        System.arraycopy(empty, 0, data, 0, empty.length - 2);
        data[empty.length - 2] = 0x25;
        System.arraycopy(empty, empty.length - 2, data, empty.length - 1, 2);
        assertEquals(1, inspect(Htj2kFrameCodec.LOSSLESS_UID, data).tilePartCount());
    }

    private static void assertRejected(String uid, byte[] frame, String message) throws Exception {
        IIOException error = assertThrows(IIOException.class, () -> inspect(uid, frame));
        assertTrue(error.getMessage().contains(uid), error.getMessage());
        assertTrue(error.getMessage().contains(message), error.getMessage());
    }

    private static Htj2kCodestream inspect(String uid, byte[] frame) throws IOException {
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(frame))) {
            return Htj2kFrameCodec.forTransferSyntax(uid).inspect(input, frame.length);
        }
    }

    private static byte[] frame(int rsiz, boolean reversible, int progression,
            int blockStyle, int psot, int tlmLength, boolean withTlm,
            boolean pad, byte[]... extraMarkers) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        marker(output, Jpeg2000Marker.SOC);
        byte[] siz = new byte[39];
        putShort(siz, 0, rsiz);
        putInt(siz, 2, 8);
        putInt(siz, 6, 8);
        putInt(siz, 18, 8);
        putInt(siz, 22, 8);
        putShort(siz, 34, 1);
        siz[36] = 7;
        siz[37] = 1;
        siz[38] = 1;
        segment(output, Jpeg2000Marker.SIZ, siz);
        segment(output, Jpeg2000Marker.CAP,
                new byte[] {0, 2, 0, 0, 0, (byte) (reversible ? 1 : 0x22)});
        for (byte[] extra : extraMarkers) {
            output.write(extra);
        }
        segment(output, Jpeg2000Marker.COD, new byte[] {
                0, (byte) progression, 0, 1, 0, 0, 4, 4, (byte) blockStyle,
                (byte) (reversible ? 1 : 0)});
        segment(output, Jpeg2000Marker.QCD,
                reversible ? new byte[] {0x40, 0x40} : new byte[] {0x42, 0x40, 0});
        if (withTlm) {
            segment(output, Jpeg2000Marker.TLM, new byte[] {
                    0, 0x10, 0, (byte) (tlmLength >>> 8), (byte) tlmLength});
        }
        byte[] sot = new byte[8];
        putInt(sot, 2, psot);
        sot[7] = 1;
        segment(output, Jpeg2000Marker.SOT, sot);
        marker(output, Jpeg2000Marker.SOD);
        marker(output, Jpeg2000Marker.EOC);
        if (pad) {
            output.write(0);
        }
        return output.toByteArray();
    }

    private static byte[] marker(int marker, byte[] payload) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        segment(output, marker, payload);
        return output.toByteArray();
    }

    private static void marker(ByteArrayOutputStream output, int marker) {
        output.write(0xff);
        output.write(marker);
    }

    private static void segment(ByteArrayOutputStream output, int marker, byte[] payload)
            throws IOException {
        marker(output, marker);
        output.write((payload.length + 2) >>> 8);
        output.write(payload.length + 2);
        output.write(payload);
    }

    private static void putShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
