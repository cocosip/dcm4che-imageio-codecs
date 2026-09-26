package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.io.IOException;

import io.github.cocosip.dcm4che.imageio.codecs.core.registry.CodecRegistrations;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k.Htj2kFrameCodec;

/** Registers the three HTJ2K transfer syntaxes with dcm4che. */
public final class Htj2kCodec {
    public static final String LOSSLESS_TRANSFER_SYNTAX_UID = Htj2kFrameCodec.LOSSLESS_UID;
    public static final String LOSSLESS_RPCL_TRANSFER_SYNTAX_UID = Htj2kFrameCodec.LOSSLESS_RPCL_UID;
    public static final String LOSSY_TRANSFER_SYNTAX_UID = Htj2kFrameCodec.LOSSY_UID;

    private Htj2kCodec() {
    }

    public static void register() throws IOException {
        CodecRegistrations.load(Htj2kCodec.class,
                "htj2k-readers.properties", "htj2k-writers.properties");
    }
}
