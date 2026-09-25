package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import javax.imageio.ImageIO;

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

class Jpeg2000DicomIntegrationTest {
    private static ImageReaderFactory.ImageReaderParam previousLosslessReader;
    private static ImageReaderFactory.ImageReaderParam previousLossyReader;
    private static ImageWriterFactory.ImageWriterParam previousLosslessWriter;
    private static ImageWriterFactory.ImageWriterParam previousLossyWriter;

    @BeforeAll
    static void register() throws Exception {
        ImageReaderFactory readers = ImageReaderFactory.getDefault();
        ImageWriterFactory writers = ImageWriterFactory.getDefault();
        previousLosslessReader = readers.get(Jpeg2000Codec.LOSSLESS_TRANSFER_SYNTAX_UID);
        previousLossyReader = readers.get(Jpeg2000Codec.LOSSY_TRANSFER_SYNTAX_UID);
        previousLosslessWriter = writers.get(Jpeg2000Codec.LOSSLESS_TRANSFER_SYNTAX_UID);
        previousLossyWriter = writers.get(Jpeg2000Codec.LOSSY_TRANSFER_SYNTAX_UID);
        Jpeg2000Codec.register();
    }

    @AfterAll
    static void restore() {
        restoreReader(Jpeg2000Codec.LOSSLESS_TRANSFER_SYNTAX_UID, previousLosslessReader);
        restoreReader(Jpeg2000Codec.LOSSY_TRANSFER_SYNTAX_UID, previousLossyReader);
        restoreWriter(Jpeg2000Codec.LOSSLESS_TRANSFER_SYNTAX_UID, previousLosslessWriter);
        restoreWriter(Jpeg2000Codec.LOSSY_TRANSFER_SYNTAX_UID, previousLossyWriter);
    }

    @Test
    void registersOnlyClassicSyntaxNames() {
        assertEquals("jpeg2000-lossless", ImageReaderFactory.getDefault()
                .get(Jpeg2000Codec.LOSSLESS_TRANSFER_SYNTAX_UID).formatName);
        assertEquals("jpeg2000-lossy", ImageReaderFactory.getDefault()
                .get(Jpeg2000Codec.LOSSY_TRANSFER_SYNTAX_UID).formatName);
        assertInstanceOf(Jpeg2000LosslessImageWriter.class, ImageWriterFactory.getImageWriter(
                ImageWriterFactory.getImageWriterParam(Jpeg2000Codec.LOSSLESS_TRANSFER_SYNTAX_UID)));
        assertInstanceOf(Jpeg2000LossyImageWriter.class, ImageWriterFactory.getImageWriter(
                ImageWriterFactory.getImageWriterParam(Jpeg2000Codec.LOSSY_TRANSFER_SYNTAX_UID)));
        assertTrue(ImageIO.getImageReadersByFormatName("jpeg2000-lossy").hasNext());
        assertTrue(ImageIO.getImageWritersByFormatName("jpeg2000-lossy").hasNext());
    }

