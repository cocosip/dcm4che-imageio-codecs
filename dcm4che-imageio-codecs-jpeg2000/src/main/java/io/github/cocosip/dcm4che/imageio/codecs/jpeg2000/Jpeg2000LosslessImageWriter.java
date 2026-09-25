package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import java.awt.image.RenderedImage;
import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.ImageWriteParam;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriter;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic.Jpeg2000LosslessCodec;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

public final class Jpeg2000LosslessImageWriter extends AbstractDicomImageWriter {
    public Jpeg2000LosslessImageWriter(ImageWriterSpi provider) {
        super(provider);
    }

    @Override
    public ImageWriteParam getDefaultWriteParam() {
        return new Jpeg2000ImageWriteParam(true);
    }

    @Override
    protected void writeFrame(ImageDescriptor descriptor, RenderedImage image,
            ImageOutputStream output, ImageWriteParam param) throws IOException {
        if (param != null && param.canWriteCompressed()
                && param.getCompressionMode() == ImageWriteParam.MODE_DISABLED) {
            throw new IIOException("JPEG 2000 compression cannot be disabled");
        }
        Jpeg2000ImageWriteParam options = param == null
                ? new Jpeg2000ImageWriteParam(true)
                : requireOptions(param);
        if (!options.isLosslessSyntax()) {
            throw new IIOException("JPEG 2000 Lossless writer requires lossless write parameters");
        }
        double[] layers = options.resolveLayerRatios(
                descriptor.getBitsStored(), descriptor.getBitsAllocated());
        Jpeg2000Raster raster = Jpeg2000RasterFrames.fromImage(descriptor, image);
        if (options.isEncodeSignedAsUnsigned()) {
            raster = Jpeg2000RasterFrames.asUnsignedCodes(raster);
        }
        output.write(Jpeg2000LosslessCodec.encode(
                raster, false,
                layers, options.getProgressionOrder()));
    }

    static Jpeg2000ImageWriteParam requireOptions(ImageWriteParam param) throws IIOException {
        if (!(param instanceof Jpeg2000ImageWriteParam)) {
            throw new IIOException("JPEG 2000 writer requires Jpeg2000ImageWriteParam");
        }
        return (Jpeg2000ImageWriteParam) param;
    }
}
