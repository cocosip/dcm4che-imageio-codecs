package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.awt.image.RenderedImage;
import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.ImageWriteParam;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriter;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k.Htj2kFrameCodec;

abstract class Htj2kImageWriter extends AbstractDicomImageWriter {
    private final String transferSyntaxUid;

    Htj2kImageWriter(ImageWriterSpi provider, String transferSyntaxUid) {
        super(provider);
        this.transferSyntaxUid = transferSyntaxUid;
    }

    @Override
    public ImageWriteParam getDefaultWriteParam() {
        return new Htj2kImageWriteParam(transferSyntaxUid);
    }

    @Override
    protected void writeFrame(ImageDescriptor descriptor, RenderedImage image,
            ImageOutputStream output, ImageWriteParam param) throws IOException {
        Htj2kImageWriteParam options = param == null
                ? new Htj2kImageWriteParam(transferSyntaxUid)
                : requireOptions(param);
        if (!options.matches(transferSyntaxUid)) {
            throw new IIOException("HTJ2K write parameters belong to another transfer syntax");
        }
        if (options.getCompressionMode() == ImageWriteParam.MODE_DISABLED) {
            throw new IIOException("HTJ2K compression cannot be disabled");
        }
        if (options.getNumLayers() != 1) {
            throw new IIOException("HTJ2K compatibility profile requires one layer");
        }
        if (options.getTargetRatio() > 1) {
            if (!Htj2kFrameCodec.LOSSY_UID.equals(transferSyntaxUid)) {
                throw new IIOException("HTJ2K target ratio requires the lossy transfer syntax");
            }
        }
        Jpeg2000Raster raster = Jpeg2000RasterFrames.fromImage(descriptor, image);
        Htj2kFrameCodec codec = Htj2kFrameCodec.forTransferSyntax(transferSyntaxUid);
        output.write(options.getTargetRatio() > 1
                ? codec.encode(raster, options.getTargetRatio())
                : codec.encode(raster, options.getProgressionOrder()));
    }

    private static Htj2kImageWriteParam requireOptions(ImageWriteParam param)
            throws IIOException {
        if (!(param instanceof Htj2kImageWriteParam)) {
            throw new IIOException("HTJ2K writer requires Htj2kImageWriteParam");
        }
        return (Htj2kImageWriteParam) param;
    }
}
