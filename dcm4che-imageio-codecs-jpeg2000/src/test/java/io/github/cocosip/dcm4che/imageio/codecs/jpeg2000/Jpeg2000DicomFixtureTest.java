package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.imageio.codec.Decompressor;
import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.io.DicomInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Jpeg2000DicomFixtureTest {
    private static ImageReaderFactory.ImageReaderParam previousLosslessReader;
    private static ImageReaderFactory.ImageReaderParam previousLossyReader;

    @BeforeAll
    static void register() throws Exception {
        ImageReaderFactory readers = ImageReaderFactory.getDefault();
        previousLosslessReader = readers.get(Jpeg2000Codec.LOSSLESS_TRANSFER_SYNTAX_UID);
        previousLossyReader = readers.get(Jpeg2000Codec.LOSSY_TRANSFER_SYNTAX_UID);
        Jpeg2000Codec.register();
    }

    @AfterAll
    static void restore() {
        restoreReader(Jpeg2000Codec.LOSSLESS_TRANSFER_SYNTAX_UID, previousLosslessReader);
        restoreReader(Jpeg2000Codec.LOSSY_TRANSFER_SYNTAX_UID, previousLossyReader);
    }

    @Test
    void decodesSignedLosslessDicomFixturesExactly() throws Exception {
        assertDecodedHash("fo_dicom_codecs_j2k_lossless.dcm", 459, 888,
                "71C16DD9D66487FDC5DC25A7278C70E9F6CE992F29D5910BD73509A4585C2B99");
        assertDecodedHash("fo_dicom_codecs_local2_j2k_lossless.dcm", 386, 552,
                "2B40E05483B31F85A81155AA3E90ADD2E4185CEE4677209BE48B414468871A01");
    }

    @Test
    void decodesSignedLossyDicomWithinNativeTolerance() throws Exception {
        byte[] actual = decode("fo_dicom_codecs_j2k_lossy.dcm", 459, 888, 1, 16,
                Jpeg2000Codec.LOSSY_TRANSFER_SYNTAX_UID);
        byte[] reference = readResource("/jpeg2000/dicom/fo_dicom_codecs_j2k_lossy_native.raw", false);
        assertEquals(reference.length, actual.length);
        int maximumError = 0;
        for (int i = 0; i < actual.length; i += 2) {
            int expectedSample = (short) ((reference[i] & 255) | ((reference[i + 1] & 255) << 8));
            int actualSample = (short) ((actual[i] & 255) | ((actual[i + 1] & 255) << 8));
            maximumError = Math.max(maximumError, Math.abs(expectedSample - actualSample));
        }
        assertTrue(maximumError <= 16, "maximum error=" + maximumError);
    }

    @Test
    void decodesNativeAndPureCodecsRgbLossyDicomWithinTolerance() throws Exception {
        byte[] reference = readResource("/jpeg2000/fo_dicom_codecs_unit8_lossy_native.raw.gz", true);
        for (String name : new String[] {"fo_dicom_codecs_unit8_j2k_lossy.dcm",
                "purecodecs_unit8_j2k_lossy.dcm"}) {
            byte[] actual = decode(name, 512, 512, 3, 8,
                    Jpeg2000Codec.LOSSY_TRANSFER_SYNTAX_UID);
            assertEquals(reference.length, actual.length, name);
            int maximumError = 0;
            for (int i = 0; i < actual.length; i++) {
                maximumError = Math.max(maximumError,
                        Math.abs((actual[i] & 255) - (reference[i] & 255)));
            }
            assertTrue(maximumError <= 6, name + " maximum error=" + maximumError);
        }
    }

    private void assertDecodedHash(String name, int rows, int columns, String expectedHash)
            throws Exception {
        byte[] pixels = decode(name, rows, columns, 1, 16,
                Jpeg2000Codec.LOSSLESS_TRANSFER_SYNTAX_UID);
        assertEquals(rows * columns * 2, pixels.length);
        assertEquals(expectedHash, toHex(MessageDigest.getInstance("SHA-256").digest(pixels)));
    }

    private byte[] decode(String name, int rows, int columns, int samples,
            int bitsStored, String transferSyntax) throws Exception {
        URL resource = getClass().getResource("/jpeg2000/dicom/" + name);
        assertNotNull(resource, name);
        File file = Paths.get(resource.toURI()).toFile();
        Attributes dataset;
        try (DicomInputStream input = new DicomInputStream(file)) {
            input.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
            dataset = input.readDataset();
            assertEquals(transferSyntax, input.getTransferSyntax());
        }
        assertEquals(rows, dataset.getInt(Tag.Rows, 0));
        assertEquals(columns, dataset.getInt(Tag.Columns, 0));
        assertEquals(samples, dataset.getInt(Tag.SamplesPerPixel, 0));
        assertEquals(bitsStored, dataset.getInt(Tag.BitsStored, 0));
        assertEquals(samples == 1 ? 1 : 0, dataset.getInt(Tag.PixelRepresentation, -1));
        assertEquals(samples == 1 ? "MONOCHROME2" : "YBR_ICT",
                dataset.getString(Tag.PhotometricInterpretation));
        assertEquals(1, dataset.getInt(Tag.NumberOfFrames, 1));
        assertTrue(new Decompressor(dataset, transferSyntax).decompress(), name);
        byte[] pixels = dataset.getBytes(Tag.PixelData);
        assertNotNull(pixels, name);
        return pixels;
    }

    private static byte[] readResource(String path, boolean gzip) throws Exception {
        InputStream input = Jpeg2000DicomFixtureTest.class.getResourceAsStream(path);
        assertNotNull(input, path);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream stream = gzip ? new GZIPInputStream(input) : input) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
        }
        return bytes.toByteArray();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(Character.forDigit((value >>> 4) & 15, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        return hex.toString().toUpperCase();
    }

    private static void restoreReader(String uid, ImageReaderFactory.ImageReaderParam previous) {
        if (previous == null) ImageReaderFactory.getDefault().remove(uid);
        else ImageReaderFactory.getDefault().put(uid, previous);
    }
}
