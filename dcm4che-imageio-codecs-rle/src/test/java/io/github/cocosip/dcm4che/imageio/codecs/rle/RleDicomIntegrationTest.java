package io.github.cocosip.dcm4che.imageio.codecs.rle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.Compressor;
import org.dcm4che3.imageio.codec.Decompressor;
import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.ImageWriterFactory;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RleDicomIntegrationTest {
    private static ImageReaderFactory.ImageReaderParam previousReader;
    private static ImageWriterFactory.ImageWriterParam previousWriter;

    @BeforeAll
    static void registerCodec() throws Exception {
        previousReader = ImageReaderFactory.getDefault().get(UID.RLELossless);
        previousWriter = ImageWriterFactory.getDefault().get(UID.RLELossless);
        RleCodec.register();
    }

    @AfterAll
    static void restoreFactories() {
        restore(ImageReaderFactory.getDefault(), previousReader);
        restore(ImageWriterFactory.getDefault(), previousWriter);
    }

    @Test
    void compressorAndDecompressorRoundTripMonochrome8() throws Exception {
        assertDicomRoundTrip(2, 3, 1, 8, false, false, 1,
                bytes(1, 2, 3, 4, 5, 6));
    }

    @Test
    void compressorAndDecompressorRoundTripSignedAndUnsignedMonochrome16()
            throws Exception {
        byte[] raw = bytes(0x01, 0x80, 0xff, 0xff, 0x34, 0x12, 0xcd, 0xab);
        assertDicomRoundTrip(2, 2, 1, 16, true, false, 1, raw);
        assertDicomRoundTrip(2, 2, 1, 16, false, false, 1, raw);
    }

    @Test
    void compressorAndDecompressorRoundTripRgbLayouts() throws Exception {
        byte[] interleaved = bytes(1, 2, 3, 4, 5, 6);
        byte[] planar = bytes(1, 4, 2, 5, 3, 6);
        assertDicomRoundTrip(1, 2, 3, 8, false, false, 1, interleaved);
        assertDicomRoundTrip(1, 2, 3, 8, false, true, 1, planar);
    }

    @Test
    void compressorAndDecompressorRoundTripMultipleFrames() throws Exception {
        assertDicomRoundTrip(1, 3, 1, 8, false, false, 2,
                bytes(1, 2, 3, 4, 5, 6));
    }

    private static void assertDicomRoundTrip(int rows, int columns, int samples,
            int bitsAllocated, boolean signed, boolean planar, int frames, byte[] raw)
            throws Exception {
        File nativeFile = Files.createTempFile("dcm4che-rle-native-", ".dcm").toFile();
        File rleFile = Files.createTempFile("dcm4che-rle-compressed-", ".dcm").toFile();
        try {
            Attributes source = dataset(rows, columns, samples, bitsAllocated,
                    signed, planar, frames, raw);
            writeDicom(nativeFile, source, UID.ExplicitVRLittleEndian);
            Attributes bulkDataSource = readDicom(nativeFile);

            try (Compressor compressor = new Compressor(
                    bulkDataSource, UID.ExplicitVRLittleEndian)) {
                assertTrue(compressor.compress(UID.RLELossless));
                assertInstanceOf(RleImageWriter.class,
                        ImageWriterFactory.getImageWriter(
                                ImageWriterFactory.getImageWriterParam(UID.RLELossless)));
                writeDicom(rleFile, bulkDataSource, UID.RLELossless);
            }

            Attributes compressed = readDicom(rleFile);
            Decompressor decompressor = new Decompressor(compressed, UID.RLELossless);
            assertTrue(decompressor.decompress());
            assertInstanceOf(RleImageReader.class,
                    ImageReaderFactory.getImageReader(
                            ImageReaderFactory.getImageReaderParam(UID.RLELossless)));

            byte[] decoded = compressed.getBytes(Tag.PixelData);
            byte[] expectedPixels = samples > 1 && !planar
                    ? interleavedToPlanar(raw, rows * columns, samples, frames)
                    : raw;
            byte[] expected = expectedPixels.length % 2 == 0
                    ? expectedPixels : Arrays.copyOf(expectedPixels, expectedPixels.length + 1);
            assertArrayEquals(expected, decoded);
            if (samples > 1) {
                assertEquals(1, compressed.getInt(Tag.PlanarConfiguration, -1));
            }
        } finally {
            Files.deleteIfExists(nativeFile.toPath());
            Files.deleteIfExists(rleFile.toPath());
        }
    }

    private static Attributes dataset(int rows, int columns, int samples,
            int bitsAllocated, boolean signed, boolean planar, int frames, byte[] raw) {
        Attributes attributes = new Attributes();
        attributes.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        attributes.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        attributes.setInt(Tag.Rows, VR.US, rows);
        attributes.setInt(Tag.Columns, VR.US, columns);
        attributes.setInt(Tag.SamplesPerPixel, VR.US, samples);
        attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
        attributes.setInt(Tag.BitsStored, VR.US, bitsAllocated);
        attributes.setInt(Tag.HighBit, VR.US, bitsAllocated - 1);
        attributes.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
        attributes.setString(Tag.PhotometricInterpretation, VR.CS,
                samples == 1 ? "MONOCHROME2" : "RGB");
        if (samples > 1) {
            attributes.setInt(Tag.PlanarConfiguration, VR.US, planar ? 1 : 0);
        }
        if (frames > 1) {
            attributes.setInt(Tag.NumberOfFrames, VR.IS, frames);
        }
        attributes.setBytes(Tag.PixelData, bitsAllocated == 8 ? VR.OB : VR.OW, raw);
        return attributes;
    }

    private static void writeDicom(File file, Attributes dataset, String transferSyntax)
            throws IOException {
        try (DicomOutputStream output = new DicomOutputStream(file)) {
            output.writeDataset(dataset.createFileMetaInformation(transferSyntax), dataset);
        }
    }

    private static Attributes readDicom(File file) throws IOException {
        try (DicomInputStream input = new DicomInputStream(file)) {
            input.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
            return input.readDataset();
        }
    }

    private static void restore(ImageReaderFactory factory,
            ImageReaderFactory.ImageReaderParam previous) {
        if (previous == null) {
            factory.remove(UID.RLELossless);
        } else {
            factory.put(UID.RLELossless, previous);
        }
    }

    private static void restore(ImageWriterFactory factory,
            ImageWriterFactory.ImageWriterParam previous) {
        if (previous == null) {
            factory.remove(UID.RLELossless);
        } else {
            factory.put(UID.RLELossless, previous);
        }
    }

    private static byte[] interleavedToPlanar(
            byte[] source, int pixelsPerFrame, int samples, int frames) {
        byte[] result = new byte[source.length];
        int frameLength = pixelsPerFrame * samples;
        for (int frame = 0; frame < frames; frame++) {
            int frameOffset = frame * frameLength;
            for (int pixel = 0; pixel < pixelsPerFrame; pixel++) {
                for (int sample = 0; sample < samples; sample++) {
                    result[frameOffset + sample * pixelsPerFrame + pixel] =
                            source[frameOffset + pixel * samples + sample];
                }
            }
        }
        return result;
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
