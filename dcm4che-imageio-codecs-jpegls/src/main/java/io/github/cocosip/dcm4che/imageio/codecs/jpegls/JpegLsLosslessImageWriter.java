package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import javax.imageio.spi.ImageWriterSpi;

public final class JpegLsLosslessImageWriter extends JpegLsImageWriter {
    public JpegLsLosslessImageWriter(ImageWriterSpi provider) {
        super(provider, true);
    }
}
