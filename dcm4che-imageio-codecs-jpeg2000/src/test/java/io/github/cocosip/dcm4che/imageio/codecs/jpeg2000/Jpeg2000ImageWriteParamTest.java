package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.imageio.IIOException;

import org.junit.jupiter.api.Test;

class Jpeg2000ImageWriteParamTest {
    @Test
    void targetRatioOverridesNativeRateAndAppendsLosslessLayer() throws Exception {
        Jpeg2000ImageWriteParam param = new Jpeg2000ImageWriteParam(true);
        param.setRate(10);
        param.setTargetRatio(4);
        param.setNumLayers(3);
        assertArrayEquals(new double[] {16, 8, 4, 0},
                param.resolveLayerRatios(12, 16));
    }

    @Test
    void nativeRatesUseStoredToAllocatedPrecisionScaling() throws Exception {
        Jpeg2000ImageWriteParam param = new Jpeg2000ImageWriteParam(false);
        param.setRateLevels(80, 40, 20, 10);
        param.setRate(20);
        assertArrayEquals(new double[] {80, 40, 15},
                param.resolveLayerRatios(12, 16));
    }

    @Test
    void rejectsInvalidTransformAndLayerPolicies() {
        Jpeg2000ImageWriteParam lossless = new Jpeg2000ImageWriteParam(true);
        assertThrows(IllegalArgumentException.class, () -> lossless.setIrreversible(true));
        assertThrows(IllegalArgumentException.class, () -> lossless.setRateLevels(10, 20));
        lossless.setIncludeFinalLosslessLayer(false);
        assertThrows(IIOException.class, () -> lossless.resolveLayerRatios(8, 8));
        Jpeg2000ImageWriteParam lossy = new Jpeg2000ImageWriteParam(false);
        lossy.setIncludeFinalLosslessLayer(true);
        assertThrows(IIOException.class, () -> lossy.resolveLayerRatios(8, 8));
    }
}
