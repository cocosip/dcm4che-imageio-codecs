package io.github.cocosip.dcm4che.imageio.codecs.core.registry;

import java.io.IOException;
import java.io.InputStream;

import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.ImageWriterFactory;

/** Incrementally loads codec-owned mappings into the dcm4che factories. */
public final class CodecRegistrations {

    private CodecRegistrations() {
    }

    public static void load(Class<?> anchor, String readers, String writers) throws IOException {
        if (anchor == null) {
            throw new NullPointerException("anchor");
        }
        try (InputStream readerStream = requireResource(anchor, readers);
                InputStream writerStream = requireResource(anchor, writers)) {
            ImageReaderFactory.getDefault().load(readerStream);
            ImageWriterFactory.getDefault().load(writerStream);
        }
    }

    private static InputStream requireResource(Class<?> anchor, String name) throws IOException {
        InputStream stream = anchor.getResourceAsStream(name);
        if (stream == null) {
            throw new IOException("No such resource relative to "
                    + anchor.getName() + ": " + name);
        }
        return stream;
    }
}
