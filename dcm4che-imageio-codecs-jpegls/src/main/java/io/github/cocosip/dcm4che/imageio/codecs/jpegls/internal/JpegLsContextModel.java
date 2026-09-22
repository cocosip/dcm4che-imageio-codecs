package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

final class JpegLsContextModel {
    static final class Context {
        private final JpegLsRegularContextState state;
        private final int sign;

        private Context(JpegLsRegularContextState state, int sign) {
            this.state = state;
            this.sign = sign;
        }

        JpegLsRegularContextState state() {
            return state;
        }

        int sign() {
            return sign;
        }
    }

    private final JpegLsRegularContextState[] states = new JpegLsRegularContextState[365];
    private final JpegLsTraits traits;

    JpegLsContextModel(int maximumSampleValue, int nearLossless, int resetThreshold,
            int threshold1, int threshold2, int threshold3) throws JpegLsException {
        this.traits = JpegLsTraits.create(maximumSampleValue, nearLossless,
                resetThreshold, threshold1, threshold2, threshold3);
        for (int i = 0; i < states.length; i++) {
            states[i] = new JpegLsRegularContextState(traits.initialContextValue());
        }
    }

    Context context(int q1, int q2, int q3) throws JpegLsException {
        if (q1 < -4 || q1 > 4 || q2 < -4 || q2 > 4 || q3 < -4 || q3 > 4) {
            throw new JpegLsException("JPEG-LS gradient quantization is outside [-4,4]");
        }
        int id = (q1 * 9 + q2) * 9 + q3;
        int sign = id < 0 ? -1 : 0;
        int canonical = id < 0 ? -id : id;
        return new Context(states[canonical], sign);
    }

    Context context(int left, int above, int aboveLeft, int aboveRight) throws JpegLsException {
        int q1 = traits.quantizeGradient(aboveRight - above);
        int q2 = traits.quantizeGradient(above - aboveLeft);
        int q3 = traits.quantizeGradient(aboveLeft - left);
        return context(q1, q2, q3);
    }

    JpegLsTraits traits() { return traits; }
}
