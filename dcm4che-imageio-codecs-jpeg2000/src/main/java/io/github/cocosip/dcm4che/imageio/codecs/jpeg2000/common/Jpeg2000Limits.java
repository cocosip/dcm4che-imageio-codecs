package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

public final class Jpeg2000Limits {
    private static final long DEFAULT_MAX_FRAME_BYTES = 512L * 1024L * 1024L;
    private static final long DEFAULT_MAX_SAMPLES = 128L * 1024L * 1024L;
    private static final int DEFAULT_MAX_TILES = 1 << 20;
    private static final long DEFAULT_MAX_CODE_BLOCKS = 1L << 24;
    private static final long DEFAULT_MAX_PACKETS = 1L << 26;
    private static final int DEFAULT_MAX_MARKER_PAYLOAD_BYTES = 65533;
    private static final int DEFAULT_MAX_QUALITY_LAYERS = 65535;

    private final long maxFrameBytes;
    private final long maxSamples;
    private final int maxTiles;
    private final long maxCodeBlocks;
    private final long maxPackets;
    private final int maxMarkerPayloadBytes;
    private final int maxQualityLayers;

    public Jpeg2000Limits(
            long maxFrameBytes,
            long maxSamples,
            int maxTiles,
            long maxCodeBlocks,
            long maxPackets,
            int maxMarkerPayloadBytes,
            int maxQualityLayers) {
        if (maxFrameBytes <= 0
                || maxSamples <= 0
                || maxTiles <= 0
                || maxCodeBlocks <= 0
                || maxPackets <= 0
                || maxMarkerPayloadBytes < 0
                || maxMarkerPayloadBytes > 65533
                || maxQualityLayers <= 0
                || maxQualityLayers > 65535) {
            throw new IllegalArgumentException("JPEG 2000 limits must be positive and fit their codestream fields");
        }
        this.maxFrameBytes = maxFrameBytes;
        this.maxSamples = maxSamples;
        this.maxTiles = maxTiles;
        this.maxCodeBlocks = maxCodeBlocks;
        this.maxPackets = maxPackets;
        this.maxMarkerPayloadBytes = maxMarkerPayloadBytes;
        this.maxQualityLayers = maxQualityLayers;
    }

    public static Jpeg2000Limits defaults() {
        return new Jpeg2000Limits(
                DEFAULT_MAX_FRAME_BYTES,
                DEFAULT_MAX_SAMPLES,
                DEFAULT_MAX_TILES,
                DEFAULT_MAX_CODE_BLOCKS,
                DEFAULT_MAX_PACKETS,
                DEFAULT_MAX_MARKER_PAYLOAD_BYTES,
                DEFAULT_MAX_QUALITY_LAYERS);
    }

    public int checkedSampleBufferBytes(
            long width,
            long height,
            int components,
            int bytesPerSample) throws Jpeg2000Exception {
        if (width <= 0 || height <= 0 || components <= 0 || bytesPerSample <= 0) {
            throw new Jpeg2000Exception("JPEG 2000 sample dimensions and storage sizes must be positive");
        }

        long pixels = multiply(width, height, "sample count");
        long samples = multiply(pixels, components, "sample count");
        if (samples > maxSamples) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 sample count " + samples + " exceeds limit " + maxSamples);
        }

        long bytes = multiply(samples, bytesPerSample, "sample byte length");
        if (bytes > maxFrameBytes || bytes > Integer.MAX_VALUE) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 sample byte length " + bytes + " exceeds the configured or Java array limit");
        }
        return (int) bytes;
    }

    public int requireFrameLength(long value) throws Jpeg2000Exception {
        return requirePositiveInt("frame byte length", value, maxFrameBytes);
    }

    public int requireMarkerPayloadLength(long value) throws Jpeg2000Exception {
        if (value < 0 || value > maxMarkerPayloadBytes) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 marker payload length " + value + " exceeds limit " + maxMarkerPayloadBytes);
        }
        return (int) value;
    }

    public int requireTileCount(long value) throws Jpeg2000Exception {
        return requirePositiveInt("tile count", value, maxTiles);
    }

    public int requireCodeBlockCount(long value) throws Jpeg2000Exception {
        return requirePositiveInt("code-block count", value, maxCodeBlocks);
    }

    public int requirePacketCount(long value) throws Jpeg2000Exception {
        return requirePositiveInt("packet count", value, maxPackets);
    }

    public int requireQualityLayerCount(long value) throws Jpeg2000Exception {
        return requirePositiveInt("quality-layer count", value, maxQualityLayers);
    }

    private static long multiply(long left, long right, String description) throws Jpeg2000Exception {
        if (left > Long.MAX_VALUE / right) {
            throw new Jpeg2000Exception("JPEG 2000 " + description + " overflow");
        }
        return left * right;
    }

    private static int requirePositiveInt(String description, long value, long maximum)
            throws Jpeg2000Exception {
        if (value <= 0 || value > maximum || value > Integer.MAX_VALUE) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 " + description + " " + value + " is outside 1.." + maximum);
        }
        return (int) value;
    }
}
