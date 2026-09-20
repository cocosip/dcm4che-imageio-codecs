package io.github.cocosip.dcm4che.imageio.codecs.rle;

import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;

import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.ImageWriterFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RleSpiAndRegistrationTest {

    @Test
    void imageIoDiscoversRleReaderAndWriterServices() {
        ImageIO.scanForPlugins();

        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("rle-ext");
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("rle-ext");

        assertTrue(readers.hasNext());
        assertTrue(writers.hasNext());
        assertInstanceOf(RleImageReader.class, readers.next());
        assertInstanceOf(RleImageWriter.class, writers.next());
    }

    @Test
    void registersRleTransferSyntaxWithDcm4cheFactories() throws Exception {
        ImageReaderFactory readers = ImageReaderFactory.getDefault();
        ImageWriterFactory writers = ImageWriterFactory.getDefault();
        ImageReaderFactory.ImageReaderParam previousReader = readers.get(RleCodec.TRANSFER_SYNTAX_UID);
        ImageWriterFactory.ImageWriterParam previousWriter = writers.get(RleCodec.TRANSFER_SYNTAX_UID);
        try {
            RleCodec.register();

            ImageReaderFactory.ImageReaderParam reader =
                    readers.get(RleCodec.TRANSFER_SYNTAX_UID);
            ImageWriterFactory.ImageWriterParam writer =
                    writers.get(RleCodec.TRANSFER_SYNTAX_UID);
            assertEquals("rle-ext", reader.formatName);
            assertEquals(RleImageReader.class.getName(), reader.className);
            assertEquals("rle-ext", writer.formatName);
            assertEquals(RleImageWriter.class.getName(), writer.className);
        } finally {
            restore(readers, previousReader);
            restore(writers, previousWriter);
        }
    }

    private static void restore(ImageReaderFactory factory,
            ImageReaderFactory.ImageReaderParam previous) {
        if (previous == null) {
            factory.remove(RleCodec.TRANSFER_SYNTAX_UID);
        } else {
            factory.put(RleCodec.TRANSFER_SYNTAX_UID, previous);
        }
    }

    private static void restore(ImageWriterFactory factory,
            ImageWriterFactory.ImageWriterParam previous) {
        if (previous == null) {
            factory.remove(RleCodec.TRANSFER_SYNTAX_UID);
        } else {
            factory.put(RleCodec.TRANSFER_SYNTAX_UID, previous);
        }
    }
}
