package io.github.cocosip.dcm4che.imageio.codecs.jpeg;

import java.util.Locale;

import javax.imageio.ImageWriteParam;
import javax.imageio.IIOException;

/** ImageIO controls shared by the JPEG writers. */
public final class JpegImageWriteParam extends ImageWriteParam {
    private int restartInterval;
    private int pointTransform;

    public JpegImageWriteParam(Locale locale) {
        super(locale);
        canWriteCompressed = true;
        compressionTypes = new String[] {"JPEG"};
        compressionType = compressionTypes[0];
        compressionQuality = 0.75f;
    }

    public int getRestartInterval() {
        return restartInterval;
    }

    public void setRestartInterval(int restartInterval) {
        if (restartInterval < 0 || restartInterval > 0xffff) {
            throw new IllegalArgumentException("JPEG restart interval must fit in 16 bits");
        }
        this.restartInterval = restartInterval;
    }

    public int getPointTransform() {
        return pointTransform;
    }

    public void setPointTransform(int pointTransform) {
        if (pointTransform < 0 || pointTransform > 15) {
            throw new IllegalArgumentException("JPEG point transform must be between 0 and 15");
        }
        this.pointTransform = pointTransform;
    }

    @Override
    public void setCompressionMode(int mode) {
        super.setCompressionMode(mode);
        if (mode == MODE_EXPLICIT && compressionType == null) {
            compressionType = compressionTypes[0];
        }
    }

    static float quality(ImageWriteParam param) throws IIOException {
        if (param == null || !param.canWriteCompressed()
                || param.getCompressionMode() == ImageWriteParam.MODE_COPY_FROM_METADATA) {
            return -1.0f;
        }
        if (param.getCompressionMode() == ImageWriteParam.MODE_DISABLED) {
            throw new IIOException("JPEG compression cannot be disabled");
        }
        float quality = param.getCompressionQuality();
        if (Float.isNaN(quality) || quality < 0.0f || quality > 1.0f) {
            throw new IIOException("JPEG compression quality must be between 0 and 1");
        }
        return quality;
    }
}
