package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Stateless packet-coordinate iteration for the five Part 1 progression orders. */
public final class Jpeg2000ProgressionIterator {
    private Jpeg2000ProgressionIterator() {
    }

    public static List<PacketCoordinate> enumerate(
            Jpeg2000ProgressionOrder order,
            int layerCount,
            int[][] precinctCounts,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        if (order == null) {
            throw new NullPointerException("order");
        }
        if (precinctCounts == null) {
            throw new NullPointerException("precinctCounts");
        }
        if (limits == null) {
            throw new NullPointerException("limits");
        }
        limits.requireQualityLayerCount(layerCount);
        if (precinctCounts.length == 0) {
            throw new Jpeg2000Exception("JPEG 2000 progression requires at least one component");
        }

        int resolutionCount = 0;
        long packetsPerLayer = 0;
        for (int component = 0; component < precinctCounts.length; component++) {
            int[] resolutions = precinctCounts[component];
            if (resolutions == null || resolutions.length == 0) {
                throw new Jpeg2000Exception(
                        "JPEG 2000 progression component " + component + " has no resolutions");
            }
            resolutionCount = Math.max(resolutionCount, resolutions.length);
            for (int resolution = 0; resolution < resolutions.length; resolution++) {
                if (resolutions[resolution] < 0) {
                    throw new Jpeg2000Exception(
                            "JPEG 2000 precinct count cannot be negative");
                }
                packetsPerLayer = checkedAdd(packetsPerLayer, resolutions[resolution]);
            }
        }
        long packetCount = checkedMultiply(packetsPerLayer, layerCount);
        int checkedPacketCount = limits.requirePacketCount(packetCount);
        List<PacketCoordinate> result = new ArrayList<PacketCoordinate>(checkedPacketCount);

        switch (order) {
            case LRCP:
                enumerateLrcp(result, layerCount, precinctCounts, resolutionCount);
                break;
            case RLCP:
                enumerateRlcp(result, layerCount, precinctCounts, resolutionCount);
                break;
            case RPCL:
                enumerateRpcl(result, layerCount, precinctCounts, resolutionCount);
                break;
            case PCRL:
                enumeratePcrl(result, layerCount, precinctCounts, resolutionCount);
                break;
            case CPRL:
                enumerateCprl(result, layerCount, precinctCounts);
                break;
            default:
                throw new Jpeg2000Exception("Unsupported JPEG 2000 progression order " + order);
        }
        return Collections.unmodifiableList(result);
    }

    public static List<PacketCoordinate> enumerate(
            Jpeg2000ProgressionOrder order,
            int layerCount,
            Jpeg2000Geometry.Tile tile,
            Jpeg2000Limits limits) throws Jpeg2000Exception {
        if (order == null) {
            throw new NullPointerException("order");
        }
        if (tile == null) {
            throw new NullPointerException("tile");
        }
        int[][] precinctCounts = new int[tile.components().size()][];
        for (int component = 0; component < tile.components().size(); component++) {
            List<Jpeg2000Geometry.Resolution> resolutions =
                    tile.components().get(component).resolutions();
            precinctCounts[component] = new int[resolutions.size()];
            for (int resolution = 0; resolution < resolutions.size(); resolution++) {
                precinctCounts[component][resolution] = resolutions.get(resolution).precincts().size();
            }
        }

        List<PacketCoordinate> result = new ArrayList<PacketCoordinate>(
                enumerate(Jpeg2000ProgressionOrder.LRCP, layerCount, precinctCounts, limits));
        Collections.sort(result, geometryComparator(order, tile));
        return Collections.unmodifiableList(result);
    }

