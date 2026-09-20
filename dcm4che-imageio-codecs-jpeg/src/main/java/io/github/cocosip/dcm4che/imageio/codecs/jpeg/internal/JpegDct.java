package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

final class JpegDct {
    private static final double[][] COS = new double[8][8];

    static {
        for (int u = 0; u < 8; u++) {
            for (int x = 0; x < 8; x++) {
                COS[u][x] = Math.cos(((2 * x + 1) * u * Math.PI) / 16.0);
            }
        }
    }

    private JpegDct() {
    }

    static double[] forward(double[] input) {
        double[] output = new double[64];
        for (int v = 0; v < 8; v++) {
            for (int u = 0; u < 8; u++) {
                double sum = 0;
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        sum += input[y * 8 + x] * COS[u][x] * COS[v][y];
                    }
                }
                output[v * 8 + u] = 0.25 * scale(u) * scale(v) * sum;
            }
        }
        return output;
    }

    static double[] inverse(double[] input) {
        double[] output = new double[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                double sum = 0;
                for (int v = 0; v < 8; v++) {
                    for (int u = 0; u < 8; u++) {
                        sum += scale(u) * scale(v) * input[v * 8 + u] * COS[u][x] * COS[v][y];
                    }
                }
                output[y * 8 + x] = 0.25 * sum;
            }
        }
        return output;
    }

    private static double scale(int value) {
        return value == 0 ? 1.0 / Math.sqrt(2.0) : 1.0;
    }
}
