package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageWriteParam;

/** ImageIO controls for DICOM JPEG-LS writers. */
public final class JpegLsImageWriteParam extends ImageWriteParam {
    private int allowedError;
    private int interleaveMode = 2;
    private int restartInterval;
    private List<JpegLsMappingTable> mappingTables = Collections.emptyList();
    private Map<Integer, Integer> componentMappingTableSelectors = Collections.emptyMap();

    /** Creates JPEG-LS write parameters for the supplied locale. */
    public JpegLsImageWriteParam(Locale locale) {
        super(locale);
        canWriteCompressed = true;
        compressionTypes = new String[] {"JPEG-LS"};
        compressionType = compressionTypes[0];
    }

    /** Returns the near-lossless allowed error (NEAR). */
    public int getAllowedError() {
        return allowedError;
    }

    /** Sets NEAR; valid values are zero through 255. */
    public void setAllowedError(int allowedError) {
        if (allowedError < 0 || allowedError > 255) {
            throw new IllegalArgumentException("JPEG-LS allowed error must be between 0 and 255");
        }
        this.allowedError = allowedError;
    }

    /** Returns JPEG-LS ILV: 0=None, 1=Line, or 2=Sample. */
    public int getInterleaveMode() {
        return interleaveMode;
    }

    /** Sets JPEG-LS ILV: 0=None, 1=Line, or 2=Sample. */
    public void setInterleaveMode(int interleaveMode) {
        if (interleaveMode < 0 || interleaveMode > 2) {
            throw new IllegalArgumentException("JPEG-LS interleave mode must be 0, 1, or 2");
        }
        this.interleaveMode = interleaveMode;
    }

    /** Returns the restart interval in image lines, or zero when disabled. */
    public int getRestartInterval() {
        return restartInterval;
    }

    /** Sets the restart interval in image lines; zero disables restart markers. */
    public void setRestartInterval(int restartInterval) {
        if (restartInterval < 0) {
            throw new IllegalArgumentException("JPEG-LS restart interval must fit in 32 bits");
        }
        this.restartInterval = restartInterval;
    }

    /** Returns the immutable mapping table list used by the writer. */
    public List<JpegLsMappingTable> getMappingTables() {
        return mappingTables;
    }

    /** Replaces the mapping table list used by the writer. */
    public void setMappingTables(List<JpegLsMappingTable> mappingTables) {
        if (mappingTables == null) {
            throw new IllegalArgumentException("mapping tables must not be null");
        }
        List<JpegLsMappingTable> copy = new ArrayList<JpegLsMappingTable>(mappingTables.size());
        for (JpegLsMappingTable table : mappingTables) {
            if (table == null) throw new IllegalArgumentException("mapping table must not be null");
            copy.add(table);
        }
        this.mappingTables = Collections.unmodifiableList(copy);
    }

    /** Returns component-to-table selectors keyed by JPEG-LS component id. */
    public Map<Integer, Integer> getComponentMappingTableSelectors() {
        return componentMappingTableSelectors;
    }

    /** Replaces all component-to-table selectors. */
    public void setComponentMappingTableSelectors(Map<Integer, Integer> selectors) {
        if (selectors == null) {
            throw new IllegalArgumentException("mapping table selectors must not be null");
        }
        Map<Integer, Integer> copy = new HashMap<Integer, Integer>();
        for (Map.Entry<Integer, Integer> entry : selectors.entrySet()) {
            setSelector(copy, entry.getKey(), entry.getValue());
        }
        this.componentMappingTableSelectors = Collections.unmodifiableMap(copy);
    }

    /** Sets one component-to-table selector. */
    public void setComponentMappingTableSelector(int componentSelector, int tableId) {
        Map<Integer, Integer> copy = new HashMap<Integer, Integer>(componentMappingTableSelectors);
        setSelector(copy, componentSelector, tableId);
        this.componentMappingTableSelectors = Collections.unmodifiableMap(copy);
    }

    private static void setSelector(Map<Integer, Integer> selectors, Integer componentSelector,
            Integer tableId) {
        if (componentSelector == null || componentSelector < 1 || componentSelector > 255) {
            throw new IllegalArgumentException("JPEG-LS component selector must be 1..255");
        }
        if (tableId == null || tableId < 1 || tableId > 255) {
            throw new IllegalArgumentException("JPEG-LS mapping table selector must be 1..255");
        }
        selectors.put(componentSelector, tableId);
    }

    @Override
    public void setCompressionMode(int mode) {
        super.setCompressionMode(mode);
        if (mode == MODE_EXPLICIT && compressionType == null) {
            compressionType = compressionTypes[0];
        }
    }
}
