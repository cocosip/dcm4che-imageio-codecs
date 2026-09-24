package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Jpeg2000RasterNormalizerTest {
    private final Jpeg2000Limits limits = Jpeg2000Limits.defaults();

    @Test
    void normalizesUnsignedMonochromeAndPaletteCodes() throws Exception {
        Jpeg2000Raster monochrome = Jpeg2000RasterNormalizer.normalize(
                descriptor(1, 3, 1, 8, 8, false, 0, "MONOCHROME1"),
                new byte[] {0, 127, (byte) 255},
                limits);
        Jpeg2000Raster palette = Jpeg2000RasterNormalizer.normalize(
                descriptor(1, 2, 1, 16, 12, false, 0, "PALETTE COLOR"),
                new byte[] {0, 0, (byte) 0xff, 0x0f},
                limits);

        assertArrayEquals(new int[] {0, 127, 255}, monochrome.component(0));
        assertArrayEquals(new int[] {-128, -1, 127}, monochrome.levelShiftedComponent(0));
        assertArrayEquals(new byte[] {0, 127, (byte) 255}, monochrome.toFrame(false));
        assertArrayEquals(new int[] {0, 4095}, palette.component(0));
        assertArrayEquals(new int[] {-2048, 2047}, palette.levelShiftedComponent(0));
        assertArrayEquals(new byte[] {0, 0, (byte) 0xff, 0x0f}, palette.toFrame(false));
    }

    @Test
    void preservesSignedTwelveAndSixteenBitSemantics() throws Exception {
        byte[] twelveBit = {
                0x00, 0x08,
                (byte) 0xff, 0x0f,
                0x00, 0x00,
                (byte) 0xff, 0x07
        };
        Jpeg2000Raster signedTwelve = Jpeg2000RasterNormalizer.normalize(
                descriptor(1, 4, 1, 16, 12, true, 0, "MONOCHROME2"),
                twelveBit,
                limits);
        byte[] sixteenBit = {0x00, (byte) 0x80, (byte) 0xff, 0x7f};
        Jpeg2000Raster signedSixteen = Jpeg2000RasterNormalizer.normalize(
                descriptor(1, 2, 1, 16, 16, true, 0, "MONOCHROME2"),
                sixteenBit,
                limits);

        assertArrayEquals(new int[] {-2048, -1, 0, 2047}, signedTwelve.component(0));
        assertArrayEquals(signedTwelve.component(0), signedTwelve.levelShiftedComponent(0));
        assertArrayEquals(twelveBit, signedTwelve.toFrame(false));
        assertArrayEquals(new int[] {-32768, 32767}, signedSixteen.component(0));
        assertArrayEquals(sixteenBit, signedSixteen.toFrame(false));
    }

    @Test
    void preservesAllocatedContainerWidthWhenPrecisionIsLower() throws Exception {
        byte[] frame = {1, 0, (byte) 0xff, 0};

        Jpeg2000Raster raster = Jpeg2000RasterNormalizer.normalize(
                descriptor(1, 2, 1, 16, 8, false, 0, "MONOCHROME2"),
                frame,
                limits);

        assertEquals(16, raster.bitsAllocated());
        assertEquals(8, raster.precision());
        assertArrayEquals(new int[] {1, 255}, raster.component(0));
        assertArrayEquals(frame, raster.toFrame(false));
    }

    @Test
    void normalizesInterleavedAndPlanarRgbLayouts() throws Exception {
        byte[] interleaved = {1, 10, 100, 2, 20, (byte) 200};
        Jpeg2000Raster interleavedRaster = Jpeg2000RasterNormalizer.normalize(
                descriptor(1, 2, 3, 8, 8, false, 0, "RGB"), interleaved, limits);
        byte[] planar = {
                1, 0, 2, 0,
                10, 0, 20, 0,
                100, 0, (byte) 200, 0
        };
        Jpeg2000Raster planarRaster = Jpeg2000RasterNormalizer.normalize(
                descriptor(1, 2, 3, 16, 16, false, 1, "RGB"), planar, limits);

        assertArrayEquals(new int[] {1, 2}, interleavedRaster.component(0));
        assertArrayEquals(new int[] {10, 20}, interleavedRaster.component(1));
        assertArrayEquals(new int[] {100, 200}, interleavedRaster.component(2));
        assertArrayEquals(interleaved, interleavedRaster.toFrame(false));
        assertArrayEquals(new int[] {1, 2}, planarRaster.component(0));
        assertArrayEquals(new int[] {10, 20}, planarRaster.component(1));
        assertArrayEquals(new int[] {100, 200}, planarRaster.component(2));
        assertArrayEquals(planar, planarRaster.toFrame(true));
    }

    @Test
    void convertsYbrFullAndPackedYbrFull422ToRgb() throws Exception {
        Jpeg2000Raster full = Jpeg2000RasterNormalizer.normalize(
                descriptor(1, 2, 3, 8, 8, false, 0, "YBR_FULL"),
                new byte[] {76, 85, (byte) 255, 100, (byte) 128, (byte) 128},
                limits);
        Jpeg2000Raster packed = Jpeg2000RasterNormalizer.normalize(
                descriptor(1, 3, 3, 8, 8, false, 0, "YBR_FULL_422"),
                new byte[] {
                        76, 100, 85, (byte) 255,
                        50, 0, (byte) 128, (byte) 128
                },
                limits);

        assertEquals("RGB", full.photometricInterpretation());
        assertArrayEquals(new int[] {254, 100}, full.component(0));
        assertArrayEquals(new int[] {0, 100}, full.component(1));
        assertArrayEquals(new int[] {0, 100}, full.component(2));
        assertArrayEquals(new int[] {254, 255, 50}, packed.component(0));
        assertArrayEquals(new int[] {0, 24, 50}, packed.component(1));
        assertArrayEquals(new int[] {0, 24, 50}, packed.component(2));
        assertArrayEquals(new byte[] {
                (byte) 254, 0, 0,
                (byte) 255, 24, 24,
                50, 50, 50
        }, packed.toFrame(false));
    }

    @Test
    void rejectsUnsupportedColorLengthsAndImplicitOverlayBits() {
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000RasterNormalizer.normalize(
                descriptor(1, 2, 3, 8, 8, false, 0, "YBR_PARTIAL_422"),
                new byte[4], limits));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000RasterNormalizer.normalize(
                descriptor(1, 2, 3, 8, 8, false, 1, "YBR_FULL_422"),
                new byte[4], limits));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000RasterNormalizer.normalize(
                descriptor(1, 2, 3, 16, 16, false, 0, "YBR_FULL"),
                new byte[12], limits));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000RasterNormalizer.normalize(
                descriptor(1, 2, 3, 8, 8, false, 0, "RGB"),
                new byte[5], limits));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000RasterNormalizer.normalize(
                descriptor(1, 1, 1, 16, 12, false, 0, "MONOCHROME2"),
                new byte[] {1, 0x10}, limits));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reversibleRasterCases")
    void roundTripsRequiredRasterAndReversibleTransformMatrix(
            String name,
            ImageDescriptor descriptor,
            byte[] frame,
            boolean planar) throws Exception {
        Jpeg2000Raster raster = Jpeg2000RasterNormalizer.normalize(descriptor, frame, limits);
        int[][] components = new int[raster.componentCount()][];
        for (int component = 0; component < components.length; component++) {
            components[component] = raster.levelShiftedComponent(component);
        }
        if (components.length == 3) {
            components = Jpeg2000ComponentTransform.forwardReversible(
                    components[0], components[1], components[2], limits);
        }
        for (int component = 0; component < components.length; component++) {
            int[] transformed = Jpeg2000Dwt53.forward(
                    components[component], raster.width(), raster.height(), 1, 1, 1, limits);
            components[component] = Jpeg2000Dwt53.inverse(
                    transformed, raster.width(), raster.height(), 1, 1, 1, limits);
        }
        if (components.length == 3) {
            components = Jpeg2000ComponentTransform.inverseReversible(
                    components[0], components[1], components[2], limits);
        }
        if (!raster.signed()) {
            int offset = 1 << (raster.precision() - 1);
            for (int component = 0; component < components.length; component++) {
                for (int sample = 0; sample < components[component].length; sample++) {
                    components[component][sample] += offset;
                }
            }
        }

        Jpeg2000Raster reconstructed = new Jpeg2000Raster(
                raster.width(), raster.height(), raster.bitsAllocated(), raster.precision(),
                raster.signed(), raster.photometricInterpretation(), components);
        assertArrayEquals(frame, reconstructed.toFrame(planar));
    }

    private static Stream<Arguments> reversibleRasterCases() {
        return Stream.of(
                Arguments.of("unsigned 8-bit monochrome",
                        descriptor(3, 3, 1, 8, 8, false, 0, "MONOCHROME2"),
                        bytes(0, 1, 63, 127, 128, 191, 253, 254, 255), false),
                Arguments.of("signed 12-bit monochrome",
                        descriptor(3, 3, 1, 16, 12, true, 0, "MONOCHROME1"),
                        littleEndian16(0x0800, 0x0aab, 0x0fff, 0, 1, 0x0555, 0x07fd, 0x07fe, 0x07ff),
                        false),
                Arguments.of("unsigned 12-bit palette",
                        descriptor(3, 3, 1, 16, 12, false, 0, "PALETTE COLOR"),
                        littleEndian16(0, 1, 0x03ff, 0x07ff, 0x0800, 0x0aaa, 0x0ffd, 0x0ffe, 0x0fff),
                        false),
                Arguments.of("signed 16-bit monochrome",
                        descriptor(3, 3, 1, 16, 16, true, 0, "MONOCHROME2"),
                        littleEndian16(0x8000, 0x8001, 0xc000, 0xffff, 0, 1, 0x3fff, 0x7ffe, 0x7fff),
                        false),
                Arguments.of("unsigned 8-bit interleaved RGB",
                        descriptor(3, 3, 3, 8, 8, false, 0, "RGB"),
                        bytes(0, 1, 2, 31, 32, 33, 63, 64, 65,
                                95, 96, 97, 127, 128, 129, 159, 160, 161,
                                191, 192, 193, 223, 224, 225, 253, 254, 255), false),
                Arguments.of("signed 8-bit interleaved RGB",
                        descriptor(3, 3, 3, 8, 8, true, 0, "RGB"),
                        bytes(0x80, 0x81, 0x82, 0x9f, 0xa0, 0xa1, 0xbf, 0xc0, 0xc1,
                                0xdf, 0xe0, 0xe1, 0xff, 0, 1, 31, 32, 33,
                                63, 64, 65, 95, 96, 97, 125, 126, 127), false),
                Arguments.of("signed 8-bit planar RGB",
                        descriptor(3, 3, 3, 8, 8, true, 1, "RGB"),
                        bytes(0x80, 0x90, 0xa0, 0xb0, 0xc0, 0xd0, 0xe0, 0xf0, 0xff,
                                0, 1, 2, 3, 4, 5, 6, 7, 8,
                                119, 120, 121, 122, 123, 124, 125, 126, 127), true),
                Arguments.of("signed 12-bit interleaved RGB",
                        descriptor(3, 3, 3, 16, 12, true, 0, "RGB"),
                        littleEndian16(
                                0x0800, 0x0801, 0x0802, 0x0aaa, 0x0bbb, 0x0ccc,
                                0x0ffd, 0x0ffe, 0x0fff, 0, 1, 2, 0x0111, 0x0222, 0x0333,
                                0x0444, 0x0555, 0x0666, 0x0777, 0x0700, 0x0600,
                                0x0500, 0x0400, 0x0300, 0x07fd, 0x07fe, 0x07ff), false),
                Arguments.of("signed 12-bit planar RGB",
                        descriptor(3, 3, 3, 16, 12, true, 1, "RGB"),
                        littleEndian16(
                                0x0800, 0x0900, 0x0a00, 0x0b00, 0x0c00, 0x0d00, 0x0e00, 0x0f00, 0x0fff,
                                0, 1, 2, 3, 4, 5, 6, 7, 8,
                                0x07f7, 0x07f8, 0x07f9, 0x07fa, 0x07fb, 0x07fc, 0x07fd, 0x07fe, 0x07ff),
                        true),
                Arguments.of("unsigned 16-bit planar RGB",
                        descriptor(3, 3, 3, 16, 16, false, 1, "RGB"),
                        littleEndian16(
                                0, 1, 2, 3, 4, 5, 6, 7, 8,
                                0x1000, 0x1001, 0x1002, 0x1003, 0x1004, 0x1005, 0x1006, 0x1007, 0x1008,
                                0xfff7, 0xfff8, 0xfff9, 0xfffa, 0xfffb, 0xfffc, 0xfffd, 0xfffe, 0xffff),
                        true),
                Arguments.of("signed 16-bit interleaved RGB",
                        descriptor(3, 3, 3, 16, 16, true, 0, "RGB"),
                        littleEndian16(
                                0x8000, 0x8001, 0x8002, 0xaaaa, 0xbbbb, 0xcccc,
                                0xfffd, 0xfffe, 0xffff, 0, 1, 2, 0x1111, 0x2222, 0x3333,
                                0x4444, 0x5555, 0x6666, 0x7777, 0x7000, 0x6000,
                                0x5000, 0x4000, 0x3000, 0x7ffd, 0x7ffe, 0x7fff), false),
                Arguments.of("signed 16-bit planar RGB",
                        descriptor(3, 3, 3, 16, 16, true, 1, "RGB"),
                        littleEndian16(
                                0x8000, 0x9000, 0xa000, 0xb000, 0xc000, 0xd000, 0xe000, 0xf000, 0xffff,
                                0, 1, 2, 3, 4, 5, 6, 7, 8,
                                0x7ff7, 0x7ff8, 0x7ff9, 0x7ffa, 0x7ffb, 0x7ffc, 0x7ffd, 0x7ffe, 0x7fff),
                        true));
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }

    private static byte[] littleEndian16(int... values) {
        byte[] result = new byte[values.length * 2];
        for (int index = 0; index < values.length; index++) {
            result[index * 2] = (byte) values[index];
            result[index * 2 + 1] = (byte) (values[index] >>> 8);
        }
        return result;
    }

    private static ImageDescriptor descriptor(
            int rows,
            int columns,
            int samples,
            int bitsAllocated,
            int bitsStored,
            boolean signed,
            int planarConfiguration,
            String photometric) {
        Attributes attributes = new Attributes();
        attributes.setInt(Tag.Rows, VR.US, rows);
        attributes.setInt(Tag.Columns, VR.US, columns);
        attributes.setInt(Tag.SamplesPerPixel, VR.US, samples);
        attributes.setInt(Tag.BitsAllocated, VR.US, bitsAllocated);
        attributes.setInt(Tag.BitsStored, VR.US, bitsStored);
        attributes.setInt(Tag.HighBit, VR.US, bitsStored - 1);
        attributes.setInt(Tag.PixelRepresentation, VR.US, signed ? 1 : 0);
        if (samples > 1) {
            attributes.setInt(Tag.PlanarConfiguration, VR.US, planarConfiguration);
        }
        attributes.setString(Tag.PhotometricInterpretation, VR.CS, photometric);
        return new ImageDescriptor(attributes);
    }
}
