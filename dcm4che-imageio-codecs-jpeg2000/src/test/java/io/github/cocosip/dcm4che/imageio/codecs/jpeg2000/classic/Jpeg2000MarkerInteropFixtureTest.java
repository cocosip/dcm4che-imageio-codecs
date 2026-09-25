package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

class Jpeg2000MarkerInteropFixtureTest {
    @Test
    void decodesCommittedMarkerVariantsExactly() throws Exception {
        byte[] expected = new byte[67 * 65];
        for (int y = 0; y < 65; y++) {
            for (int x = 0; x < 67; x++) {
                expected[y * 67 + x] = (byte) ((x * 31 + y * 73 + (x ^ y) * 7) & 255);
            }
        }
        for (String variant : new String[] {"sop_eph", "ppt", "ppm", "rgn", "poc"}) {
            String resource = "/jpeg2000/java_synthetic_" + variant + ".j2k";
            Jpeg2000Raster decoded = Jpeg2000LosslessCodec.decode(readResource(resource));
            assertEquals(67, decoded.width(), variant);
            assertEquals(65, decoded.height(), variant);
            assertEquals(8, decoded.precision(), variant);
            assertEquals(false, decoded.signed(), variant);
            assertArrayEquals(expected, decoded.toFrame(false), variant);
        }
    }

    @Test
    void decodesSameFixtureAcrossConcurrentInstances() throws Exception {
        byte[] codestream = readResource("/jpeg2000/java_synthetic_poc.j2k");
        byte[] expected = Jpeg2000LosslessCodec.decode(codestream).toFrame(false);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<byte[]>> results = new ArrayList<Future<byte[]>>();
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() ->
                        Jpeg2000LosslessCodec.decode(codestream).toFrame(false)));
            }
            for (Future<byte[]> result : results) {
                assertArrayEquals(expected, result.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private byte[] readResource(String resource) throws Exception {
        InputStream input = getClass().getResourceAsStream(resource);
        assertNotNull(input, resource);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream stream = input) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
        }
        return bytes.toByteArray();
    }
}
