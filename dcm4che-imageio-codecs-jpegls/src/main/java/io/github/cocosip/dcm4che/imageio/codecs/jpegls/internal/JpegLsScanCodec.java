package io.github.cocosip.dcm4che.imageio.codecs.jpegls.internal;

/** JPEG-LS entropy codec for one scan, including the three Part 1 ILV modes. */
final class JpegLsScanCodec {
    private final int width;
    private final int height;
    private final int componentCount;
    private final JpegLsTraits traits;
    private final JpegLsInterleaveMode interleaveMode;

    JpegLsScanCodec(int width, int height, JpegLsTraits traits) throws JpegLsException {
        this(width, height, 1, traits, JpegLsInterleaveMode.NONE);
    }

    JpegLsScanCodec(int width, int height, int componentCount, JpegLsTraits traits,
            JpegLsInterleaveMode interleaveMode) throws JpegLsException {
        if (width <= 0 || height <= 0) throw new JpegLsException("JPEG-LS scan dimensions must be positive");
        if (componentCount != 1 && componentCount != 3) throw new JpegLsException("JPEG-LS supports one or three components");
        if (componentCount == 1 && interleaveMode != JpegLsInterleaveMode.NONE) throw new JpegLsException("single-component JPEG-LS scan must use no interleave");
        if (componentCount > 1 && interleaveMode == JpegLsInterleaveMode.NONE) throw new JpegLsException("multi-component JPEG-LS scan must be interleaved");
        this.width = width;
        this.height = height;
        this.componentCount = componentCount;
        this.traits = traits;
        this.interleaveMode = interleaveMode;
    }

    byte[] encode(int[] samples) throws JpegLsException {
        requireSampleCount(samples);
        int[] reconstructed = new int[samples.length];
        JpegLsContextModel[] models = createModels();
        JpegLsRunModeScanner[] scanners = createScanners();
        JpegLsGolombWriter writer = new JpegLsGolombWriter();
        if (interleaveMode == JpegLsInterleaveMode.SAMPLE) {
            encodeSampleInterleaved(writer, samples, reconstructed, models, scanners[0]);
        } else if (interleaveMode == JpegLsInterleaveMode.LINE) {
            for (int y = 0; y < height; y++) for (int component = 0; component < componentCount; component++)
                encodeComponentLine(writer, samples, reconstructed, y, component, models[component], scanners[component]);
        } else {
            for (int component = 0; component < componentCount; component++) for (int y = 0; y < height; y++)
                encodeComponentLine(writer, samples, reconstructed, y, component, models[component], scanners[component]);
        }
        return writer.toByteArray();
    }

    int[] decode(byte[] data) throws JpegLsException {
        if (data == null) throw new JpegLsException("JPEG-LS scan data is null");
        int[] samples = new int[checkedSampleCount()];
        JpegLsContextModel[] models = createModels();
        JpegLsRunModeScanner[] scanners = createScanners();
        JpegLsGolombReader reader = new JpegLsGolombReader(data);
        if (interleaveMode == JpegLsInterleaveMode.SAMPLE) {
            decodeSampleInterleaved(reader, samples, models, scanners[0]);
        } else if (interleaveMode == JpegLsInterleaveMode.LINE) {
            for (int y = 0; y < height; y++) for (int component = 0; component < componentCount; component++)
                decodeComponentLine(reader, samples, y, component, models[component], scanners[component]);
        } else {
            for (int component = 0; component < componentCount; component++) for (int y = 0; y < height; y++)
                decodeComponentLine(reader, samples, y, component, models[component], scanners[component]);
        }
        return samples;
    }

    private void encodeComponentLine(JpegLsGolombWriter writer, int[] original, int[] reconstructed,
            int y, int component, JpegLsContextModel model, JpegLsRunModeScanner scanner) throws JpegLsException {
        int x = 0;
        while (x < width) {
            int[] n = neighbors(reconstructed, x, y, component);
            if (isRunMode(n)) { x += encodeRun(writer, original, reconstructed, x, y, component, n[0], scanner); continue; }
            encodeRegular(writer, original, reconstructed, x, y, component, n, model);
            x++;
        }
    }

    private void decodeComponentLine(JpegLsGolombReader reader, int[] samples, int y, int component,
            JpegLsContextModel model, JpegLsRunModeScanner scanner) throws JpegLsException {
        int x = 0;
        while (x < width) {
            int[] n = neighbors(samples, x, y, component);
            if (isRunMode(n)) { x += decodeRun(reader, samples, x, y, component, n[0], scanner); continue; }
            decodeRegular(reader, samples, x, y, component, n, model);
            x++;
        }
    }

