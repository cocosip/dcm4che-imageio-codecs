package io.github.cocosip.dcm4che.imageio.codecs.jpeg.internal;

import javax.imageio.IIOException;

/** Checked failure for malformed or unsupported JPEG syntax. */
public final class JpegException extends IIOException {
    private static final long serialVersionUID = 1L;

    public JpegException(String message) {
        super(message);
    }

    public JpegException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
