package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;

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

class Htj2kDicomIntegrationTest {
    private static final String[] UIDS = {
            Htj2kCodec.LOSSLESS_TRANSFER_SYNTAX_UID,
            Htj2kCodec.LOSSLESS_RPCL_TRANSFER_SYNTAX_UID,
            Htj2kCodec.LOSSY_TRANSFER_SYNTAX_UID
    };
    private static final String[] NAMES = {
            "htj2k-lossless", "htj2k-lossless-rpcl", "htj2k-lossy"
    };
    private static final ImageReaderFactory.ImageReaderParam[] PREVIOUS_READERS =
            new ImageReaderFactory.ImageReaderParam[UIDS.length];
    private static final ImageWriterFactory.ImageWriterParam[] PREVIOUS_WRITERS =
            new ImageWriterFactory.ImageWriterParam[UIDS.length];

    @BeforeAll
    static void register() throws Exception {
        for (int i = 0; i < UIDS.length; i++) {
            PREVIOUS_READERS[i] = ImageReaderFactory.getDefault().get(UIDS[i]);
            PREVIOUS_WRITERS[i] = ImageWriterFactory.getDefault().get(UIDS[i]);
        }
        Htj2kCodec.register();
    }

    @AfterAll
    static void restore() {
        for (int i = 0; i < UIDS.length; i++) {
            if (PREVIOUS_READERS[i] == null) ImageReaderFactory.getDefault().remove(UIDS[i]);
            else ImageReaderFactory.getDefault().put(UIDS[i], PREVIOUS_READERS[i]);
            if (PREVIOUS_WRITERS[i] == null) ImageWriterFactory.getDefault().remove(UIDS[i]);
            else ImageWriterFactory.getDefault().put(UIDS[i], PREVIOUS_WRITERS[i]);
        }
    }

    @Test
    void registersExactSyntaxNames() {
        for (int i = 0; i < UIDS.length; i++) {
            assertEquals(NAMES[i], ImageReaderFactory.getDefault().get(UIDS[i]).formatName);
            assertEquals(NAMES[i], ImageWriterFactory.getDefault().get(UIDS[i]).formatName);
            assertTrue(ImageIO.getImageReadersByFormatName(NAMES[i]).hasNext());
            assertTrue(ImageIO.getImageWritersByFormatName(NAMES[i]).hasNext());
        }
    }

    @Test
    void compressesAndDecompressesAllThreeSyntaxes() throws Exception {
        byte[] pixels = new byte[35 * 37 * 3];
        for (int y = 0; y < 35; y++) {
            for (int x = 0; x < 37; x++) {
                for (int c = 0; c < 3; c++) {
                    pixels[(y * 37 + x) * 3 + c] = (byte) (x * 2 + y * 3 + c * 19);
                }
            }
        }
        for (String uid : UIDS) {
            Attributes dataset = new Attributes();
            dataset.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
            dataset.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
            dataset.setInt(Tag.Rows, VR.US, 35);
            dataset.setInt(Tag.Columns, VR.US, 37);
            dataset.setInt(Tag.SamplesPerPixel, VR.US, 3);
            dataset.setInt(Tag.PlanarConfiguration, VR.US, 0);
            dataset.setInt(Tag.BitsAllocated, VR.US, 8);
            dataset.setInt(Tag.BitsStored, VR.US, 8);
            dataset.setInt(Tag.HighBit, VR.US, 7);
            dataset.setInt(Tag.PixelRepresentation, VR.US, 0);
            dataset.setString(Tag.PhotometricInterpretation, VR.CS, "RGB");
            dataset.setBytes(Tag.PixelData, VR.OB, pixels);
            File sourceFile = Files.createTempFile("htj2k-source-", ".dcm").toFile();
            File compressed = Files.createTempFile("htj2k-compressed-", ".dcm").toFile();
            try {
                try (DicomOutputStream output = new DicomOutputStream(sourceFile)) {
                    output.writeDataset(dataset.createFileMetaInformation(
                            UID.ExplicitVRLittleEndian), dataset);
                }
                Attributes source;
                try (DicomInputStream input = new DicomInputStream(sourceFile)) {
                    input.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
                    source = input.readDataset();
                }
                try (Compressor compressor = new Compressor(source, UID.ExplicitVRLittleEndian)) {
                    assertTrue(compressor.compress(uid));
                    try (DicomOutputStream output = new DicomOutputStream(compressed)) {
                        output.writeDataset(source.createFileMetaInformation(uid), source);
                    }
                }
                Attributes encoded;
                try (DicomInputStream input = new DicomInputStream(compressed)) {
                    input.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
                    encoded = input.readDataset();
                }
                assertEquals(uid.equals(Htj2kCodec.LOSSY_TRANSFER_SYNTAX_UID)
                        ? "YBR_ICT" : "YBR_RCT",
                        encoded.getString(Tag.PhotometricInterpretation));
                assertTrue(new Decompressor(encoded, uid).decompress());
                byte[] actual = encoded.getBytes(Tag.PixelData);
                if (uid.equals(Htj2kCodec.LOSSY_TRANSFER_SYNTAX_UID)) {
                    for (int i = 0; i < pixels.length; i++) {
                        assertTrue(Math.abs((pixels[i] & 255) - (actual[i] & 255)) <= 12,
                                "HTJ2K lossy sample " + i);
                    }
                } else {
                    byte[] expected = new byte[actual.length];
                    System.arraycopy(pixels, 0, expected, 0, pixels.length);
                    assertArrayEquals(expected, actual);
                }
            } finally {
                Files.deleteIfExists(sourceFile.toPath());
                Files.deleteIfExists(compressed.toPath());
            }
        }
    }
}
