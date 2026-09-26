package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

public final class Htj2kLossyImageWriterSpi extends Htj2kImageWriterSpi {
    public Htj2kLossyImageWriterSpi() {
        super("htj2k-lossy", Htj2kLossyImageWriter.class,
                Htj2kLossyImageReaderSpi.class);
    }

    @Override
    protected Htj2kLossyImageWriter newWriter() {
        return new Htj2kLossyImageWriter(this);
    }
}
