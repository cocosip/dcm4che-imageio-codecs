package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

public final class Htj2kLosslessRpclImageReaderSpi extends Htj2kImageReaderSpi {
    public Htj2kLosslessRpclImageReaderSpi() {
        super("htj2k-lossless-rpcl", Htj2kLosslessRpclImageReader.class,
                Htj2kLosslessRpclImageWriterSpi.class);
    }

    @Override
    protected Htj2kLosslessRpclImageReader newReader() {
        return new Htj2kLosslessRpclImageReader(this);
    }
}