    private static Comparator<PacketCoordinate> geometryComparator(
            final Jpeg2000ProgressionOrder order,
            final Jpeg2000Geometry.Tile tile) {
        return new Comparator<PacketCoordinate>() {
            @Override
            public int compare(PacketCoordinate left, PacketCoordinate right) {
                Jpeg2000Geometry.Precinct leftPrecinct = precinct(tile, left);
                Jpeg2000Geometry.Precinct rightPrecinct = precinct(tile, right);
                switch (order) {
                    case LRCP:
                        return compareFields(left, right, leftPrecinct, rightPrecinct, 0, 1, 2, 5);
                    case RLCP:
                        return compareFields(left, right, leftPrecinct, rightPrecinct, 1, 0, 2, 5);
                    case RPCL:
                        return compareFields(left, right, leftPrecinct, rightPrecinct, 1, 4, 3, 2, 0);
                    case PCRL:
                        return compareFields(left, right, leftPrecinct, rightPrecinct, 4, 3, 2, 1, 0);
                    case CPRL:
                        return compareFields(left, right, leftPrecinct, rightPrecinct, 2, 4, 3, 1, 0);
                    default:
                        throw new IllegalArgumentException("Unsupported JPEG 2000 progression order " + order);
                }
            }
        };
    }

    private static Jpeg2000Geometry.Precinct precinct(
            Jpeg2000Geometry.Tile tile, PacketCoordinate coordinate) {
        return tile.components().get(coordinate.component())
                .resolutions().get(coordinate.resolution())
                .precincts().get(coordinate.precinct());
    }

