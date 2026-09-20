package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;

import javax.imageio.stream.MemoryCacheImageInputStream;

import org.junit.jupiter.api.Test;

class JpegMarkerReaderTest {
    @Test
    void readsMarkerAndPayloadLength() throws Exception {
        byte[] jpeg = {(byte) 0xff, (byte) 0xe0, 0, 4, 1, 2};
        JpegMarkerReader reader = new JpegMarkerReader(new MemoryCacheImageInputStream(
                new ByteArrayInputStream(jpeg)));

        JpegMarker marker = reader.next();

        assertEquals(0xe0, marker.code());
        assertEquals(2, marker.payload().length);
    }

    @Test
    void rejectsMissingMarkerPrefix() throws Exception {
        JpegMarkerReader reader = new JpegMarkerReader(new MemoryCacheImageInputStream(
                new ByteArrayInputStream(new byte[] {1, 2})));

        assertThrows(JpegException.class, reader::next);
    }
}
