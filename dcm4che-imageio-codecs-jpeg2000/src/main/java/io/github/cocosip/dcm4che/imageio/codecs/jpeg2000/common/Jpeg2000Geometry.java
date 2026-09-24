package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Jpeg2000Geometry {
    private Jpeg2000Geometry() {
    }

    public static Image create(
            long imageX0,
            long imageY0,
            long imageX1,
            long imageY1,
            long tileX0,
            long tileY0,
            long tileWidth,
            long tileHeight,
            int componentCount,
            int decompositionLevels,
            int codeBlockWidth,
            int codeBlockHeight,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        if (limits == null) {
            throw new NullPointerException("limits");
        }
        validateArguments(
                imageX0, imageY0, imageX1, imageY1,
                tileX0, tileY0, tileWidth, tileHeight,
                componentCount, decompositionLevels, codeBlockWidth, codeBlockHeight);
        limits.checkedSampleBufferBytes(
                imageX1 - imageX0, imageY1 - imageY0, componentCount, Integer.BYTES);

        long tilesX = ceilDiv(imageX1 - tileX0, tileWidth);
        long tilesY = ceilDiv(imageY1 - tileY0, tileHeight);
        int tileCount = limits.requireTileCount(checkedMultiply(tilesX, tilesY, "tile count"));
        long codeBlockCount = countCodeBlocks(
                imageX0, imageY0, imageX1, imageY1,
                tileX0, tileY0, tileWidth, tileHeight,
                componentCount, decompositionLevels, codeBlockWidth, codeBlockHeight,
                tilesX, tilesY);
        limits.requireCodeBlockCount(codeBlockCount);

        Bounds imageBounds = new Bounds(imageX0, imageY0, imageX1, imageY1);
        List<Tile> tiles = new ArrayList<Tile>(tileCount);
        int index = 0;
        for (long tileY = 0; tileY < tilesY; tileY++) {
            for (long tileX = 0; tileX < tilesX; tileX++) {
                Bounds tileBounds = tileBounds(
                        imageBounds, tileX0, tileY0, tileWidth, tileHeight, tileX, tileY);
                List<Component> components = new ArrayList<Component>(componentCount);
                for (int component = 0; component < componentCount; component++) {
                    components.add(buildComponent(
                            component, tileBounds, decompositionLevels,
                            codeBlockWidth, codeBlockHeight));
                }
                tiles.add(new Tile(index++, tileBounds, components));
            }
        }
        return new Image(imageBounds, tiles);
    }

    private static void validateArguments(
            long imageX0,
            long imageY0,
            long imageX1,
            long imageY1,
            long tileX0,
            long tileY0,
            long tileWidth,
            long tileHeight,
            int componentCount,
            int decompositionLevels,
            int codeBlockWidth,
            int codeBlockHeight) throws Jpeg2000Exception {
        if (imageX0 < 0 || imageY0 < 0 || imageX1 <= imageX0 || imageY1 <= imageY0) {
            throw new Jpeg2000Exception("JPEG 2000 image geometry is invalid");
        }
        if (tileX0 < 0 || tileY0 < 0 || tileX0 > imageX0 || tileY0 > imageY0
                || tileWidth <= imageX0 - tileX0 || tileHeight <= imageY0 - tileY0) {
            throw new Jpeg2000Exception("JPEG 2000 tile geometry does not cover the image origin");
        }
        if (componentCount <= 0 || decompositionLevels < 0 || decompositionLevels > 32) {
            throw new Jpeg2000Exception("JPEG 2000 component or decomposition count is invalid");
        }
        if (codeBlockWidth < 4 || codeBlockWidth > 1024
                || codeBlockHeight < 4 || codeBlockHeight > 1024
                || !isPowerOfTwo(codeBlockWidth) || !isPowerOfTwo(codeBlockHeight)
                || (long) codeBlockWidth * codeBlockHeight > 4096) {
            throw new Jpeg2000Exception(
                    "JPEG 2000 code-block dimensions must be powers of two in 4..1024"
                            + " with area at most 4096");
        }
    }

    private static long countCodeBlocks(
            long imageX0,
            long imageY0,
            long imageX1,
            long imageY1,
            long tileX0,
            long tileY0,
            long tileWidth,
            long tileHeight,
            int componentCount,
            int decompositionLevels,
            int codeBlockWidth,
            int codeBlockHeight,
            long tilesX,
            long tilesY) throws Jpeg2000Exception {
        Bounds imageBounds = new Bounds(imageX0, imageY0, imageX1, imageY1);
        long count = 0;
        for (long tileY = 0; tileY < tilesY; tileY++) {
            for (long tileX = 0; tileX < tilesX; tileX++) {
                Bounds bounds = tileBounds(
                        imageBounds, tileX0, tileY0, tileWidth, tileHeight, tileX, tileY);
                for (int resolution = 0; resolution <= decompositionLevels; resolution++) {
                    for (Bounds band : subbandBounds(bounds, decompositionLevels, resolution)) {
                        if (band.width() == 0 || band.height() == 0) {
                            continue;
                        }
                        long blocksX = ceilDiv(band.x1(), codeBlockWidth)
                                - floorDiv(band.x0(), codeBlockWidth);
                        long blocksY = ceilDiv(band.y1(), codeBlockHeight)
                                - floorDiv(band.y0(), codeBlockHeight);
                        count = checkedAdd(count,
                                checkedMultiply(blocksX, blocksY, "code-block count"),
                                "code-block count");
                    }
                }
            }
        }
        return checkedMultiply(count, componentCount, "component code-block count");
    }

    private static TileComponentData componentData(Bounds bounds, int levels, int resolution) {
        Bounds current = resolutionBounds(bounds, levels - resolution);
        if (resolution == 0) {
            return new TileComponentData(current, Collections.singletonList(
                    new BandData(Orientation.LL, 0, 0, current)));
        }
        Bounds previous = resolutionBounds(bounds, levels - resolution + 1);
        int lowWidth = intLength(previous.width(), "low-pass width");
        int lowHeight = intLength(previous.height(), "low-pass height");
        List<Bounds> boundsByOrientation = subbandBounds(bounds, levels, resolution);
        List<BandData> bands = new ArrayList<BandData>(3);
        bands.add(new BandData(Orientation.HL, lowWidth, 0, boundsByOrientation.get(0)));
        bands.add(new BandData(Orientation.LH, 0, lowHeight, boundsByOrientation.get(1)));
        bands.add(new BandData(Orientation.HH, lowWidth, lowHeight, boundsByOrientation.get(2)));
        return new TileComponentData(current, bands);
    }

    private static Component buildComponent(
            int index,
            Bounds bounds,
            int levels,
            int codeBlockWidth,
            int codeBlockHeight) {
        List<Resolution> resolutions = new ArrayList<Resolution>(levels + 1);
        for (int resolution = 0; resolution <= levels; resolution++) {
            TileComponentData data = componentData(bounds, levels, resolution);
            List<Subband> subbands = new ArrayList<Subband>(data.bands.size());
            for (BandData band : data.bands) {
                subbands.add(new Subband(
                        band.orientation,
                        band.offsetX,
                        band.offsetY,
                        band.bounds,
                        buildCodeBlocks(band, codeBlockWidth, codeBlockHeight)));
            }
            resolutions.add(new Resolution(resolution, data.bounds, subbands));
        }
        return new Component(index, bounds, resolutions);
    }

    private static List<CodeBlock> buildCodeBlocks(
            BandData band,
            int codeBlockWidth,
            int codeBlockHeight) {
        List<CodeBlock> blocks = new ArrayList<CodeBlock>();
        if (band.bounds.width() == 0 || band.bounds.height() == 0) {
            return blocks;
        }
        long firstY = floorDiv(band.bounds.y0(), codeBlockHeight);
        long lastY = ceilDiv(band.bounds.y1(), codeBlockHeight);
        long firstX = floorDiv(band.bounds.x0(), codeBlockWidth);
        long lastX = ceilDiv(band.bounds.x1(), codeBlockWidth);
        for (long blockY = firstY; blockY < lastY; blockY++) {
            for (long blockX = firstX; blockX < lastX; blockX++) {
                long x0 = Math.max(band.bounds.x0(), blockX * codeBlockWidth);
                long y0 = Math.max(band.bounds.y0(), blockY * codeBlockHeight);
                long x1 = Math.min(band.bounds.x1(), (blockX + 1) * codeBlockWidth);
                long y1 = Math.min(band.bounds.y1(), (blockY + 1) * codeBlockHeight);
                int offsetX = band.offsetX + intLength(x0 - band.bounds.x0(), "code-block offset X");
                int offsetY = band.offsetY + intLength(y0 - band.bounds.y0(), "code-block offset Y");
                blocks.add(new CodeBlock(offsetX, offsetY, new Bounds(x0, y0, x1, y1)));
            }
        }
        return blocks;
    }

    private static List<Bounds> subbandBounds(Bounds tile, int levels, int resolution) {
        int level = levels - resolution;
        if (resolution == 0) {
            return Collections.singletonList(resolutionBounds(tile, level));
        }
        long lowScale = 1L << level;
        long bandScale = 1L << (level + 1);
        List<Bounds> result = new ArrayList<Bounds>(3);
        result.add(new Bounds(
                ceilDiv(tile.x0() - lowScale, bandScale),
                ceilDiv(tile.y0(), bandScale),
                ceilDiv(tile.x1() - lowScale, bandScale),
                ceilDiv(tile.y1(), bandScale)));
        result.add(new Bounds(
                ceilDiv(tile.x0(), bandScale),
                ceilDiv(tile.y0() - lowScale, bandScale),
                ceilDiv(tile.x1(), bandScale),
                ceilDiv(tile.y1() - lowScale, bandScale)));
        result.add(new Bounds(
                ceilDiv(tile.x0() - lowScale, bandScale),
                ceilDiv(tile.y0() - lowScale, bandScale),
                ceilDiv(tile.x1() - lowScale, bandScale),
                ceilDiv(tile.y1() - lowScale, bandScale)));
        return result;
    }

    private static Bounds resolutionBounds(Bounds bounds, int level) {
        long scale = 1L << level;
        return new Bounds(
                ceilDiv(bounds.x0(), scale),
                ceilDiv(bounds.y0(), scale),
                ceilDiv(bounds.x1(), scale),
                ceilDiv(bounds.y1(), scale));
    }

    private static Bounds tileBounds(
            Bounds image,
            long tileX0,
            long tileY0,
            long tileWidth,
            long tileHeight,
            long tileX,
            long tileY) {
        long x0 = tileX0 + tileX * tileWidth;
        long y0 = tileY0 + tileY * tileHeight;
        return new Bounds(
                Math.max(image.x0(), x0),
                Math.max(image.y0(), y0),
                Math.min(image.x1(), x0 + tileWidth),
                Math.min(image.y1(), y0 + tileHeight));
    }

    private static boolean isPowerOfTwo(int value) {
        return (value & (value - 1)) == 0;
    }

    private static long floorDiv(long value, long divisor) {
        return Math.floorDiv(value, divisor);
    }

    private static long ceilDiv(long value, long divisor) {
        return -Math.floorDiv(-value, divisor);
    }

    private static long checkedMultiply(long left, long right, String context)
            throws Jpeg2000Exception {
        if (left != 0 && right > Long.MAX_VALUE / left) {
            throw new Jpeg2000Exception("JPEG 2000 " + context + " overflow");
        }
        return left * right;
    }

    private static long checkedAdd(long left, long right, String context)
            throws Jpeg2000Exception {
        if (left > Long.MAX_VALUE - right) {
            throw new Jpeg2000Exception("JPEG 2000 " + context + " overflow");
        }
        return left + right;
    }

    private static int intLength(long value, String context) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("JPEG 2000 " + context + " exceeds Java array limits");
        }
        return (int) value;
    }

    public enum Orientation {
        LL,
        HL,
        LH,
        HH
    }

    public static final class Bounds {
        private final long x0;
        private final long y0;
        private final long x1;
        private final long y1;

        private Bounds(long x0, long y0, long x1, long y1) {
            if (x1 < x0 || y1 < y0) {
                throw new IllegalArgumentException("JPEG 2000 bounds are inverted");
            }
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        public long x0() {
            return x0;
        }

        public long y0() {
            return y0;
        }

        public long x1() {
            return x1;
        }

        public long y1() {
            return y1;
        }

        public long width() {
            return x1 - x0;
        }

        public long height() {
            return y1 - y0;
        }
    }

    public static final class Image {
        private final Bounds bounds;
        private final List<Tile> tiles;

        private Image(Bounds bounds, List<Tile> tiles) {
            this.bounds = bounds;
            this.tiles = immutable(tiles);
        }

        public Bounds bounds() {
            return bounds;
        }

        public List<Tile> tiles() {
            return tiles;
        }
    }

    public static final class Tile {
        private final int index;
        private final Bounds bounds;
        private final List<Component> components;

        private Tile(int index, Bounds bounds, List<Component> components) {
            this.index = index;
            this.bounds = bounds;
            this.components = immutable(components);
        }

        public int index() {
            return index;
        }

        public Bounds bounds() {
            return bounds;
        }

        public List<Component> components() {
            return components;
        }
    }

    public static final class Component {
        private final int index;
        private final Bounds bounds;
        private final List<Resolution> resolutions;

        private Component(int index, Bounds bounds, List<Resolution> resolutions) {
            this.index = index;
            this.bounds = bounds;
            this.resolutions = immutable(resolutions);
        }

        public int index() {
            return index;
        }

        public Bounds bounds() {
            return bounds;
        }

        public List<Resolution> resolutions() {
            return resolutions;
        }
    }

    public static final class Resolution {
        private final int level;
        private final Bounds bounds;
        private final List<Subband> subbands;

        private Resolution(int level, Bounds bounds, List<Subband> subbands) {
            this.level = level;
            this.bounds = bounds;
            this.subbands = immutable(subbands);
        }

        public int level() {
            return level;
        }

        public Bounds bounds() {
            return bounds;
        }

        public List<Subband> subbands() {
            return subbands;
        }
    }

    public static final class Subband {
        private final Orientation orientation;
        private final int offsetX;
        private final int offsetY;
        private final Bounds bounds;
        private final List<CodeBlock> codeBlocks;

        private Subband(
                Orientation orientation,
                int offsetX,
                int offsetY,
                Bounds bounds,
                List<CodeBlock> codeBlocks) {
            this.orientation = orientation;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.bounds = bounds;
            this.codeBlocks = immutable(codeBlocks);
        }

        public Orientation orientation() {
            return orientation;
        }

        public int offsetX() {
            return offsetX;
        }

        public int offsetY() {
            return offsetY;
        }

        public Bounds bounds() {
            return bounds;
        }

        public List<CodeBlock> codeBlocks() {
            return codeBlocks;
        }
    }

    public static final class CodeBlock {
        private final int offsetX;
        private final int offsetY;
        private final Bounds bounds;

        private CodeBlock(int offsetX, int offsetY, Bounds bounds) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.bounds = bounds;
        }

        public int offsetX() {
            return offsetX;
        }

        public int offsetY() {
            return offsetY;
        }

        public Bounds bounds() {
            return bounds;
        }
    }

    private static final class TileComponentData {
        private final Bounds bounds;
        private final List<BandData> bands;

        private TileComponentData(Bounds bounds, List<BandData> bands) {
            this.bounds = bounds;
            this.bands = bands;
        }
    }

    private static final class BandData {
        private final Orientation orientation;
        private final int offsetX;
        private final int offsetY;
        private final Bounds bounds;

        private BandData(Orientation orientation, int offsetX, int offsetY, Bounds bounds) {
            this.orientation = orientation;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.bounds = bounds;
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
