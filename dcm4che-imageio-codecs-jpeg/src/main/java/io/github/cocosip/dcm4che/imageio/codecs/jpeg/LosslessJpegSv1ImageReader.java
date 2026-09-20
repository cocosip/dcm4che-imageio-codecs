package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import javax.imageio.spi.ImageReaderSpi;

public final class LosslessJpegSv1ImageReader extends LosslessJpegImageReader {
    public LosslessJpegSv1ImageReader(ImageReaderSpi provider) {
        super(provider, 1);
    }
}
