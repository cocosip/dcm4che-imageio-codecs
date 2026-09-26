package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

public final class Htj2kLosslessRpclImageWriterSpi extends Htj2kImageWriterSpi {
    public Htj2kLosslessRpclImageWriterSpi() {
        super("htj2k-lossless-rpcl", Htj2kLosslessRpclImageWriter.class,
                Htj2kLosslessRpclImageReaderSpi.class);
    }

    @Override
    protected Htj2kLosslessRpclImageWriter newWriter() {
        return new Htj2kLosslessRpclImageWriter(this);
    }
}
