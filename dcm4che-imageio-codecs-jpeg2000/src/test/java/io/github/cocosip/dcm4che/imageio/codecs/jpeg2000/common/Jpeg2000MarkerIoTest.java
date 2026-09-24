package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.junit.jupiter.api.Test;

class Jpeg2000MarkerIoTest {
    private static final Jpeg2000Limits LIMITS = new Jpeg2000Limits(
            64,
            16,
            4,
            8,
            12,
            10,
            3);

    @Test
    void boundedInputReadsBigEndianValuesWithoutConsumingTrailingBytes() throws Exception {
        MemoryCacheImageInputStream stream = input(0x12, 0x34, 0x56);
        Jpeg2000Input bounded = new Jpeg2000Input(stream, 2, LIMITS);

        assertEquals(0x1234, bounded.readUnsignedShort("test value"));
        assertEquals(0, bounded.remaining());
        assertThrows(Jpeg2000Exception.class, () -> bounded.readUnsignedByte("past frame"));
        assertEquals(2, stream.getStreamPosition());
        assertEquals(0x56, stream.readUnsignedByte());
    }

    @Test
    void readerParsesStandaloneAndLengthBearingMarkers() throws Exception {
        MemoryCacheImageInputStream stream = input(
                0xff, Jpeg2000Marker.SOC,
                0xff, Jpeg2000Marker.COD, 0x00, 0x04, 0x01, 0x02,
                0xff, Jpeg2000Marker.EOC);
        Jpeg2000CodestreamReader reader = new Jpeg2000CodestreamReader(stream, 10, LIMITS);

        Jpeg2000MarkerSegment soc = reader.readNext();
        Jpeg2000MarkerSegment cod = reader.readNext();
        Jpeg2000MarkerSegment eoc = reader.readNext();

        assertEquals(Jpeg2000Marker.SOC, soc.marker());
        assertEquals(0, soc.offset());
        assertFalse(soc.hasPayload());
        assertEquals(Jpeg2000Marker.COD, cod.marker());
        assertEquals(2, cod.offset());
        assertArrayEquals(new byte[] {1, 2}, cod.payload());
        assertEquals(Jpeg2000Marker.EOC, eoc.marker());
        assertEquals(0, reader.remaining());
    }

    @Test
    void readerRejectsMalformedMarkerFramingBeforeAllocation() throws Exception {
        Jpeg2000Exception prefix = assertThrows(
                Jpeg2000Exception.class,
                () -> reader(0x00, Jpeg2000Marker.SOC).readNext());
        Jpeg2000Exception shortLength = assertThrows(
                Jpeg2000Exception.class,
                () -> reader(0xff, Jpeg2000Marker.SIZ, 0x00, 0x01).readNext());
        Jpeg2000Exception truncated = assertThrows(
                Jpeg2000Exception.class,
                () -> reader(0xff, Jpeg2000Marker.SIZ, 0x00, 0x06, 0x01).readNext());

        assertTrue(prefix.getMessage().contains("offset 0"));
        assertTrue(shortLength.getMessage().contains("SIZ"));
        assertTrue(shortLength.getMessage().contains("length 1"));
        assertTrue(truncated.getMessage().contains("declares 4 payload bytes"));
    }

    @Test
    void readerRejectsMarkerPayloadBeyondConfiguredLimit() throws Exception {
        byte[] bytes = new byte[15];
        bytes[0] = (byte) 0xff;
        bytes[1] = (byte) Jpeg2000Marker.COM;
        bytes[2] = 0;
        bytes[3] = 13;

        Jpeg2000Exception error = assertThrows(
                Jpeg2000Exception.class,
                () -> reader(bytes).readNext());

        assertTrue(error.getMessage().contains("payload length 11"));
    }

    @Test
    void writerUsesBigEndianLengthsAndPreservesRawTileBytes() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(bytes);
        Jpeg2000CodestreamWriter writer = new Jpeg2000CodestreamWriter(stream, LIMITS);

        writer.writeStandalone(Jpeg2000Marker.SOC);
        writer.writeSegment(Jpeg2000Marker.COD, new byte[] {1, 2});
        writer.writeStandalone(Jpeg2000Marker.SOD);
        writer.writeRaw(new byte[] {3, (byte) 0xff, 4});
        writer.writeStandalone(Jpeg2000Marker.EOC);
        stream.flush();

        assertArrayEquals(new byte[] {
                (byte) 0xff, Jpeg2000Marker.SOC,
                (byte) 0xff, Jpeg2000Marker.COD, 0, 4, 1, 2,
                (byte) 0xff, (byte) Jpeg2000Marker.SOD, 3, (byte) 0xff, 4,
                (byte) 0xff, (byte) Jpeg2000Marker.EOC
        }, bytes.toByteArray());
    }

    @Test
    void writerRejectsMarkerKindAndPayloadLimitMismatches() throws Exception {
        MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(new ByteArrayOutputStream());
        Jpeg2000CodestreamWriter writer = new Jpeg2000CodestreamWriter(stream, LIMITS);

        assertThrows(IllegalArgumentException.class, () -> writer.writeStandalone(Jpeg2000Marker.COD));
        assertThrows(IllegalArgumentException.class, () -> writer.writeSegment(Jpeg2000Marker.SOC, new byte[0]));
        assertThrows(Jpeg2000Exception.class, () -> writer.writeSegment(Jpeg2000Marker.COM, new byte[11]));
    }

    private static Jpeg2000CodestreamReader reader(int... values) throws Exception {
        return reader(toBytes(values));
    }

    private static Jpeg2000CodestreamReader reader(byte[] values) throws Exception {
        return new Jpeg2000CodestreamReader(
                new MemoryCacheImageInputStream(new ByteArrayInputStream(values)),
                values.length,
                LIMITS);
    }

    private static MemoryCacheImageInputStream input(int... values) {
        return new MemoryCacheImageInputStream(new ByteArrayInputStream(toBytes(values)));
    }

    private static byte[] toBytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }
        return bytes;
    }
}
