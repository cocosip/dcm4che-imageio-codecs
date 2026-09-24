package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class Jpeg2000GeometryTest {
    private final Jpeg2000Limits limits = Jpeg2000Limits.defaults();

    @Test
    void buildsOddOriginResolutionAndSubbandBounds() throws Exception {
        Jpeg2000Geometry.Image image = Jpeg2000Geometry.create(
                1, 3, 8, 10,
                0, 0, 8, 10,
                1, 2, 4, 4,
                limits);

        assertEquals(1, image.tiles().size());
        Jpeg2000Geometry.Component component = image.tiles().get(0).components().get(0);
        assertBounds(component.bounds(), 1, 3, 8, 10);
        assertEquals(3, component.resolutions().size());

        Jpeg2000Geometry.Resolution lowest = component.resolutions().get(0);
        assertBounds(lowest.bounds(), 1, 1, 2, 3);
        assertEquals(Jpeg2000Geometry.Orientation.LL, lowest.subbands().get(0).orientation());

        Jpeg2000Geometry.Resolution middle = component.resolutions().get(1);
        assertBounds(middle.bounds(), 1, 2, 4, 5);
        assertEquals(3, middle.subbands().size());
        assertBounds(middle.subbands().get(0).bounds(), 0, 1, 2, 3);
        assertBounds(middle.subbands().get(1).bounds(), 1, 1, 2, 2);
        assertBounds(middle.subbands().get(2).bounds(), 0, 1, 2, 2);

        Jpeg2000Geometry.Resolution full = component.resolutions().get(2);
        assertBounds(full.bounds(), 1, 3, 8, 10);
        assertBounds(full.subbands().get(0).bounds(), 0, 2, 4, 5);
        assertBounds(full.subbands().get(1).bounds(), 1, 1, 4, 5);
        assertBounds(full.subbands().get(2).bounds(), 0, 1, 4, 5);
    }

    @Test
    void clipsTilesAndCodeBlocksAtImageAndSubbandEdges() throws Exception {
        Jpeg2000Geometry.Image image = Jpeg2000Geometry.create(
                1, 1, 10, 8,
                0, 0, 6, 5,
                1, 1, 4, 4,
                limits);

        assertEquals(4, image.tiles().size());
        assertBounds(image.tiles().get(0).bounds(), 1, 1, 6, 5);
        assertBounds(image.tiles().get(3).bounds(), 6, 5, 10, 8);

        List<Jpeg2000Geometry.CodeBlock> blocks = image.tiles().get(3)
                .components().get(0)
                .resolutions().get(1)
                .subbands().get(2)
                .codeBlocks();
        assertEquals(2, blocks.size());
        assertBounds(blocks.get(0).bounds(), 3, 2, 4, 4);
        assertBounds(blocks.get(1).bounds(), 4, 2, 5, 4);
    }

    @Test
    void doesNotCreateCodeBlocksForEmptyOddOriginSubbands() throws Exception {
        Jpeg2000Geometry.Component component = Jpeg2000Geometry.create(
                1, 1, 2, 2,
                1, 1, 1, 1,
                1, 1, 4, 4,
                limits).tiles().get(0).components().get(0);

        assertEquals(0, component.resolutions().get(0).subbands().get(0).codeBlocks().size());
        assertEquals(0, component.resolutions().get(1).subbands().get(0).codeBlocks().size());
        assertEquals(0, component.resolutions().get(1).subbands().get(1).codeBlocks().size());
        assertEquals(1, component.resolutions().get(1).subbands().get(2).codeBlocks().size());
    }

    @Test
    void rejectsGeometryBeforeAllocatingCollections() {
        Jpeg2000Limits small = new Jpeg2000Limits(64, 8, 1, 1, 8, 10, 1);
        Jpeg2000Limits tileLimited = new Jpeg2000Limits(64, 16, 1, 16, 8, 10, 1);

        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000Geometry.create(
                0, 0, 4, 4,
                0, 0, 4, 4,
                1, 1, 4, 4,
                small));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000Geometry.create(
                0, 0, 4, 4,
                0, 0, 2, 2,
                1, 1, 4, 4,
                tileLimited));
    }

    @Test
    void rejectsCodeBlockDimensionsOutsidePartOneLimits() {
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000Geometry.create(
                0, 0, 8, 8,
                0, 0, 8, 8,
                1, 1, 2, 4,
                limits));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000Geometry.create(
                0, 0, 8, 8,
                0, 0, 8, 8,
                1, 1, 1024, 8,
                limits));
        assertThrows(Jpeg2000Exception.class, () -> Jpeg2000Geometry.create(
                0, 0, 8, 8,
                0, 0, 8, 8,
                1, 1, 2048, 2,
                limits));
    }

    private static void assertBounds(
            Jpeg2000Geometry.Bounds bounds,
            long x0,
            long y0,
            long x1,
            long y1) {
        assertEquals(x0, bounds.x0());
        assertEquals(y0, bounds.y0());
        assertEquals(x1, bounds.x1());
        assertEquals(y1, bounds.y1());
    }
}