    private void encodeSampleInterleaved(JpegLsGolombWriter writer, int[] original, int[] reconstructed,
            JpegLsContextModel[] models, JpegLsRunModeScanner scanner) throws JpegLsException {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            boolean run = isRunMode(neighbors(reconstructed, x, y, 0));
            for (int component = 1; component < componentCount && run; component++) run = isRunMode(neighbors(reconstructed, x, y, component));
            if (run) { x += encodeSampleRun(writer, original, reconstructed, x, y, scanner) - 1; continue; }
            for (int component = 0; component < componentCount; component++)
                encodeRegular(writer, original, reconstructed, x, y, component, neighbors(reconstructed, x, y, component), models[component]);
        }
    }

    private void decodeSampleInterleaved(JpegLsGolombReader reader, int[] samples,
            JpegLsContextModel[] models, JpegLsRunModeScanner scanner) throws JpegLsException {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            boolean run = isRunMode(neighbors(samples, x, y, 0));
            for (int component = 1; component < componentCount && run; component++) run = isRunMode(neighbors(samples, x, y, component));
            if (run) { x += decodeSampleRun(reader, samples, x, y, scanner) - 1; continue; }
            for (int component = 0; component < componentCount; component++)
                decodeRegular(reader, samples, x, y, component, neighbors(samples, x, y, component), models[component]);
        }
    }

    private int encodeRun(JpegLsGolombWriter writer, int[] original, int[] reconstructed, int x, int y,
            int component, int left, JpegLsRunModeScanner scanner) throws JpegLsException {
        int run = 0;
        while (x + run < width && traits.isNear(sample(original, x + run, y, component), left)) {
            setSample(reconstructed, x + run, y, component, left); run++;
        }
        scanner.encodeRunLength(writer, run, x + run == width);
        if (x + run == width) return run;
        int interruptionX = x + run;
        int[] n = neighbors(reconstructed, interruptionX, y, component);
        int context = traits.isNear(n[0], n[1]) ? 1 : 0;
        int sign = n[1] - n[0] < 0 ? -1 : 1;
        int error = traits.computeErrorValue((sample(original, interruptionX, y, component) - n[1]) * sign);
        scanner.encodeRunInterruption(writer, context, error);
        setSample(reconstructed, interruptionX, y, component, traits.reconstruct(n[1], error * sign));
        scanner.decrementRunIndex();
        return run + 1;
    }

    private int decodeRun(JpegLsGolombReader reader, int[] samples, int x, int y, int component,
            int left, JpegLsRunModeScanner scanner) throws JpegLsException {
        int run = scanner.decodeRunLength(reader, width - x);
        for (int i = 0; i < run; i++) setSample(samples, x + i, y, component, left);
        if (x + run == width) return run;
        int interruptionX = x + run;
        int[] n = neighbors(samples, interruptionX, y, component);
        int context = traits.isNear(n[0], n[1]) ? 1 : 0;
        int sign = n[1] - n[0] < 0 ? -1 : 1;
        int error = scanner.decodeRunInterruption(reader, context);
        setSample(samples, interruptionX, y, component, traits.reconstruct(n[1], error * sign));
        scanner.decrementRunIndex();
        return run + 1;
    }

    private int encodeSampleRun(JpegLsGolombWriter writer, int[] original, int[] reconstructed,
            int x, int y, JpegLsRunModeScanner scanner) throws JpegLsException {
        int run = 0;
        while (x + run < width) {
            boolean near = true;
            for (int component = 0; component < componentCount; component++) {
                int left = neighbors(reconstructed, x + run, y, component)[0];
                if (!traits.isNear(sample(original, x + run, y, component), left)) { near = false; break; }
            }
            if (!near) break;
            for (int component = 0; component < componentCount; component++) {
                int left = neighbors(reconstructed, x + run, y, component)[0];
                setSample(reconstructed, x + run, y, component, left);
            }
            run++;
        }
        scanner.encodeRunLength(writer, run, x + run == width);
        if (x + run == width) return run;
        int interruptionX = x + run;
        for (int component = 0; component < componentCount; component++) {
            int[] n = neighbors(reconstructed, interruptionX, y, component);
            int context = traits.isNear(n[0], n[1]) ? 1 : 0;
            int sign = context == 1 ? 1 : (n[1] - n[0] < 0 ? -1 : 1);
            int reference = context == 1 ? n[0] : n[1];
            int error = traits.computeErrorValue((sample(original, interruptionX, y, component) - reference) * sign);
            scanner.encodeRunInterruption(writer, context, error);
            setSample(reconstructed, interruptionX, y, component, traits.reconstruct(reference, error * sign));
        }
        scanner.decrementRunIndex();
        return run + 1;
    }

    private int decodeSampleRun(JpegLsGolombReader reader, int[] samples, int x, int y,
            JpegLsRunModeScanner scanner) throws JpegLsException {
        int run = scanner.decodeRunLength(reader, width - x);
        for (int i = 0; i < run; i++) for (int component = 0; component < componentCount; component++) {
            setSample(samples, x + i, y, component, neighbors(samples, x + i, y, component)[0]);
        }
        if (x + run == width) return run;
        int interruptionX = x + run;
        for (int component = 0; component < componentCount; component++) {
            int[] n = neighbors(samples, interruptionX, y, component);
            int context = traits.isNear(n[0], n[1]) ? 1 : 0;
            int sign = context == 1 ? 1 : (n[1] - n[0] < 0 ? -1 : 1);
            int reference = context == 1 ? n[0] : n[1];
            int error = scanner.decodeRunInterruption(reader, context);
            setSample(samples, interruptionX, y, component, traits.reconstruct(reference, error * sign));
        }
        scanner.decrementRunIndex();
        return run + 1;
    }

    private void encodeRegular(JpegLsGolombWriter writer, int[] original, int[] reconstructed, int x,
            int y, int component, int[] n, JpegLsContextModel model) throws JpegLsException {
        JpegLsContextModel.Context context = model.context(n[0], n[1], n[2], n[3]);
        int prediction = JpegLsPredictor.predict(n[0], n[1], n[2]);
        int corrected = clamp(prediction + applySign(context.state().c(), context.sign()));
        int error = traits.computeErrorValue(applySign(sample(original, x, y, component) - corrected, context.sign()));
        int parameter = context.state().golombParameter();
        int mapped = traits.mapError(error ^ context.state().errorCorrection(parameter, traits.nearLossless()));
        writer.writeMapped(mapped, parameter, traits.limit(), traits.quantizedBitsPerPixel());
        context.state().update(error, traits.nearLossless(), traits.resetThreshold());
        setSample(reconstructed, x, y, component, traits.reconstruct(corrected, applySign(error, context.sign())));
    }

    private void decodeRegular(JpegLsGolombReader reader, int[] samples, int x, int y, int component,
            int[] n, JpegLsContextModel model) throws JpegLsException {
        JpegLsContextModel.Context context = model.context(n[0], n[1], n[2], n[3]);
        int prediction = JpegLsPredictor.predict(n[0], n[1], n[2]);
        int corrected = clamp(prediction + applySign(context.state().c(), context.sign()));
        int parameter = context.state().golombParameter();
        int mapped = reader.readMapped(parameter, traits.limit(), traits.quantizedBitsPerPixel());
        int error = traits.unmapError(mapped);
        if (parameter == 0) error ^= context.state().errorCorrection(parameter, traits.nearLossless());
        context.state().update(error, traits.nearLossless(), traits.resetThreshold());
        setSample(samples, x, y, component, traits.reconstruct(corrected, applySign(error, context.sign())));
    }

    private int[] neighbors(int[] values, int x, int y, int component) {
        int left = x == 0 ? (y == 0 ? 0 : sample(values, 0, y - 1, component)) : sample(values, x - 1, y, component);
        int above = y == 0 ? 0 : sample(values, x, y - 1, component);
        int aboveLeft = y == 0 || x == 0 ? 0 : sample(values, x - 1, y - 1, component);
        int aboveRight = y == 0 ? 0 : sample(values, Math.min(x + 1, width - 1), y - 1, component);
        return new int[] {left, above, aboveLeft, aboveRight};
    }

    private boolean isRunMode(int[] n) {
        return traits.isNear(n[3], n[1]) && traits.isNear(n[1], n[2]) && traits.isNear(n[2], n[0]);
    }

    private JpegLsContextModel[] createModels() throws JpegLsException {
        JpegLsContextModel[] models = new JpegLsContextModel[componentCount];
        for (int i = 0; i < models.length; i++) models[i] = new JpegLsContextModel(
                traits.maximumSampleValue(), traits.nearLossless(), traits.resetThreshold(),
                traits.threshold1(), traits.threshold2(), traits.threshold3());
        return models;
    }

    private JpegLsRunModeScanner[] createScanners() {
        JpegLsRunModeScanner[] scanners = new JpegLsRunModeScanner[componentCount];
        for (int i = 0; i < scanners.length; i++) scanners[i] = new JpegLsRunModeScanner(traits);
        return scanners;
    }

    private int sample(int[] samples, int x, int y, int component) { return samples[(y * width + x) * componentCount + component]; }
    private void setSample(int[] samples, int x, int y, int component, int value) { samples[(y * width + x) * componentCount + component] = value; }
    private void requireSampleCount(int[] samples) throws JpegLsException { if (samples == null || samples.length != checkedSampleCount()) throw new JpegLsException("JPEG-LS sample count mismatch"); }
    private int checkedSampleCount() throws JpegLsException { long count = (long) width * height * componentCount; if (count > Integer.MAX_VALUE) throw new JpegLsException("JPEG-LS sample count overflow"); return (int) count; }
    private int clamp(int value) { return Math.max(0, Math.min(value, traits.maximumSampleValue())); }
    private static int applySign(int value, int sign) { return sign == 0 ? value : -value; }
}
