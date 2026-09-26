package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

public final class Htj2kLosslessImageReaderSpi extends Htj2kImageReaderSpi {
    public Htj2kLosslessImageReaderSpi() {
        super("htj2k-lossless", Htj2kLosslessImageReader.class,
                Htj2kLosslessImageWriterSpi.class);
    }

    @Override
    protected Htj2kLosslessImageReader newReader() {
        return new Htj2kLosslessImageReader(this);
    }
}
