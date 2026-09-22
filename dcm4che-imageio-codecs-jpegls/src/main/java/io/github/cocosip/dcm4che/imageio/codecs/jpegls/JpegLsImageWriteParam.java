package io.github.cocosip.dcm4che.imageio.codecs.jpegls;

import java.util.Locale;

import javax.imageio.ImageWriteParam;

/** ImageIO controls for DICOM JPEG-LS writers. */
public final class JpegLsImageWriteParam extends ImageWriteParam {
    private int allowedError;
    private int interleaveMode = 2;
    private int restartInterval;

    public JpegLsImageWriteParam(Locale locale) {
        super(locale);
        canWriteCompressed = true;
        compressionTypes = new String[] {"JPEG-LS"};
        compressionType = compressionTypes[0];
    }

    public int getAllowedError() {
        return allowedError;
    }

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

    public void setInterleaveMode(int interleaveMode) {
        if (interleaveMode < 0 || interleaveMode > 2) {
            throw new IllegalArgumentException("JPEG-LS interleave mode must be 0, 1, or 2");
        }
        this.interleaveMode = interleaveMode;
    }

    public int getRestartInterval() {
        return restartInterval;
    }

    public void setRestartInterval(int restartInterval) {
        if (restartInterval < 0) {
            throw new IllegalArgumentException("JPEG-LS restart interval must fit in 32 bits");
        }
        this.restartInterval = restartInterval;
    }

    @Override
    public void setCompressionMode(int mode) {
        super.setCompressionMode(mode);
        if (mode == MODE_EXPLICIT && compressionType == null) {
            compressionType = compressionTypes[0];
        }
    }
}
