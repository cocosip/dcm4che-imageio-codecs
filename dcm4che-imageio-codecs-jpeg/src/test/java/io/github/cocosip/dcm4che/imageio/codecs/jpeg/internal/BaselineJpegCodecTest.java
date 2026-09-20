package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BaselineJpegCodecTest {
    @Test
    void roundTripsAnEightByEightMonochromeBlock() throws Exception {
        int[] samples = new int[64];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (i * 17) & 0xff;
        }
        JpegFrame source = JpegFrame.of(8, 8, 1, samples);

        byte[] encoded = BaselineJpegCodec.encode(source);
        JpegFrame decoded = BaselineJpegCodec.decode(encoded);

        assertTrue(encoded.length > 100);
        for (int i = 0; i < samples.length; i++) {
            assertTrue(Math.abs(samples[i] - decoded.samples()[i]) <= 50,
                    "sample " + i + " differs too much: " + samples[i] + " vs " + decoded.samples()[i]);
        }
    }
}
