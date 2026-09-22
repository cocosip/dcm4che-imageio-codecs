package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JpegLsColorTransformTest {
    @Test
    void appliesHpOneTwoAndThreeInverseTransforms() throws Exception {
        int[] hp1 = {130, 120, 140};
        JpegLsColorTransform.applyInverse(hp1, 1, 8);
        assertArrayEquals(new int[] {122, 120, 132}, hp1);

        int[] hp2 = {130, 120, 140};
        JpegLsColorTransform.applyInverse(hp2, 2, 8);
        assertArrayEquals(new int[] {122, 120, 133}, hp2);

        int[] hp3 = {130, 120, 140};
        JpegLsColorTransform.applyInverse(hp3, 3, 8);
        assertArrayEquals(new int[] {141, 129, 121}, hp3);
    }

    @Test
    void validatesTransformComponentAndPrecisionRequirements() throws Exception {
        JpegLsColorTransform.validate(0, 1, 12);
        assertThrows(JpegLsException.class, () -> JpegLsColorTransform.validate(1, 1, 8));
        assertThrows(JpegLsException.class, () -> JpegLsColorTransform.validate(1, 3, 12));
        assertThrows(JpegLsException.class, () -> JpegLsColorTransform.validate(4, 3, 8));
    }
}
