package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.image.DicomImageTypes;
import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReader;
import io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal.JpegLsFrameCodec;

class JpegLsImageReader extends AbstractDicomImageReader {
    JpegLsImageReader(ImageReaderSpi provider) {
        super(provider);
    }

    @Override
    protected BufferedImage readFrame(ImageDescriptor descriptor,
            ImageInputStream input, ImageReadParam param) throws IOException {
        validateReadParam(param);
        byte[] encoded = readRemaining(input);
        JpegLsFrameCodec.DecodedFrame frame = JpegLsFrameCodec.decode(encoded);
        if (frame.width() != descriptor.getColumns() || frame.height() != descriptor.getRows()
                || frame.precision() != descriptor.getBitsStored()
                || frame.components() != descriptor.getSamples()) {
            throw new IIOException("JPEG-LS frame header does not match DICOM descriptor");
        }
        BufferedImage image = destination(descriptor, param);
        JpegLsRasterFrames.copyToImage(descriptor, frame.samples(), image);
        return image;
    }

    private static BufferedImage destination(ImageDescriptor descriptor, ImageReadParam param) {
        if (param != null && param.getDestination() != null) return param.getDestination();
        if (param != null && param.getDestinationType() != null) {
            return param.getDestinationType().createBufferedImage(
                    descriptor.getColumns(), descriptor.getRows());
        }
        return DicomImageTypes.createImage(descriptor);
    }

    private static void validateReadParam(ImageReadParam param) throws IIOException {
        if (param == null) return;
        Point offset = param.getDestinationOffset();
        if (param.getSourceRegion() != null || param.getSourceXSubsampling() != 1
                || param.getSourceYSubsampling() != 1 || offset.x != 0 || offset.y != 0
                || param.getSourceBands() != null || param.getDestinationBands() != null) {
            throw new IIOException("JPEG-LS reader does not support regions, subsampling, "
                    + "offsets, or band selection yet");
        }
    }

    static byte[] readRemaining(ImageInputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                int value = input.read();
                if (value < 0) break;
                bytes.write(value);
            } else {
                bytes.write(buffer, 0, read);
            }
        }
        return bytes.toByteArray();
    }
}
