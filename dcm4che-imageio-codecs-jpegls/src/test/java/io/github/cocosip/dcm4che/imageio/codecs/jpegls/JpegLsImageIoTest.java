package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferShort;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.nio.ByteBuffer;

import javax.imageio.IIOImage;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.BytesWithImageImageDescriptor;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.junit.jupiter.api.Test;
import org.dcm4che3.imageio.codec.ImageReaderFactory;
import org.dcm4che3.imageio.codec.ImageWriterFactory;

import io.github.cocosip.dcm4che.imageio.codecs.core.image.DicomImageTypes;
import io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal.JpegLsFrameCodec;

class JpegLsImageIoTest {
    @Test
    void losslessWriterAndReaderRoundTripMonochrome8() throws Exception {
        ImageDescriptor descriptor = descriptor(2, 3, 8, false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        int[] samples = {1, 2, 3, 4, 5, 6};
        fill(source, samples);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsLosslessImageWriter writer = new JpegLsLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        writer.write(null, new IIOImage(source, null, null), writer.getDefaultWriteParam());
        output.flush();

        JpegLsLosslessImageReader reader = new JpegLsLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        BufferedImage decoded = reader.read(0);

        assertSamples(decoded, samples);
    }

    @Test
    void nearLosslessParameterIsUsedAndDestinationIsReused() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 4, 8, false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        fill(source, new int[] {10, 20, 30, 40});

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsNearLosslessImageWriter writer = new JpegLsNearLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        JpegLsImageWriteParam writeParam = (JpegLsImageWriteParam) writer.getDefaultWriteParam();
        writeParam.setAllowedError(2);
        writer.write(null, new IIOImage(source, null, null), writeParam);
        output.flush();

        JpegLsNearLosslessImageReader reader = new JpegLsNearLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        BufferedImage destination = DicomImageTypes.createImage(descriptor);
        ImageReadParam readParam = reader.getDefaultReadParam();
        readParam.setDestination(destination);
        assertSame(destination, reader.read(0, readParam));
        for (int x = 0; x < 4; x++) {
            assertEquals(true, Math.abs(source.getRaster().getSample(x, 0, 0)
                    - destination.getRaster().getSample(x, 0, 0)) <= 2);
        }
    }

    @Test
    void registersBothTransferSyntaxesWithDcm4cheFactories() throws Exception {
        JpegLsCodec.register();
        assertInstanceOf(JpegLsLosslessImageReader.class,
                ImageReaderFactory.getImageReader(ImageReaderFactory.getImageReaderParam(
                        JpegLsCodec.LOSSLESS_TRANSFER_SYNTAX_UID)));
        assertInstanceOf(JpegLsNearLosslessImageWriter.class,
                ImageWriterFactory.getImageWriter(ImageWriterFactory.getImageWriterParam(
                JpegLsCodec.NEAR_LOSSLESS_TRANSFER_SYNTAX_UID)));
    }

    @Test
    void discoversBothJpegLsSpisFromServiceFiles() {
        ImageIO.scanForPlugins();
        assertTrue(hasReader(JpegLsLosslessImageReader.class, "jpeg-ls-lossless"));
        assertTrue(hasReader(JpegLsNearLosslessImageReader.class, "jpeg-ls-near-lossless"));
        assertTrue(hasWriter(JpegLsLosslessImageWriter.class, "jpeg-ls-lossless"));
        assertTrue(hasWriter(JpegLsNearLosslessImageWriter.class, "jpeg-ls-near-lossless"));
    }

