package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import javax.imageio.spi.ImageWriterSpi;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k.Htj2kFrameCodec;

public final class Htj2kLosslessImageWriter extends Htj2kImageWriter {
    public Htj2kLosslessImageWriter(ImageWriterSpi provider) {
        super(provider, Htj2kFrameCodec.LOSSLESS_UID);
    }
}
