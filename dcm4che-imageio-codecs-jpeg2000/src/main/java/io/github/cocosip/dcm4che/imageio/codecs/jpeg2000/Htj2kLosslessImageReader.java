package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import javax.imageio.spi.ImageReaderSpi;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k.Htj2kFrameCodec;

public final class Htj2kLosslessImageReader extends Htj2kImageReader {
    public Htj2kLosslessImageReader(ImageReaderSpi provider) {
        super(provider, Htj2kFrameCodec.LOSSLESS_UID);
    }
}
