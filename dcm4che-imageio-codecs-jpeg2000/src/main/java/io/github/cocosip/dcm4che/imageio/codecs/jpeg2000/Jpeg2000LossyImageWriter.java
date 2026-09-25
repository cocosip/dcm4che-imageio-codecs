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
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

public final class Jpeg2000LossyImageWriter extends AbstractDicomImageWriter {
    public Jpeg2000LossyImageWriter(ImageWriterSpi provider) {
        super(provider);
    }

    @Override
    public ImageWriteParam getDefaultWriteParam() {
        return new Jpeg2000ImageWriteParam(false);
    }

    @Override
    protected void writeFrame(ImageDescriptor descriptor, RenderedImage image,
            ImageOutputStream output, ImageWriteParam param) throws IOException {
        Jpeg2000ImageWriteParam options = param == null
                ? new Jpeg2000ImageWriteParam(false)
                : Jpeg2000LosslessImageWriter.requireOptions(param);
        if (!options.isIrreversible() && descriptor.getSamples() == 3) {
            throw new IIOException("Reversible three-component .91 conflicts with dcm4che YBR_ICT metadata");
        }
        double[] layers = options.resolveLayerRatios(
                descriptor.getBitsStored(), descriptor.getBitsAllocated());
        Jpeg2000Raster raster = Jpeg2000RasterFrames.fromImage(descriptor, image);
        if (options.isEncodeSignedAsUnsigned()) {
            raster = Jpeg2000RasterFrames.asUnsignedCodes(raster);
        }
        output.write(Jpeg2000LosslessCodec.encode(
                raster,
                options.isIrreversible(), layers, options.getProgressionOrder()));
    }
}
