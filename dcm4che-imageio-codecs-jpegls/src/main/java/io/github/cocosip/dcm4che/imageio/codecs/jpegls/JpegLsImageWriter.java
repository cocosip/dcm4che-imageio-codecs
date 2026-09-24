package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import java.awt.image.RenderedImage;
import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.ImageWriteParam;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageWriter;
import io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal.JpegLsFrameCodec;

class JpegLsImageWriter extends AbstractDicomImageWriter {
    private final boolean lossless;

    JpegLsImageWriter(ImageWriterSpi provider, boolean lossless) {
        super(provider);
        this.lossless = lossless;
    }

    @Override
    public ImageWriteParam getDefaultWriteParam() {
        JpegLsImageWriteParam param = new JpegLsImageWriteParam(getLocale());
        if (!lossless) param.setAllowedError(2);
        return param;
    }

    @Override
    protected void writeFrame(ImageDescriptor descriptor, RenderedImage image,
            ImageOutputStream output, ImageWriteParam param) throws IOException {
        if (param != null && param.getCompressionMode() == ImageWriteParam.MODE_DISABLED) {
            throw new IIOException("JPEG-LS compression cannot be disabled");
        }
        int allowedError = lossless ? 0 : 2;
        if (param instanceof JpegLsImageWriteParam) {
            allowedError = ((JpegLsImageWriteParam) param).getAllowedError();
        }
        if (lossless && allowedError != 0) {
            throw new IIOException("JPEG-LS Lossless requires allowedError=0");
        }
        int[] samples = JpegLsRasterFrames.toSamples(descriptor, image,
                descriptor.isSigned() && allowedError > 0);
        int precision = descriptor.getBitsStored();
        int interleave = descriptor.getSamples() == 1 ? 0 : 2;
        if (descriptor.getSamples() > 1 && param instanceof JpegLsImageWriteParam) {
            interleave = ((JpegLsImageWriteParam) param).getInterleaveMode();
        }
        int restartInterval = param instanceof JpegLsImageWriteParam
                ? ((JpegLsImageWriteParam) param).getRestartInterval() : 0;
        java.util.List<JpegLsMappingTable> mappingTables = java.util.Collections.emptyList();
        java.util.Map<Integer, Integer> mappingSelectors = java.util.Collections.emptyMap();
        if (param instanceof JpegLsImageWriteParam) {
            JpegLsImageWriteParam jpeglsParam = (JpegLsImageWriteParam) param;
            mappingTables = jpeglsParam.getMappingTables();
            mappingSelectors = jpeglsParam.getComponentMappingTableSelectors();
        }
        output.write(JpegLsFrameCodec.encode(descriptor.getColumns(), descriptor.getRows(),
                precision, descriptor.getSamples(), allowedError, interleave,
                restartInterval, samples, mappingTables, mappingSelectors));
    }
}
