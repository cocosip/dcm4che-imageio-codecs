package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

import javax.imageio.IIOException;

public final class Jpeg2000Exception extends IIOException {
    private static final long serialVersionUID = 1L;

    public Jpeg2000Exception(String message) {
        super(message);
    }

    public Jpeg2000Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
