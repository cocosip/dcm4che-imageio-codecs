package io.github.cocosip.dcm4che.imageio.codecs.core.registry;

import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.ImageWriterFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

class CodecRegistrationsTest {
    private static final String TEST_UID = "9.9.9.1";
    private static final String SENTINEL_UID = "9.9.9.2";

    @Test
    void loadsReaderAndWriterResourcesIncrementally() throws Exception {
        ImageReaderFactory.ImageReaderParam sentinelReader =
                new ImageReaderFactory.ImageReaderParam("sentinel-reader", null, null);
        ImageWriterFactory.ImageWriterParam sentinelWriter =
                new ImageWriterFactory.ImageWriterParam(
                        "sentinel-writer", null, null, new String[0]);
        ImageReaderFactory.getDefault().put(SENTINEL_UID, sentinelReader);
        ImageWriterFactory.getDefault().put(SENTINEL_UID, sentinelWriter);
        try {
            CodecRegistrations.load(
                    CodecRegistrationsTest.class, "readers.properties", "writers.properties");

            ImageReaderFactory.ImageReaderParam reader =
                    ImageReaderFactory.getDefault().get(TEST_UID);
            ImageWriterFactory.ImageWriterParam writer =
                    ImageWriterFactory.getDefault().get(TEST_UID);
            assertNotNull(reader);
            assertNotNull(writer);
            assertEquals("test-reader", reader.formatName);
            assertEquals("test-writer", writer.formatName);
            assertSame(sentinelReader, ImageReaderFactory.getDefault().get(SENTINEL_UID));
            assertSame(sentinelWriter, ImageWriterFactory.getDefault().get(SENTINEL_UID));
        } finally {
            ImageReaderFactory.getDefault().remove(TEST_UID);
            ImageWriterFactory.getDefault().remove(TEST_UID);
            ImageReaderFactory.getDefault().remove(SENTINEL_UID);
            ImageWriterFactory.getDefault().remove(SENTINEL_UID);
        }
    }

    @Test
    void rejectsMissingResourcesBeforeChangingFactories() {
        assertThrows(IOException.class, () -> CodecRegistrations.load(
                CodecRegistrationsTest.class, "missing-readers.properties", "writers.properties"));

        assertEquals(false, ImageReaderFactory.getDefault().contains(TEST_UID));
        assertEquals(null, ImageWriterFactory.getDefault().get(TEST_UID));
    }
}
