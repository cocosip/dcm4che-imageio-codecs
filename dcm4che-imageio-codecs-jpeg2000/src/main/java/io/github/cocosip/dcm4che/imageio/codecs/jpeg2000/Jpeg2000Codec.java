package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.io.IOException;

import io.github.cocosip.dcm4che.imageio.codecs.core.registry.CodecRegistrations;

/** Registers the classic JPEG 2000 transfer syntaxes with dcm4che. */
public final class Jpeg2000Codec {
    public static final String LOSSLESS_TRANSFER_SYNTAX_UID = "1.2.840.10008.1.2.4.90";
    public static final String LOSSY_TRANSFER_SYNTAX_UID = "1.2.840.10008.1.2.4.91";

    private Jpeg2000Codec() {
    }

    public static void register() throws IOException {
        CodecRegistrations.load(Jpeg2000Codec.class, "readers.properties", "writers.properties");
    }
}