    @Test
    void compressesAndDecompressesTwoLosslessFrames() throws Exception {
        byte[] pixels = new byte[2 * 35 * 37];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) (i * 31 + (i >>> 4));
        }
        byte[] decoded = transcode(pixels, 35, 37, 2,
                1, "MONOCHROME2", Jpeg2000Codec.LOSSLESS_TRANSFER_SYNTAX_UID, "MONOCHROME2");
        assertArrayEquals(pixels, decoded);
    }

    @Test
    void compressesAndDecompressesLossyFrame() throws Exception {
        byte[] pixels = new byte[64 * 65];
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 65; x++) {
                pixels[y * 65 + x] = (byte) (x + y);
            }
        }
        byte[] decoded = transcode(pixels, 64, 65, 1,
                1, "MONOCHROME2", Jpeg2000Codec.LOSSY_TRANSFER_SYNTAX_UID, "MONOCHROME2");
        for (int i = 0; i < pixels.length; i++) {
            int error = Math.abs((pixels[i] & 255) - (decoded[i] & 255));
            assertTrue(error <= 8, "lossy error at sample " + i + " is " + error);
        }
    }

    @Test
    void rgbMetadataMatchesReversibleAndIrreversibleMct() throws Exception {
        byte[] pixels = new byte[35 * 37 * 3];
        for (int y = 0; y < 35; y++) {
            for (int x = 0; x < 37; x++) {
                int offset = (y * 37 + x) * 3;
                pixels[offset] = (byte) (x + y);
                pixels[offset + 1] = (byte) (20 + x + y);
                pixels[offset + 2] = (byte) (40 + x + y);
            }
        }
        assertArrayEquals(Arrays.copyOf(pixels, pixels.length + 1),
                transcode(pixels, 35, 37, 1, 3, "RGB",
                Jpeg2000Codec.LOSSLESS_TRANSFER_SYNTAX_UID, "YBR_RCT"));
        byte[] lossy = transcode(pixels, 35, 37, 1, 3, "RGB",
                Jpeg2000Codec.LOSSY_TRANSFER_SYNTAX_UID, "YBR_ICT");
        for (int i = 0; i < pixels.length; i++) {
            assertTrue(Math.abs((pixels[i] & 255) - (lossy[i] & 255)) <= 8);
        }
    }

    @Test
    void reportsHostRejectionOfPackedYbrFull422() throws Exception {
        byte[] packed = {(byte) 100, (byte) 110, (byte) 128, (byte) 128,
                (byte) 120, (byte) 130, (byte) 128, (byte) 128};
        assertThrows(UnsupportedOperationException.class,
                () -> transcode(packed, 2, 2, 1, 3, "YBR_FULL_422",
                        Jpeg2000Codec.LOSSLESS_TRANSFER_SYNTAX_UID, "YBR_RCT"));
    }

    private static byte[] transcode(byte[] pixels, int rows, int columns, int frames,
            int samples, String sourcePhotometric, String syntax,
            String expectedPhotometric) throws Exception {
        File sourceFile = Files.createTempFile("jpeg2000-source-", ".dcm").toFile();
        File compressedFile = Files.createTempFile("jpeg2000-compressed-", ".dcm").toFile();
        try {
            Attributes source = new Attributes();
            source.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
            source.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
            source.setInt(Tag.Rows, VR.US, rows);
            source.setInt(Tag.Columns, VR.US, columns);
            source.setInt(Tag.SamplesPerPixel, VR.US, samples);
            if (samples == 3) {
                source.setInt(Tag.PlanarConfiguration, VR.US, 0);
            }
            source.setInt(Tag.BitsAllocated, VR.US, 8);
            source.setInt(Tag.BitsStored, VR.US, 8);
            source.setInt(Tag.HighBit, VR.US, 7);
            source.setInt(Tag.PixelRepresentation, VR.US, 0);
            source.setString(Tag.PhotometricInterpretation, VR.CS, sourcePhotometric);
            if (frames > 1) {
                source.setInt(Tag.NumberOfFrames, VR.IS, frames);
            }
            source.setBytes(Tag.PixelData, VR.OB, pixels);
            writeDicom(sourceFile, source, UID.ExplicitVRLittleEndian);
            Attributes dataset = readDicom(sourceFile);
            try (Compressor compressor = new Compressor(dataset, UID.ExplicitVRLittleEndian)) {
                assertTrue(compressor.compress(syntax));
                writeDicom(compressedFile, dataset, syntax);
            }
            Attributes compressed = readDicom(compressedFile);
            assertEquals(expectedPhotometric,
                    compressed.getString(Tag.PhotometricInterpretation));
            assertTrue(new Decompressor(compressed, syntax).decompress());
            return compressed.getBytes(Tag.PixelData);
        } finally {
            Files.deleteIfExists(sourceFile.toPath());
            Files.deleteIfExists(compressedFile.toPath());
        }
    }

    private static void writeDicom(File file, Attributes dataset, String syntax) throws Exception {
        try (DicomOutputStream output = new DicomOutputStream(file)) {
            output.writeDataset(dataset.createFileMetaInformation(syntax), dataset);
        }
    }

    private static Attributes readDicom(File file) throws Exception {
        try (DicomInputStream input = new DicomInputStream(file)) {
            input.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
            return input.readDataset();
        }
    }

    private static void restoreReader(String uid, ImageReaderFactory.ImageReaderParam previous) {
        if (previous == null) ImageReaderFactory.getDefault().remove(uid);
        else ImageReaderFactory.getDefault().put(uid, previous);
    }

    private static void restoreWriter(String uid, ImageWriterFactory.ImageWriterParam previous) {
        if (previous == null) ImageWriterFactory.getDefault().remove(uid);
        else ImageWriterFactory.getDefault().put(uid, previous);
    }
}
