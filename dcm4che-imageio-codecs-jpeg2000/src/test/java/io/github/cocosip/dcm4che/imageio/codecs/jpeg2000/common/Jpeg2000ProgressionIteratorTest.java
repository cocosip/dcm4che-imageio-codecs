package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class Jpeg2000ProgressionIteratorTest {
    @Test
    void enumeratesEveryPacketOnceInAllFiveOrders() throws Exception {
        int[][] precinctCounts = {
                {2, 1},
                {1, 2}
        };
        for (Jpeg2000ProgressionOrder order : Jpeg2000ProgressionOrder.values()) {
            List<Jpeg2000ProgressionIterator.PacketCoordinate> actual =
                    Jpeg2000ProgressionIterator.enumerate(
                            order, 2, precinctCounts, Jpeg2000Limits.defaults());

            assertEquals(12, actual.size());
            assertEquals(12, new HashSet<Jpeg2000ProgressionIterator.PacketCoordinate>(actual).size());

            List<Jpeg2000ProgressionIterator.PacketCoordinate> expected = allCoordinates(2, precinctCounts);
            expected.sort(comparator(order));
            assertEquals(expected, actual, order.name());
        }
    }

    @Test
    void validatesDimensionsCountsAndPacketLimitBeforeEnumeration() {
        assertThrows(NullPointerException.class, () -> Jpeg2000ProgressionIterator.enumerate(
                null, 1, new int[][] {{1}}, Jpeg2000Limits.defaults()));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000ProgressionIterator.enumerate(
                Jpeg2000ProgressionOrder.LRCP, 0, new int[][] {{1}}, Jpeg2000Limits.defaults()));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000ProgressionIterator.enumerate(
                Jpeg2000ProgressionOrder.LRCP, 1, new int[][] {{-1}}, Jpeg2000Limits.defaults()));

        Jpeg2000Limits twoPackets = new Jpeg2000Limits(1024, 1024, 1, 8, 2, 64, 3);
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000ProgressionIterator.enumerate(
                Jpeg2000ProgressionOrder.LRCP, 2, new int[][] {{2}}, twoPackets));
    }

    @Test
    void usesSpatialPrecinctPositionsFromTileGeometry() throws Exception {
        Jpeg2000Geometry.Tile tile = Jpeg2000Geometry.create(
                0, 0, 16, 8,
                0, 0, 16, 8,
                1, 1, 4, 4,
                new int[] {4, 4}, new int[] {4, 4},
                Jpeg2000Limits.defaults()).tiles().get(0);

        for (Jpeg2000ProgressionOrder order : Jpeg2000ProgressionOrder.values()) {
            List<Jpeg2000ProgressionIterator.PacketCoordinate> ordered =
                    Jpeg2000ProgressionIterator.enumerate(
                            order, 1, tile, Jpeg2000Limits.defaults());
            assertEquals(10, ordered.size());
            assertEquals(10,
                    new HashSet<Jpeg2000ProgressionIterator.PacketCoordinate>(ordered).size());
        }

        List<Jpeg2000ProgressionIterator.PacketCoordinate> packets =
                Jpeg2000ProgressionIterator.enumerate(
                        Jpeg2000ProgressionOrder.PCRL,
                        1,
                        tile,
                        Jpeg2000Limits.defaults());

        assertEquals("0:0:0:0", packets.get(0).toString());
        assertEquals("0:1:0:0", packets.get(1).toString());
        assertEquals("0:1:0:1", packets.get(2).toString());
        assertEquals("0:0:0:1", packets.get(3).toString());
        assertEquals("0:1:0:2", packets.get(4).toString());
    }

    private static List<Jpeg2000ProgressionIterator.PacketCoordinate> allCoordinates(
            int layers, int[][] precinctCounts) {
        List<Jpeg2000ProgressionIterator.PacketCoordinate> result =
                new ArrayList<Jpeg2000ProgressionIterator.PacketCoordinate>();
        for (int layer = 0; layer < layers; layer++) {
            for (int component = 0; component < precinctCounts.length; component++) {
                for (int resolution = 0; resolution < precinctCounts[component].length; resolution++) {
                    for (int precinct = 0;
                            precinct < precinctCounts[component][resolution]; precinct++) {
                        result.add(new Jpeg2000ProgressionIterator.PacketCoordinate(
                                layer, resolution, component, precinct));
                    }
                }
            }
        }
        return result;
    }

    private static Comparator<Jpeg2000ProgressionIterator.PacketCoordinate> comparator(
            Jpeg2000ProgressionOrder order) {
        switch (order) {
            case LRCP:
                return fields(0, 1, 2, 3);
            case RLCP:
                return fields(1, 0, 2, 3);
            case RPCL:
                return fields(1, 3, 2, 0);
            case PCRL:
                return fields(3, 2, 1, 0);
            case CPRL:
                return fields(2, 3, 1, 0);
            default:
                throw new AssertionError(order);
        }
    }

    private static Comparator<Jpeg2000ProgressionIterator.PacketCoordinate> fields(
            int first, int second, int third, int fourth) {
        return (left, right) -> {
            int[] leftValues = values(left);
            int[] rightValues = values(right);
            for (int field : new int[] {first, second, third, fourth}) {
                int comparison = Integer.compare(leftValues[field], rightValues[field]);
                if (comparison != 0) {
                    return comparison;
                }
            }
            return 0;
        };
    }

    private static int[] values(Jpeg2000ProgressionIterator.PacketCoordinate coordinate) {
        return new int[] {
                coordinate.layer(), coordinate.resolution(),
                coordinate.component(), coordinate.precinct()
        };
    }
}
