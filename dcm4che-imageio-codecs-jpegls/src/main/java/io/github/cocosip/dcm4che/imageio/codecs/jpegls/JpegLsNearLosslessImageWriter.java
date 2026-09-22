package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import javax.imageio.spi.ImageWriterSpi;

public final class JpegLsNearLosslessImageWriter extends JpegLsImageWriter {
    public JpegLsNearLosslessImageWriter(ImageWriterSpi provider) {
        super(provider, false);
    }
}
