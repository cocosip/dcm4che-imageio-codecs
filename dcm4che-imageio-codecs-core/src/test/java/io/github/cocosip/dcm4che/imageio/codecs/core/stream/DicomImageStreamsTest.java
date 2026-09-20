package io.github.cocosip.dcm4che.imageio.codecs.core.stream;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.BytesWithImageImageDescriptor;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.dcm4che3.imageio.stream.SegmentedInputImageStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DicomImageStreamsTest {

    @Test
    void extractsDescriptorFromSegmentedInputStream() throws IOException {
        ImageDescriptor descriptor = descriptor();
        Fragments fragments = new Fragments(VR.OB, false, 2);
        fragments.add(null);
        fragments.add(new byte[] { 0 });
        SegmentedInputImageStream stream = new SegmentedInputImageStream(null, fragments, 0);
        stream.setImageDescriptor(descriptor);

        assertSame(descriptor, DicomImageStreams.requireDescriptor(stream));
    }

    @Test
    void extractsDescriptorFromDescriptorCarrier() {
        ImageDescriptor descriptor = descriptor();

        assertSame(descriptor, DicomImageStreams.requireDescriptor(new DescriptorCarrier(descriptor)));
    }

    @Test
    void rejectsMissingDescriptor() {
        assertThrows(IllegalArgumentException.class,
                () -> DicomImageStreams.requireDescriptor(new DescriptorCarrier(null)));
    }

    @Test
    void rejectsUnsupportedObject() {
        assertThrows(IllegalArgumentException.class,
                () -> DicomImageStreams.requireDescriptor(new Object()));
    }

    private static ImageDescriptor descriptor() {
        Attributes attributes = new Attributes();
        attributes.setInt(Tag.Rows, VR.US, 1);
        attributes.setInt(Tag.Columns, VR.US, 1);
        attributes.setInt(Tag.SamplesPerPixel, VR.US, 1);
        attributes.setInt(Tag.BitsAllocated, VR.US, 8);
        attributes.setInt(Tag.BitsStored, VR.US, 8);
        return new ImageDescriptor(attributes);
    }

    private static final class DescriptorCarrier implements BytesWithImageImageDescriptor {
        private final ImageDescriptor descriptor;

        private DescriptorCarrier(ImageDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public ByteBuffer getBytes() {
            return ByteBuffer.allocate(0);
        }

        @Override
        public ImageDescriptor getImageDescriptor() {
            return descriptor;
        }
    }
}
