package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.classic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CodingStyleSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000CommentSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ComponentCodingStyleSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ComponentQuantizationSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000QuantizationSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000SizeSegment;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000StartOfTileSegment;

public final class Jpeg2000ClassicCodestream {
    private final Jpeg2000SizeSegment size;
    private final Jpeg2000CodingStyleSegment codingStyle;
    private final List<Jpeg2000ComponentCodingStyleSegment> componentCodingStyles;
    private final Jpeg2000QuantizationSegment quantization;
    private final List<Jpeg2000ComponentQuantizationSegment> componentQuantizations;
    private final List<Jpeg2000CommentSegment> comments;
    private final List<Jpeg2000ProgressionChange> progressionChanges;
    private final int[] regionShifts;
    private final List<Jpeg2000ClassicTilePart> tileParts;
    private final long logicalLength;

    Jpeg2000ClassicCodestream(
            Jpeg2000SizeSegment size,
            Jpeg2000CodingStyleSegment codingStyle,
            List<Jpeg2000ComponentCodingStyleSegment> componentCodingStyles,
            Jpeg2000QuantizationSegment quantization,
            List<Jpeg2000ComponentQuantizationSegment> componentQuantizations,
            List<Jpeg2000CommentSegment> comments,
            List<Jpeg2000ProgressionChange> progressionChanges,
            int[] regionShifts,
            List<Jpeg2000ClassicTilePart> tileParts,
            long logicalLength) {
        this.size = size;
        this.codingStyle = codingStyle;
        this.componentCodingStyles = immutableCopy(componentCodingStyles);
        this.quantization = quantization;
        this.componentQuantizations = immutableCopy(componentQuantizations);
        this.comments = immutableCopy(comments);
        this.progressionChanges = immutableCopy(progressionChanges);
        this.regionShifts = regionShifts.clone();
        if (tileParts == null || tileParts.isEmpty()) {
            throw new IllegalArgumentException("JPEG 2000 codestream requires at least one tile-part");
        }
        this.tileParts = immutableCopy(tileParts);
        this.logicalLength = logicalLength;
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    public Jpeg2000SizeSegment size() {
        return size;
    }

    public Jpeg2000CodingStyleSegment codingStyle() {
        return codingStyle;
    }

    public List<Jpeg2000ComponentCodingStyleSegment> componentCodingStyles() {
        return componentCodingStyles;
    }

    public Jpeg2000QuantizationSegment quantization() {
        return quantization;
    }

    public List<Jpeg2000ComponentQuantizationSegment> componentQuantizations() {
        return componentQuantizations;
    }

    public List<Jpeg2000CommentSegment> comments() {
        return comments;
    }

    List<Jpeg2000ProgressionChange> progressionChanges() {
        return progressionChanges;
    }

    int regionShift(int component) {
        return regionShifts[component];
    }

    public Jpeg2000StartOfTileSegment startOfTile() {
        return tileParts.get(0).startOfTile();
    }

    public byte[] tileData() {
        return tileParts.get(0).data();
    }

    public List<Jpeg2000ClassicTilePart> tileParts() {
        return tileParts;
    }

    public long logicalLength() {
        return logicalLength;
    }
}