    private static int compareFields(
            PacketCoordinate left,
            PacketCoordinate right,
            Jpeg2000Geometry.Precinct leftPrecinct,
            Jpeg2000Geometry.Precinct rightPrecinct,
            int... fields) {
        for (int field : fields) {
            int comparison;
            switch (field) {
                case 0:
                    comparison = Integer.compare(left.layer(), right.layer());
                    break;
                case 1:
                    comparison = Integer.compare(left.resolution(), right.resolution());
                    break;
                case 2:
                    comparison = Integer.compare(left.component(), right.component());
                    break;
                case 3:
                    comparison = Long.compare(leftPrecinct.referenceX(), rightPrecinct.referenceX());
                    break;
                case 4:
                    comparison = Long.compare(leftPrecinct.referenceY(), rightPrecinct.referenceY());
                    break;
                case 5:
                    comparison = Integer.compare(left.precinct(), right.precinct());
                    break;
                default:
                    throw new AssertionError(field);
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static void enumerateLrcp(
            List<PacketCoordinate> result, int layers, int[][] counts, int resolutions) {
        for (int layer = 0; layer < layers; layer++) {
            for (int resolution = 0; resolution < resolutions; resolution++) {
                for (int component = 0; component < counts.length; component++) {
                    appendPrecincts(result, layer, resolution, component, count(counts, component, resolution));
                }
            }
        }
    }

    private static void enumerateRlcp(
            List<PacketCoordinate> result, int layers, int[][] counts, int resolutions) {
        for (int resolution = 0; resolution < resolutions; resolution++) {
            for (int layer = 0; layer < layers; layer++) {
                for (int component = 0; component < counts.length; component++) {
                    appendPrecincts(result, layer, resolution, component, count(counts, component, resolution));
                }
            }
        }
    }

    private static void enumerateRpcl(
            List<PacketCoordinate> result, int layers, int[][] counts, int resolutions) {
        for (int resolution = 0; resolution < resolutions; resolution++) {
            int maxPrecincts = maxPrecincts(counts, resolution, -1);
            for (int precinct = 0; precinct < maxPrecincts; precinct++) {
                for (int component = 0; component < counts.length; component++) {
                    if (precinct >= count(counts, component, resolution)) {
                        continue;
                    }
                    for (int layer = 0; layer < layers; layer++) {
                        result.add(new PacketCoordinate(layer, resolution, component, precinct));
                    }
                }
            }
        }
    }

    private static void enumeratePcrl(
            List<PacketCoordinate> result, int layers, int[][] counts, int resolutions) {
        int maxPrecincts = maxPrecincts(counts, -1, -1);
        for (int precinct = 0; precinct < maxPrecincts; precinct++) {
            for (int component = 0; component < counts.length; component++) {
                for (int resolution = 0; resolution < resolutions; resolution++) {
                    if (precinct >= count(counts, component, resolution)) {
                        continue;
                    }
                    for (int layer = 0; layer < layers; layer++) {
                        result.add(new PacketCoordinate(layer, resolution, component, precinct));
                    }
                }
            }
        }
    }

    private static void enumerateCprl(
            List<PacketCoordinate> result, int layers, int[][] counts) {
        for (int component = 0; component < counts.length; component++) {
            int maxPrecincts = maxPrecincts(counts, -1, component);
            for (int precinct = 0; precinct < maxPrecincts; precinct++) {
                for (int resolution = 0; resolution < counts[component].length; resolution++) {
                    if (precinct >= counts[component][resolution]) {
                        continue;
                    }
                    for (int layer = 0; layer < layers; layer++) {
                        result.add(new PacketCoordinate(layer, resolution, component, precinct));
                    }
                }
            }
        }
    }

    private static void appendPrecincts(
            List<PacketCoordinate> result,
            int layer,
            int resolution,
            int component,
            int precinctCount) {
        for (int precinct = 0; precinct < precinctCount; precinct++) {
            result.add(new PacketCoordinate(layer, resolution, component, precinct));
        }
    }

    private static int count(int[][] counts, int component, int resolution) {
        return resolution < counts[component].length ? counts[component][resolution] : 0;
    }

    private static int maxPrecincts(int[][] counts, int resolution, int selectedComponent) {
        int maximum = 0;
        int firstComponent = selectedComponent >= 0 ? selectedComponent : 0;
        int componentEnd = selectedComponent >= 0 ? selectedComponent + 1 : counts.length;
        for (int component = firstComponent; component < componentEnd; component++) {
            if (resolution >= 0) {
                maximum = Math.max(maximum, count(counts, component, resolution));
            } else {
                for (int value : counts[component]) {
                    maximum = Math.max(maximum, value);
                }
            }
        }
        return maximum;
    }

    private static long checkedAdd(long left, int right) throws Jpeg2000Exception {
        if (left > Long.MAX_VALUE - right) {
            throw new Jpeg2000Exception("JPEG 2000 packet count overflow");
        }
        return left + right;
    }

    private static long checkedMultiply(long left, int right) throws Jpeg2000Exception {
        if (left > Long.MAX_VALUE / right) {
            throw new Jpeg2000Exception("JPEG 2000 packet count overflow");
        }
        return left * right;
    }

    public static final class PacketCoordinate {
        private final int layer;
        private final int resolution;
        private final int component;
        private final int precinct;

        public PacketCoordinate(int layer, int resolution, int component, int precinct) {
            if (layer < 0 || resolution < 0 || component < 0 || precinct < 0) {
                throw new IllegalArgumentException("JPEG 2000 packet coordinates cannot be negative");
            }
            this.layer = layer;
            this.resolution = resolution;
            this.component = component;
            this.precinct = precinct;
        }

        public int layer() {
            return layer;
        }

        public int resolution() {
            return resolution;
        }

        public int component() {
            return component;
        }

        public int precinct() {
            return precinct;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PacketCoordinate)) {
                return false;
            }
            PacketCoordinate coordinate = (PacketCoordinate) other;
            return layer == coordinate.layer
                    && resolution == coordinate.resolution
                    && component == coordinate.component
                    && precinct == coordinate.precinct;
        }

        @Override
        public int hashCode() {
            int result = layer;
            result = 31 * result + resolution;
            result = 31 * result + component;
            result = 31 * result + precinct;
            return result;
        }

        @Override
        public String toString() {
            return layer + ":" + resolution + ":" + component + ":" + precinct;
        }
    }
}
