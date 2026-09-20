package io.github.cocosip.dcm4che.imageio.codecs.core.stream;

import org.dcm4che3.imageio.codec.BytesWithImageImageDescriptor;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.dcm4che3.imageio.stream.SegmentedInputImageStream;

/** Extracts DICOM image metadata from the stream contracts used by dcm4che. */
public final class DicomImageStreams {

    private DicomImageStreams() {
    }

    public static ImageDescriptor requireDescriptor(Object stream) {
        ImageDescriptor descriptor;
        if (stream instanceof SegmentedInputImageStream) {
            descriptor = ((SegmentedInputImageStream) stream).getImageDescriptor();
        } else if (stream instanceof BytesWithImageImageDescriptor) {
            descriptor = ((BytesWithImageImageDescriptor) stream).getImageDescriptor();
        } else {
            throw new IllegalArgumentException(
                    "Stream does not expose a dcm4che ImageDescriptor: " + typeName(stream));
        }

        if (descriptor == null) {
            throw new IllegalArgumentException("Stream has no dcm4che ImageDescriptor");
        }
        return descriptor;
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }
}
