package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class JpegLsDicomIntegrationTest {
    private static ImageReaderFactory.ImageReaderParam previousLosslessReader;
    private static ImageWriterFactory.ImageWriterParam previousLosslessWriter;
    private static ImageReaderFactory.ImageReaderParam previousNearReader;
    private static ImageWriterFactory.ImageWriterParam previousNearWriter;

    @BeforeAll
    static void registerCodec() throws Exception {
        previousLosslessReader = ImageReaderFactory.getDefault()
                .get(JpegLsCodec.LOSSLESS_TRANSFER_SYNTAX_UID);
        previousLosslessWriter = ImageWriterFactory.getDefault()
                .get(JpegLsCodec.LOSSLESS_TRANSFER_SYNTAX_UID);
        previousNearReader = ImageReaderFactory.getDefault()
                .get(JpegLsCodec.NEAR_LOSSLESS_TRANSFER_SYNTAX_UID);
        previousNearWriter = ImageWriterFactory.getDefault()
                .get(JpegLsCodec.NEAR_LOSSLESS_TRANSFER_SYNTAX_UID);
        JpegLsCodec.register();
    }

    @AfterAll
    static void restoreFactories() {
        restore(ImageReaderFactory.getDefault(), JpegLsCodec.LOSSLESS_TRANSFER_SYNTAX_UID,
                previousLosslessReader);
        restore(ImageWriterFactory.getDefault(), JpegLsCodec.LOSSLESS_TRANSFER_SYNTAX_UID,
                previousLosslessWriter);
        restore(ImageReaderFactory.getDefault(), JpegLsCodec.NEAR_LOSSLESS_TRANSFER_SYNTAX_UID,
                previousNearReader);
        restore(ImageWriterFactory.getDefault(), JpegLsCodec.NEAR_LOSSLESS_TRANSFER_SYNTAX_UID,
                previousNearWriter);
    }

    @Test
    void compressorAndDecompressorRoundTripLosslessMonochrome8() throws Exception {
        assertDicomRoundTrip(2, 3, 1, 8, false, 1, UID.JPEGLSLossless,
                bytes(1, 2, 3, 4, 5, 6), 0);
    }

    @Test
    void compressorAndDecompressorRoundTripLosslessMultipleFrames() throws Exception {
        assertDicomRoundTrip(1, 3, 1, 8, false, 2, UID.JPEGLSLossless,
                bytes(1, 2, 3, 4, 5, 6), 0);
    }

    @Test
    void compressorAndDecompressorRoundTripOddLengthEightBitPixelData() throws Exception {
        assertDicomRoundTrip(1, 1, 1, 8, false, 1, UID.JPEGLSLossless,
                bytes(0x7f), 0);
    }

    @Test
    void compressorAndDecompressorRoundTripNearLosslessMonochrome8() throws Exception {
        assertDicomRoundTrip(1, 4, 1, 8, false, 1, UID.JPEGLSNearLossless,
                bytes(10, 20, 30, 40), 2);
    }

    @Test
    void compressorAndDecompressorRoundTripNearLosslessUnsignedMonochrome16() throws Exception {
        assertDicomRoundTrip(1, 4, 1, 16, false, 1, UID.JPEGLSNearLossless,
                bytes(0x00, 0x00, 0x01, 0x00, 0x00, 0x80, 0xff, 0xff), 2);
    }

    @Test
    void compressorAndDecompressorRoundTripSignedAndUnsignedMonochrome16() throws Exception {
        byte[] raw = bytes(0x01, 0x80, 0xff, 0xff, 0x34, 0x12, 0xcd, 0xab);
        assertDicomRoundTrip(2, 2, 1, 16, true, 1, UID.JPEGLSLossless, raw, 0);
        assertDicomRoundTrip(2, 2, 1, 16, false, 1, UID.JPEGLSLossless, raw, 0);
    }

    @Test
    void compressorAndDecompressorRoundTripRgbLayouts() throws Exception {
        assertDicomRoundTrip(1, 2, 3, 8, false, 1, UID.JPEGLSLossless,
                bytes(1, 2, 3, 4, 5, 6), 0);
        assertDicomRoundTrip(1, 2, 3, 8, false, 1, UID.JPEGLSLossless,
                bytes(1, 4, 2, 5, 3, 6), 0, true);
    }

    @Test
    void compressorAndDecompressorPreservePaletteLookupTables() throws Exception {
        File nativeFile = Files.createTempFile("dcm4che-jpegls-palette-native-", ".dcm").toFile();
        File compressedFile = Files.createTempFile("dcm4che-jpegls-palette-compressed-", ".dcm").toFile();
        try {
            byte[] red = bytes(0, 0, 100, 0, (byte) 0xff, 0, 0x34, 0x12);
            byte[] green = bytes(0, 0, 0, 0, 0, 0, 0x78, 0x56);
            byte[] blue = bytes(0, 0, 0, 0, 0, 0, (byte) 0xbc, 0x9a);
            Attributes source = paletteDataset(bytes(0, 1, 2, 3), red, green, blue);
            writeDicom(nativeFile, source, UID.ExplicitVRLittleEndian);
            Attributes bulkDataSource = readDicom(nativeFile);

            try (Compressor compressor = new Compressor(
                    bulkDataSource, UID.ExplicitVRLittleEndian)) {
                assertTrue(compressor.compress(UID.JPEGLSLossless));
                writeDicom(compressedFile, bulkDataSource, UID.JPEGLSLossless);
            }

            Attributes compressed = readDicom(compressedFile);
            assertPaletteLookupTables(compressed, red, green, blue);
            Decompressor decompressor = new Decompressor(compressed, UID.JPEGLSLossless);
            assertTrue(decompressor.decompress());
            assertPaletteLookupTables(compressed, red, green, blue);
        } finally {
            Files.deleteIfExists(nativeFile.toPath());
            Files.deleteIfExists(compressedFile.toPath());
        }
    }

    private static void assertDicomRoundTrip(int rows, int columns, int samples,
            int bitsAllocated, boolean signed, int frames, String transferSyntax,
            byte[] raw, int allowedError) throws Exception {
        assertDicomRoundTrip(rows, columns, samples, bitsAllocated, signed, frames,
                transferSyntax, raw, allowedError, false);
    }

    private static void assertDicomRoundTrip(int rows, int columns, int samples,
            int bitsAllocated, boolean signed, int frames, String transferSyntax,
            byte[] raw, int allowedError, boolean planar) throws Exception {
        File nativeFile = Files.createTempFile("dcm4che-jpegls-native-", ".dcm").toFile();
        File compressedFile = Files.createTempFile("dcm4che-jpegls-compressed-", ".dcm").toFile();
        try {
            Attributes source = dataset(rows, columns, samples, bitsAllocated, signed, planar,
                    frames, raw);
            writeDicom(nativeFile, source, UID.ExplicitVRLittleEndian);
            Attributes bulkDataSource = readDicom(nativeFile);

            try (Compressor compressor = new Compressor(
                    bulkDataSource, UID.ExplicitVRLittleEndian)) {
                assertTrue(compressor.compress(transferSyntax));
                if (UID.JPEGLSLossless.equals(transferSyntax)) {
                    assertInstanceOf(JpegLsLosslessImageWriter.class,
                            ImageWriterFactory.getImageWriter(ImageWriterFactory
                                    .getImageWriterParam(transferSyntax)));
                } else {
                    assertInstanceOf(JpegLsNearLosslessImageWriter.class,
                            ImageWriterFactory.getImageWriter(ImageWriterFactory
                                    .getImageWriterParam(transferSyntax)));
                }
                writeDicom(compressedFile, bulkDataSource, transferSyntax);
            }

            Attributes compressed = readDicom(compressedFile);
            Decompressor decompressor = new Decompressor(compressed, transferSyntax);
            assertTrue(decompressor.decompress());
            if (UID.JPEGLSLossless.equals(transferSyntax)) {
                assertInstanceOf(JpegLsLosslessImageReader.class,
                        ImageReaderFactory.getImageReader(ImageReaderFactory
                                .getImageReaderParam(transferSyntax)));
            } else {
                assertInstanceOf(JpegLsNearLosslessImageReader.class,
                        ImageReaderFactory.getImageReader(ImageReaderFactory
                                .getImageReaderParam(transferSyntax)));
            }

            byte[] decoded = compressed.getBytes(Tag.PixelData);
            byte[] expectedRaw = planar
                    ? interleavedFromPlanar(raw, rows * columns, samples, frames) : raw;
            byte[] expected = expectedRaw.length % 2 == 0
                    ? expectedRaw : Arrays.copyOf(expectedRaw, expectedRaw.length + 1);
            if (allowedError == 0) {
                assertArrayEquals(expected, decoded);
            } else {
                int bytesPerSample = bitsAllocated / 8;
                for (int offset = 0; offset < raw.length; offset += bytesPerSample) {
                    int expectedSample = sample(raw, offset, bytesPerSample, signed);
                    int actualSample = sample(decoded, offset, bytesPerSample, signed);
                    assertTrue(Math.abs(expectedSample - actualSample) <= allowedError,
                            "sample " + (offset / bytesPerSample)
                                    + " exceeds JPEG-LS near-lossless error");
                }
            }
        } finally {
            Files.deleteIfExists(nativeFile.toPath());
            Files.deleteIfExists(compressedFile.toPath());
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
        if (samples > 1) attributes.setInt(Tag.PlanarConfiguration, VR.US, planar ? 1 : 0);
        if (frames > 1) attributes.setInt(Tag.NumberOfFrames, VR.IS, frames);
        attributes.setBytes(Tag.PixelData, bitsAllocated == 8 ? VR.OB : VR.OW, raw);
        return attributes;
    }

    private static Attributes paletteDataset(byte[] pixels, byte[] red, byte[] green, byte[] blue) {
        Attributes attributes = dataset(1, pixels.length, 1, 8, false, false, 1, pixels);
        attributes.setString(Tag.PhotometricInterpretation, VR.CS, "PALETTE COLOR");
        attributes.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, 4, 0, 16);
        attributes.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, 4, 0, 16);
        attributes.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, 4, 0, 16);
        attributes.setBytes(Tag.RedPaletteColorLookupTableData, VR.OW, red);
        attributes.setBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, green);
        attributes.setBytes(Tag.BluePaletteColorLookupTableData, VR.OW, blue);
        return attributes;
    }

    private static void assertPaletteLookupTables(Attributes attributes,
            byte[] red, byte[] green, byte[] blue) throws IOException {
        assertArrayEquals(red, attributes.getBytes(Tag.RedPaletteColorLookupTableData));
        assertArrayEquals(green, attributes.getBytes(Tag.GreenPaletteColorLookupTableData));
        assertArrayEquals(blue, attributes.getBytes(Tag.BluePaletteColorLookupTableData));
        assertArrayEquals(new int[] {4, 0, 16},
                attributes.getInts(Tag.RedPaletteColorLookupTableDescriptor));
        assertArrayEquals(new int[] {4, 0, 16},
                attributes.getInts(Tag.GreenPaletteColorLookupTableDescriptor));
        assertArrayEquals(new int[] {4, 0, 16},
                attributes.getInts(Tag.BluePaletteColorLookupTableDescriptor));
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

    private static void restore(ImageReaderFactory factory, String uid,
            ImageReaderFactory.ImageReaderParam previous) {
        if (previous == null) factory.remove(uid);
        else factory.put(uid, previous);
    }

    private static void restore(ImageWriterFactory factory, String uid,
            ImageWriterFactory.ImageWriterParam previous) {
        if (previous == null) factory.remove(uid);
        else factory.put(uid, previous);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) result[index] = (byte) values[index];
        return result;
    }

    private static byte[] interleavedFromPlanar(byte[] source, int pixelsPerFrame,
            int samples, int frames) {
        byte[] result = new byte[source.length];
        int frameLength = pixelsPerFrame * samples;
        for (int frame = 0; frame < frames; frame++) {
            int frameOffset = frame * frameLength;
            for (int pixel = 0; pixel < pixelsPerFrame; pixel++) {
                for (int sample = 0; sample < samples; sample++) {
                    result[frameOffset + pixel * samples + sample] =
                            source[frameOffset + sample * pixelsPerFrame + pixel];
                }
            }
        }
        return result;
    }

    private static int sample(byte[] bytes, int offset, int bytesPerSample, boolean signed) {
        if (bytesPerSample == 1) {
            int value = bytes[offset] & 0xff;
            return signed && value >= 0x80 ? value - 0x100 : value;
        }
        int value = (bytes[offset] & 0xff) | (bytes[offset + 1] & 0xff) << 8;
        return signed && value >= 0x8000 ? value - 0x10000 : value;
    }
}
