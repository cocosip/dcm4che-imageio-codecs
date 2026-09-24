package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import javax.imageio.spi.ImageReaderSpi;

public final class JpegLsLosslessImageReader extends JpegLsImageReader {
    public JpegLsLosslessImageReader(ImageReaderSpi provider) {
        super(provider, true);
    }
}
