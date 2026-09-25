package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Exception;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Geometry;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionIterator;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;

/** One main-header POC interval and its bounded packet traversal. */
final class Jpeg2000ProgressionChange {
    private final int startResolution;
    private final int startComponent;
    private final int endLayer;
    private final int endResolution;
    private final int endComponent;
    private final Jpeg2000ProgressionOrder order;

    private Jpeg2000ProgressionChange(int startResolution, int startComponent,
            int endLayer, int endResolution, int endComponent,
            Jpeg2000ProgressionOrder order) {
        this.startResolution = startResolution;
        this.startComponent = startComponent;
        this.endLayer = endLayer;
        this.endResolution = endResolution;
        this.endComponent = endComponent;
        this.order = order;
    }

    static List<Jpeg2000ProgressionChange> parse(byte[] payload, int components,
            int resolutions, int layers) throws Jpeg2000Exception {
        int componentBytes = components < 257 ? 1 : 2;
        int entryBytes = 5 + 2 * componentBytes;
        if (payload.length == 0 || payload.length % entryBytes != 0) {
            throw new Jpeg2000Exception("JPEG 2000 POC payload length is invalid");
        }
        List<Jpeg2000ProgressionChange> changes =
                new ArrayList<Jpeg2000ProgressionChange>();
        int previousLayer = 0;
        for (int offset = 0; offset < payload.length; offset += entryBytes) {
            int startResolution = payload[offset] & 0xff;
            int startComponent = componentBytes == 1 ? payload[offset + 1] & 0xff
                    : ((payload[offset + 1] & 0xff) << 8) | (payload[offset + 2] & 0xff);
            int layerOffset = offset + 1 + componentBytes;
            int endLayer = ((payload[layerOffset] & 0xff) << 8)
                    | (payload[layerOffset + 1] & 0xff);
            int endResolution = payload[layerOffset + 2] & 0xff;
            int endComponentOffset = layerOffset + 3;
            int endComponent = componentBytes == 1 ? payload[endComponentOffset] & 0xff
                    : ((payload[endComponentOffset] & 0xff) << 8)
                            | (payload[endComponentOffset + 1] & 0xff);
            Jpeg2000ProgressionOrder order = Jpeg2000ProgressionOrder.fromCode(
                    payload[offset + entryBytes - 1] & 0xff);
            if (startResolution >= endResolution || endResolution > resolutions
                    || startComponent >= endComponent || endComponent > components
                    || endLayer <= previousLayer || endLayer > layers) {
                throw new Jpeg2000Exception("JPEG 2000 POC progression interval is invalid");
            }
            changes.add(new Jpeg2000ProgressionChange(startResolution,
                    startComponent, endLayer, endResolution, endComponent, order));
            previousLayer = endLayer;
        }
        return changes;
    }

    static List<Jpeg2000ProgressionIterator.PacketCoordinate> enumerate(
            List<Jpeg2000ProgressionChange> changes,
            Jpeg2000ProgressionOrder defaultOrder, int layers,
            Jpeg2000Geometry.Tile tile, Jpeg2000Limits limits) throws Jpeg2000Exception {
        if (changes.isEmpty()) {
            return Jpeg2000ProgressionIterator.enumerate(defaultOrder, layers, tile, limits);
        }
        List<Jpeg2000ProgressionIterator.PacketCoordinate> result =
                new ArrayList<Jpeg2000ProgressionIterator.PacketCoordinate>();
        Set<Jpeg2000ProgressionIterator.PacketCoordinate> seen =
                new HashSet<Jpeg2000ProgressionIterator.PacketCoordinate>();
        int firstLayer = 0;
        for (Jpeg2000ProgressionChange change : changes) {
            for (Jpeg2000ProgressionIterator.PacketCoordinate coordinate :
                    Jpeg2000ProgressionIterator.enumerate(change.order, layers, tile, limits)) {
                if (coordinate.layer() < firstLayer || coordinate.layer() >= change.endLayer
                        || coordinate.resolution() < change.startResolution
                        || coordinate.resolution() >= change.endResolution
                        || coordinate.component() < change.startComponent
                        || coordinate.component() >= change.endComponent) {
                    continue;
                }
                if (!seen.add(coordinate)) {
                    throw new Jpeg2000Exception("JPEG 2000 POC repeats a packet coordinate");
                }
                result.add(coordinate);
            }
            firstLayer = change.endLayer;
        }
        int expected = Jpeg2000ProgressionIterator.enumerate(
                defaultOrder, layers, tile, limits).size();
        if (seen.size() != expected) {
            throw new Jpeg2000Exception("JPEG 2000 POC does not cover every packet coordinate");
        }
        return result;
    }
}
