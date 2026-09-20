package io.github.cocosip.dcm4che.imageio.codecs.rle;

import java.io.IOException;

import io.github.cocosip.dcm4che.imageio.codecs.core.registry.CodecRegistrations;

public final class RleCodec {
    public static final String TRANSFER_SYNTAX_UID = "1.2.840.10008.1.2.5";

    private RleCodec() {
    }

    public static void register() throws IOException {
        CodecRegistrations.load(RleCodec.class, "readers.properties", "writers.properties");
    }
}
