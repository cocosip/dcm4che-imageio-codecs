package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

public final class Htj2kLosslessImageWriterSpi extends Htj2kImageWriterSpi {
    public Htj2kLosslessImageWriterSpi() {
        super("htj2k-lossless", Htj2kLosslessImageWriter.class,
                Htj2kLosslessImageReaderSpi.class);
    }

    @Override
    protected Htj2kLosslessImageWriter newWriter() {
        return new Htj2kLosslessImageWriter(this);
    }
}
