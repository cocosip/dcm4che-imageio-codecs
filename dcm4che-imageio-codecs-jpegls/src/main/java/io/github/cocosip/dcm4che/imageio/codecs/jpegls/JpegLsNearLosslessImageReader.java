package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import javax.imageio.spi.ImageReaderSpi;

public final class JpegLsNearLosslessImageReader extends JpegLsImageReader {
    public JpegLsNearLosslessImageReader(ImageReaderSpi provider) {
        super(provider, false);
    }
}