    @Test
    void preservesSignedSixteenBitContainerBits() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 3, 16, true);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        short[] sourceData = ((DataBufferShort) source.getRaster().getDataBuffer()).getData();
        sourceData[0] = (short) 0x8001;
        sourceData[1] = (short) 0xffff;
        sourceData[2] = (short) 0x1234;

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsLosslessImageWriter writer = new JpegLsLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        writer.write(null, new IIOImage(source, null, null), writer.getDefaultWriteParam());
        output.flush();

        JpegLsLosslessImageReader reader = new JpegLsLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        short[] decoded = ((DataBufferShort) reader.read(0).getRaster().getDataBuffer()).getData();
        assertEquals(sourceData[0], decoded[0]);
        assertEquals(sourceData[1], decoded[1]);
        assertEquals(sourceData[2], decoded[2]);
    }

    @Test
    void encodesSignedContainerAsRawTwosComplementCodes() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 4, 16, true);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        fill(source, new int[] {0x8001, 0xffff, 0, 0x7fff});

        byte[] encoded = encodeLossless(descriptor, source);

        assertArrayEquals(new int[] {0x8001, 0xffff, 0, 0x7fff},
                JpegLsFrameCodec.decode(encoded).samples());
    }

    @Test
    void roundTripsRgbInterleavedImage() throws Exception {
        ImageDescriptor descriptor = rgbDescriptor(1, 2);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        fill(source, new int[] {1, 10, 100, 2, 20, 110});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsLosslessImageWriter writer = new JpegLsLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        writer.write(null, new IIOImage(source, null, null), writer.getDefaultWriteParam());
        output.flush();
        JpegLsLosslessImageReader reader = new JpegLsLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        assertSamples(reader.read(0), new int[] {1, 10, 100, 2, 20, 110});
    }

    @Test
    void readsSourceRegionWithSubsampling() throws Exception {
        ImageDescriptor descriptor = descriptor(4, 4, 8, false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        fill(source, sequence(16));

        byte[] bytes = encodeLossless(descriptor, source);
        JpegLsLosslessImageReader reader = new JpegLsLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes, descriptor));
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceRegion(new Rectangle(1, 1, 3, 3));
        param.setSourceSubsampling(2, 2, 0, 0);

        BufferedImage decoded = reader.read(0, param);

        assertEquals(2, decoded.getWidth());
        assertEquals(2, decoded.getHeight());
        assertSamples(decoded, new int[] {5, 7, 13, 15});
    }

    @Test
    void readsIntoDestinationOffsetAndSelectedBands() throws Exception {
        ImageDescriptor descriptor = rgbDescriptor(2, 2);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        fill(source, new int[] {1, 10, 100, 2, 20, 110, 3, 30, 120, 4, 40, 130});

        byte[] bytes = encodeLossless(descriptor, source);
        JpegLsLosslessImageReader reader = new JpegLsLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes, descriptor));
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceRegion(new Rectangle(0, 0, 2, 1));
        param.setDestinationOffset(new java.awt.Point(1, 1));
        param.setSourceBands(new int[] {2, 0});
        param.setDestinationBands(new int[] {1, 0});

        BufferedImage decoded = reader.read(0, param);

        assertEquals(3, decoded.getWidth());
        assertEquals(2, decoded.getHeight());
        assertEquals(1, decoded.getRaster().getSample(1, 1, 0));
        assertEquals(100, decoded.getRaster().getSample(1, 1, 1));
        assertEquals(2, decoded.getRaster().getSample(2, 1, 0));
        assertEquals(110, decoded.getRaster().getSample(2, 1, 1));
    }

    @Test
    void writerEmitsMappingTableAndComponentSelector() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 4, 8, false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        fill(source, new int[] {0, 1, 2, 3});

        JpegLsLosslessImageWriter writer = new JpegLsLosslessImageWriter(null);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        JpegLsImageWriteParam param = (JpegLsImageWriteParam) writer.getDefaultWriteParam();
        param.setMappingTables(java.util.Collections.singletonList(
                new JpegLsMappingTable(1, 2, mappingEntries(2))));
        param.setComponentMappingTableSelector(1, 1);
        writer.write(null, new IIOImage(source, null, null), param);
        output.flush();

        JpegLsFrameCodec.DecodedFrame decoded = JpegLsFrameCodec.decode(bytes.toByteArray());
        assertArrayEquals(new int[] {0, 1, 2, 3}, decoded.samples());
        assertEquals(1, decoded.mappedComponents().size());
        assertEquals(2, decoded.mappedComponents().get(0).entryWidth());
        assertArrayEquals(new byte[] {0, 1, 1, 2, 2, 3, 3, 4},
                decoded.mappedComponents().get(0).bytes());
    }

    @Test
    void readerRejectsMappedOutputThatCannotFitDICOMRaster() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 2, 8, false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        fill(source, new int[] {0, 1});

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsLosslessImageWriter writer = new JpegLsLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        JpegLsImageWriteParam param = (JpegLsImageWriteParam) writer.getDefaultWriteParam();
        param.setMappingTables(java.util.Collections.singletonList(
                new JpegLsMappingTable(1, 2, mappingEntries(2))));
        param.setComponentMappingTableSelector(1, 1);
        writer.write(null, new IIOImage(source, null, null), param);
        output.flush();

        JpegLsLosslessImageReader reader = new JpegLsLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        assertThrows(IIOException.class, () -> reader.read(0));
    }

    @Test
    void rejectsSubsampledPhotometricForStandardJpegLsProfile() throws Exception {
        ImageDescriptor descriptor = rgbDescriptor(1, 2, "YBR_FULL_422", false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        fill(source, new int[] {1, 2, 3, 4, 5, 6});

        JpegLsLosslessImageWriter writer = new JpegLsLosslessImageWriter(null);
        writer.setOutput(new DescriptorOutputStream(new ByteArrayOutputStream(), descriptor));
        assertThrows(IIOException.class,
                () -> writer.write(null, new IIOImage(source, null, null),
                writer.getDefaultWriteParam()));
    }

    @Test
    void wrapsMalformedCodestreamAsImageIoException() {
        ImageDescriptor descriptor = descriptor(1, 1, 8, false);
        JpegLsLosslessImageReader reader = new JpegLsLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(new byte[] {0}, descriptor));
        assertThrows(IIOException.class, () -> reader.read(0));
    }

    @Test
    void supportsPaletteIndexesAndYbrFullSamplesWithoutImplicitLutConversion() throws Exception {
        ImageDescriptor palette = descriptor(1, 4, 8, false, 8, "PALETTE COLOR");
        BufferedImage paletteSource = DicomImageTypes.createImage(palette);
        fill(paletteSource, new int[] {0, 1, 2, 3});
        assertSamples(readLossless(palette, paletteSource), new int[] {0, 1, 2, 3});

        ImageDescriptor ybr = rgbDescriptor(1, 2, "YBR_FULL", false);
        BufferedImage ybrSource = DicomImageTypes.createImage(ybr);
        fill(ybrSource, new int[] {10, 128, 128, 20, 120, 140});
        assertSamples(readLossless(ybr, ybrSource),
                new int[] {10, 128, 128, 20, 120, 140});
    }

    @Test
    void losslessRoundTripCoversStoredPrecisionAndSignedBitPatterns() throws Exception {
        for (int bits = 2; bits <= 16; bits++) {
            int allocated = bits <= 8 ? 8 : 16;
            ImageDescriptor descriptor = descriptor(1, 4, bits, true, allocated);
            BufferedImage source = DicomImageTypes.createImage(descriptor);
            int mask = (1 << bits) - 1;
            int[] samples = {0, mask, 1 << (bits - 1), mask - 1};
            fill(source, samples);

            byte[] bytes = encodeLossless(descriptor, source);
            JpegLsLosslessImageReader reader = new JpegLsLosslessImageReader(null);
            reader.setInput(new DescriptorInputStream(bytes, descriptor));
            BufferedImage decoded = reader.read(0);
            for (int x = 0; x < samples.length; x++) {
                assertEquals(samples[x], decoded.getRaster().getSample(x, 0, 0) & mask,
                        "BitsStored=" + bits + ", sample=" + x);
            }
        }
    }

    @Test
    void losslessUnsignedSamplesCoverEveryStoredPrecision() throws Exception {
        for (int bits = 2; bits <= 16; bits++) {
            int allocated = bits <= 8 ? 8 : 16;
            ImageDescriptor descriptor = descriptor(1, 4, bits, false, allocated);
            BufferedImage source = DicomImageTypes.createImage(descriptor);
            int mask = (1 << bits) - 1;
            int[] samples = {0, mask, 1 << (bits - 1), mask - 1};
            fill(source, samples);

            BufferedImage decoded = readLossless(descriptor, source);

            assertSamples(decoded, samples);
        }
    }

    @Test
    void readerAndWriterCanBeReusedWithDifferentInputs() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 2, 16, false, 16);
        BufferedImage first = DicomImageTypes.createImage(descriptor);
        BufferedImage second = DicomImageTypes.createImage(descriptor);
        fill(first, new int[] {0, 0xffff});
        fill(second, new int[] {0x1234, 0xabcd});

        JpegLsLosslessImageWriter writer = new JpegLsLosslessImageWriter(null);
        ByteArrayOutputStream firstBytes = new ByteArrayOutputStream();
        DescriptorOutputStream firstOutput = new DescriptorOutputStream(firstBytes, descriptor);
        writer.setOutput(firstOutput);
        writer.write(null, new IIOImage(first, null, null), writer.getDefaultWriteParam());
        firstOutput.flush();

        ByteArrayOutputStream secondBytes = new ByteArrayOutputStream();
        DescriptorOutputStream secondOutput = new DescriptorOutputStream(secondBytes, descriptor);
        writer.setOutput(secondOutput);
        writer.write(null, new IIOImage(second, null, null), writer.getDefaultWriteParam());
        secondOutput.flush();

        JpegLsLosslessImageReader reader = new JpegLsLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(firstBytes.toByteArray(), descriptor));
        assertSamples(reader.read(0), new int[] {0, 0xffff});
        reader.setInput(new DescriptorInputStream(secondBytes.toByteArray(), descriptor));
        assertSamples(reader.read(0), new int[] {0x1234, 0xabcd});
    }

    @Test
    void losslessReaderRejectsNonZeroNearLosslessFrame() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 2, 8, false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        fill(source, new int[] {10, 20});

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsNearLosslessImageWriter nearWriter = new JpegLsNearLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        nearWriter.setOutput(output);
        JpegLsImageWriteParam nearParam = (JpegLsImageWriteParam) nearWriter.getDefaultWriteParam();
        nearParam.setAllowedError(2);
        nearWriter.write(null, new IIOImage(source, null, null), nearParam);
        output.flush();

        JpegLsLosslessImageReader losslessReader = new JpegLsLosslessImageReader(null);
        losslessReader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        assertThrows(IIOException.class, () -> losslessReader.read(0));
    }

    @Test
    void nearLosslessReaderAcceptsExplicitZeroNearFrame() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 2, 8, false);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        int[] samples = {10, 20};
        fill(source, samples);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsNearLosslessImageWriter writer = new JpegLsNearLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        JpegLsImageWriteParam param = (JpegLsImageWriteParam) writer.getDefaultWriteParam();
        param.setAllowedError(0);
        writer.write(null, new IIOImage(source, null, null), param);
        output.flush();

        JpegLsNearLosslessImageReader reader = new JpegLsNearLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        assertSamples(reader.read(0), samples);
    }

    @Test
    void nearLosslessSignedSamplesUseSignedDomainError() throws Exception {
        ImageDescriptor descriptor = descriptor(8, 8, 16, true);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        int[] samples = new int[64];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i & 1) == 0
                    ? 0x7ff0 + ((i * 37) & 0x3f)
                    : 0x8010 - ((i * 29) & 0x3f);
        }
        fill(source, samples);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsNearLosslessImageWriter writer = new JpegLsNearLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        JpegLsImageWriteParam writeParam = (JpegLsImageWriteParam) writer.getDefaultWriteParam();
        writeParam.setAllowedError(2);
        writer.write(null, new IIOImage(source, null, null), writeParam);
        output.flush();

        JpegLsNearLosslessImageReader reader = new JpegLsNearLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        BufferedImage decoded = reader.read(0);
        for (int x = 0; x < samples.length; x++) {
            int expected = signed16(samples[x]);
            int actual = signed16(decoded.getRaster().getSample(x % 8, x / 8, 0));
            assertTrue(Math.abs(expected - actual) <= 2,
                    "sample " + x + " exceeds signed near-lossless error");
        }
    }

    @Test
    void nearLosslessSignedSamplesCoverSixteenBitExtremes() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 4, 16, true, 16);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        int[] samples = {-32768, -32767, 32766, 32767};
        fill(source, samples);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsNearLosslessImageWriter writer = new JpegLsNearLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        JpegLsImageWriteParam param = (JpegLsImageWriteParam) writer.getDefaultWriteParam();
        param.setAllowedError(2);
        writer.write(null, new IIOImage(source, null, null), param);
        output.flush();

        JpegLsNearLosslessImageReader reader = new JpegLsNearLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        BufferedImage decoded = reader.read(0);
        for (int x = 0; x < samples.length; x++) {
            int actual = signed16(decoded.getRaster().getSample(x, 0, 0));
            assertTrue(Math.abs(samples[x] - actual) <= 2,
                    "signed extreme sample " + x + " exceeds near-lossless error");
        }
    }

    @Test
    void losslessSignedSamplesWithUnusedHighBitsAreSignExtended() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 4, 12, true, 16);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        int[] samples = {-2048, -1, 0, 2047};
        fill(source, samples);

        BufferedImage decoded = readLossless(descriptor, source);

        for (int x = 0; x < samples.length; x++) {
            assertEquals(samples[x], decoded.getRaster().getSample(x, 0, 0),
                    "signed 12-bit sample " + x);
        }
    }

    @Test
    void losslessUnsigned16PreservesFullRangeAndMasksUnusedHighBits() throws Exception {
        ImageDescriptor fullPrecision = descriptor(1, 4, 16, false, 16);
        BufferedImage fullSource = DicomImageTypes.createImage(fullPrecision);
        fill(fullSource, new int[] {0, 1, 0x8000, 0xffff});
        assertSamples(readLossless(fullPrecision, fullSource),
                new int[] {0, 1, 0x8000, 0xffff});

        ImageDescriptor storedPrecision = descriptor(1, 4, 12, false, 16);
        BufferedImage storedSource = DicomImageTypes.createImage(storedPrecision);
        fill(storedSource, new int[] {0, 0x0fff, 0x8123, 0xf123});
        assertSamples(readLossless(storedPrecision, storedSource),
                new int[] {0, 0x0fff, 0x0123, 0x0123});
    }

    @Test
    void nearLosslessUnsigned16MeasuresErrorInUnsignedSampleDomain() throws Exception {
        ImageDescriptor descriptor = descriptor(1, 4, 16, false, 16);
        BufferedImage source = DicomImageTypes.createImage(descriptor);
        int[] samples = {0, 1, 0x8000, 0xffff};
        fill(source, samples);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsNearLosslessImageWriter writer = new JpegLsNearLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        JpegLsImageWriteParam param = (JpegLsImageWriteParam) writer.getDefaultWriteParam();
        param.setAllowedError(2);
        writer.write(null, new IIOImage(source, null, null), param);
        output.flush();

        JpegLsNearLosslessImageReader reader = new JpegLsNearLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes.toByteArray(), descriptor));
        BufferedImage decoded = reader.read(0);
        for (int i = 0; i < samples.length; i++) {
            assertTrue(Math.abs(samples[i] - decoded.getRaster().getSample(i, 0, 0)) <= 2,
                    "unsigned sample " + i + " exceeds NEAR tolerance");
        }
    }

    private static ImageDescriptor descriptor(int rows, int columns, int bits, boolean signed) {
        return descriptor(rows, columns, bits, signed, bits);
    }

    private static ImageDescriptor descriptor(int rows, int columns, int bits,
            boolean signed, int bitsAllocated) {
        return descriptor(rows, columns, bits, signed, bitsAllocated, "MONOCHROME2");
    }

    private static ImageDescriptor descriptor(int rows, int columns, int bits,
            boolean signed, int bitsAllocated, String photometric) {
        Attributes attributes = new Attributes();
        attributes.setInt(Tag.Rows, VR.US, rows);
        attributes.setInt(Tag.Columns, VR.US, columns);
        attributes.setInt(Tag.SamplesPerPixel, VR.US, 1);
        attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
        attributes.setInt(Tag.BitsStored, VR.US, bits);
        attributes.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
        attributes.setString(Tag.PhotometricInterpretation, VR.CS, photometric);
        return new ImageDescriptor(attributes);
    }

    private static ImageDescriptor rgbDescriptor(int rows, int columns) {
        return rgbDescriptor(rows, columns, "RGB", false);
    }

    private static ImageDescriptor rgbDescriptor(int rows, int columns,
            String photometric, boolean banded) {
        Attributes attributes = new Attributes();
        attributes.setInt(Tag.Rows, VR.US, rows);
        attributes.setInt(Tag.Columns, VR.US, columns);
        attributes.setInt(Tag.SamplesPerPixel, VR.US, 3);
        attributes.setInt(Tag.BitsAllocated, VR.US, 8);
        attributes.setInt(Tag.BitsStored, VR.US, 8);
        attributes.setInt(Tag.PixelRepresentation, VR.US, 0);
        attributes.setInt(Tag.PlanarConfiguration, VR.US, banded ? 1 : 0);
        attributes.setString(Tag.PhotometricInterpretation, VR.CS, photometric);
        return new ImageDescriptor(attributes);
    }

    private static void fill(BufferedImage image, int[] values) {
        int i = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                for (int band = 0; band < image.getRaster().getNumBands(); band++) {
                    image.getRaster().setSample(x, y, band, values[i++]);
                }
            }
        }
    }

    private static int[] sequence(int length) {
        int[] values = new int[length];
        for (int i = 0; i < length; i++) values[i] = i;
        return values;
    }

    private static byte[] mappingEntries(int entryWidth) {
        byte[] entries = new byte[256 * entryWidth];
        for (int sample = 0; sample < 256; sample++) {
            for (int byteIndex = 0; byteIndex < entryWidth; byteIndex++) {
                entries[sample * entryWidth + byteIndex] = (byte) (sample + byteIndex);
            }
        }
        return entries;
    }

    private static int signed16(int value) {
        return (short) value;
    }

    private static boolean hasReader(Class<?> expected, String format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
        while (readers.hasNext()) {
            if (expected.isInstance(readers.next())) return true;
        }
        return false;
    }

    private static boolean hasWriter(Class<?> expected, String format) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        while (writers.hasNext()) {
            if (expected.isInstance(writers.next())) return true;
        }
        return false;
    }

    private static byte[] encodeLossless(ImageDescriptor descriptor, BufferedImage source)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JpegLsLosslessImageWriter writer = new JpegLsLosslessImageWriter(null);
        DescriptorOutputStream output = new DescriptorOutputStream(bytes, descriptor);
        writer.setOutput(output);
        writer.write(null, new IIOImage(source, null, null), writer.getDefaultWriteParam());
        output.flush();
        return bytes.toByteArray();
    }

    private static BufferedImage readLossless(ImageDescriptor descriptor, BufferedImage source)
            throws Exception {
        byte[] bytes = encodeLossless(descriptor, source);
        JpegLsLosslessImageReader reader = new JpegLsLosslessImageReader(null);
        reader.setInput(new DescriptorInputStream(bytes, descriptor));
        return reader.read(0);
    }

    private static void assertSamples(BufferedImage image, int[] expected) {
        int i = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                for (int band = 0; band < image.getRaster().getNumBands(); band++) {
                    assertEquals(expected[i++], image.getRaster().getSample(x, y, band));
                }
            }
        }
    }

    private static final class DescriptorInputStream extends MemoryCacheImageInputStream
            implements BytesWithImageImageDescriptor {
        private final ImageDescriptor descriptor;
        private final byte[] bytes;

        DescriptorInputStream(byte[] bytes, ImageDescriptor descriptor) {
            super(new ByteArrayInputStream(bytes));
            this.bytes = bytes;
            this.descriptor = descriptor;
        }

        @Override public ByteBuffer getBytes() { return ByteBuffer.wrap(bytes).asReadOnlyBuffer(); }
        @Override public ImageDescriptor getImageDescriptor() { return descriptor; }
    }

    private static final class DescriptorOutputStream extends MemoryCacheImageOutputStream
            implements BytesWithImageImageDescriptor {
        private final ImageDescriptor descriptor;

        DescriptorOutputStream(ByteArrayOutputStream output, ImageDescriptor descriptor) {
            super(output);
            this.descriptor = descriptor;
        }

        @Override public ByteBuffer getBytes() { return ByteBuffer.allocate(0); }
        @Override public ImageDescriptor getImageDescriptor() { return descriptor; }
    }
}
