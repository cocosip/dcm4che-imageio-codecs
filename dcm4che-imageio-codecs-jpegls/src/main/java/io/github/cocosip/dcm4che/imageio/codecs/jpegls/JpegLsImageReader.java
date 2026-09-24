package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import org.dcm4che3.imageio.codec.ImageDescriptor;

import io.github.cocosip.dcm4che.imageio.codecs.core.spi.AbstractDicomImageReader;
import io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal.JpegLsFrameCodec;

class JpegLsImageReader extends AbstractDicomImageReader {
    private final boolean lossless;

    JpegLsImageReader(ImageReaderSpi provider, boolean lossless) {
        super(provider);
        this.lossless = lossless;
    }

    @Override
    protected BufferedImage readFrame(ImageDescriptor descriptor,
            ImageInputStream input, ImageReadParam param) throws IOException {
        byte[] encoded = trimDicomFragmentPadding(readRemaining(input));
        JpegLsFrameCodec.DecodedFrame frame;
        try {
            frame = JpegLsFrameCodec.decode(encoded);
        } catch (IIOException e) {
            throw e;
        } catch (IOException e) {
            throw new IIOException("Malformed JPEG-LS codestream: " + e.getMessage(), e);
        }
        if (frame.width() != descriptor.getColumns() || frame.height() != descriptor.getRows()
                || frame.precision() != descriptor.getBitsStored()
                || frame.components() != descriptor.getSamples()) {
            throw new IIOException("JPEG-LS frame header does not match DICOM descriptor");
        }
        if (lossless && frame.nearLossless() != 0) {
            throw new IIOException("JPEG-LS Lossless reader received a non-zero NEAR frame");
        }
        if (frame.hasMappedOutput()) {
            throw new IIOException("JPEG-LS mapping table output cannot be represented by a DICOM Raster");
        }
        return JpegLsRasterFrames.toImage(descriptor, frame.samples(), param,
                descriptor.isSigned() && frame.nearLossless() > 0);
    }

    static byte[] readRemaining(ImageInputStream input) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
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

    private static byte[] trimDicomFragmentPadding(byte[] encoded) {
        if (encoded.length >= 3
                && encoded[encoded.length - 3] == (byte) 0xff
                && encoded[encoded.length - 2] == (byte) 0xd9
                && encoded[encoded.length - 1] == 0) {
            byte[] trimmed = new byte[encoded.length - 1];
            System.arraycopy(encoded, 0, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return encoded;
    }
}
