package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import javax.imageio.spi.ImageReaderSpi;

/** Syntax-bound .91 reader; classic packet decoding is shared with .90. */
public final class Jpeg2000LossyImageReader extends Jpeg2000LosslessImageReader {
    public Jpeg2000LossyImageReader(ImageReaderSpi provider) {
        super(provider);
    }

    @Override
    protected boolean requireReversibleTransform() {
        return false;
    }
}
