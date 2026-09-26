package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

public final class Htj2kLossyImageReaderSpi extends Htj2kImageReaderSpi {
    public Htj2kLossyImageReaderSpi() {
        super("htj2k-lossy", Htj2kLossyImageReader.class,
                Htj2kLossyImageWriterSpi.class);
    }

    @Override
    protected Htj2kLossyImageReader newReader() {
        return new Htj2kLossyImageReader(this);
    }
}
