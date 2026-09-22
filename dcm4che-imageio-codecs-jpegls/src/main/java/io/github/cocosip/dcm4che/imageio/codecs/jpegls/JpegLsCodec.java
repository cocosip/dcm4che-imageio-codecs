package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import java.io.IOException;

import io.github.cocosip.dcm4che.imageio.codecs.core.registry.CodecRegistrations;

/** Registers the JPEG-LS reader and writer mappings with dcm4che. */
public final class JpegLsCodec {
    public static final String LOSSLESS_TRANSFER_SYNTAX_UID = "1.2.840.10008.1.2.4.80";
    public static final String NEAR_LOSSLESS_TRANSFER_SYNTAX_UID = "1.2.840.10008.1.2.4.81";

    private JpegLsCodec() {
    }

    public static void register() throws IOException {
        CodecRegistrations.load(JpegLsCodec.class, "readers.properties", "writers.properties");
    }
}
