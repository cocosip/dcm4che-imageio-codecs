package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.ImageWriterFactory;
import org.junit.jupiter.api.Test;

import io.github.cocosip.dcm4che.imageio.codecs.core.registry.CodecRegistrations;

class JpegCodecRegistrationTest {
    private static final String[][] PUBLIC_UIDS = {
        {"1.2.840.10008.1.2.4.50", "jpeg-ext"},
        {"1.2.840.10008.1.2.4.51", "jpeg-ext"},
        {"1.2.840.10008.1.2.4.57", "jpeg-lossless"},
        {"1.2.840.10008.1.2.4.70", "jpeg-lossless-sv1"}
    };

    private static final String[] NON_PUBLIC_UIDS = {
        "1.2.840.10008.1.2.4.52",
        "1.2.840.10008.1.2.4.53"
    };

    @Test
    void registersOnlyTheFourSupportedDicomJpegTransferSyntaxes() throws Exception {
        ImageReaderFactory.ImageReaderParam[] existingReaders = new ImageReaderFactory.ImageReaderParam[NON_PUBLIC_UIDS.length];
        ImageWriterFactory.ImageWriterParam[] existingWriters = new ImageWriterFactory.ImageWriterParam[NON_PUBLIC_UIDS.length];
        for (int i = 0; i < NON_PUBLIC_UIDS.length; i++) {
            existingReaders[i] = ImageReaderFactory.getDefault().get(NON_PUBLIC_UIDS[i]);
            existingWriters[i] = ImageWriterFactory.getDefault().get(NON_PUBLIC_UIDS[i]);
        }

        CodecRegistrations.load(JpegCodecRegistrationTest.class, "/io/github/cocosip/dcm4che/imageio/codecs/jpeg/readers.properties",
                "/io/github/cocosip/dcm4che/imageio/codecs/jpeg/writers.properties");

        for (String[] entry : PUBLIC_UIDS) {
            String uid = entry[0];
            assertEquals(entry[1], ImageReaderFactory.getDefault().get(uid).formatName);
            assertNotNull(ImageWriterFactory.getDefault().get(uid));
        }

        for (int i = 0; i < NON_PUBLIC_UIDS.length; i++) {
            assertSame(existingReaders[i], ImageReaderFactory.getDefault().get(NON_PUBLIC_UIDS[i]));
            assertSame(existingWriters[i], ImageWriterFactory.getDefault().get(NON_PUBLIC_UIDS[i]));
        }
    }
}
