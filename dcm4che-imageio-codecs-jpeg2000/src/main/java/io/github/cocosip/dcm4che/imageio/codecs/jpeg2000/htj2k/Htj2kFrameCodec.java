package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.stream.ImageInputStream;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;

/** Transfer-syntax-specific entry point for the HT codec family. */
public final class Htj2kFrameCodec {
    public static final String LOSSLESS_UID = "1.2.840.10008.1.2.4.201";
    public static final String LOSSLESS_RPCL_UID = "1.2.840.10008.1.2.4.202";
    public static final String LOSSY_UID = "1.2.840.10008.1.2.4.203";

    private final String transferSyntaxUid;
    private final boolean reversible;
    private final boolean selectableProgression;

    private Htj2kFrameCodec(String transferSyntaxUid, boolean reversible,
            boolean selectableProgression) {
        this.transferSyntaxUid = transferSyntaxUid;
        this.reversible = reversible;
        this.selectableProgression = selectableProgression;
    }

    public static Htj2kFrameCodec forTransferSyntax(String uid) throws IIOException {
        if (LOSSLESS_UID.equals(uid)) {
            return new Htj2kFrameCodec(uid, true, false);
        }
        if (LOSSLESS_RPCL_UID.equals(uid)) {
            return new Htj2kFrameCodec(uid, true, true);
        }
        if (LOSSY_UID.equals(uid)) {
            return new Htj2kFrameCodec(uid, false, false);
        }
        throw new IIOException("Unsupported HTJ2K transfer syntax " + uid);
    }

    public Htj2kCodestream inspect(ImageInputStream input, long frameLength) throws IOException {
        return inspect(input, frameLength, Jpeg2000Limits.defaults());
    }

    public Htj2kCodestream inspect(ImageInputStream input, long frameLength,
            Jpeg2000Limits limits) throws IOException {
        try {
            return new Htj2kCodestreamParser(input, frameLength, limits, this).parse();
        } catch (IOException error) {
            throw new IIOException("HTJ2K " + transferSyntaxUid + " inspect frame 0: "
                    + error.getMessage(), error);
        }
    }

    String transferSyntaxUid() {
        return transferSyntaxUid;
    }

    boolean reversible() {
        return reversible;
    }

    boolean selectableProgression() {
        return selectableProgression;
    }
}
