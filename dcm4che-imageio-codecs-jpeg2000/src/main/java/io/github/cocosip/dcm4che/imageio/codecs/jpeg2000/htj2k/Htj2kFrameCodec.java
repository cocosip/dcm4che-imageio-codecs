package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.stream.ImageInputStream;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000ProgressionOrder;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

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

    public byte[] encode(Jpeg2000Raster raster) throws IOException {
        return encode(raster, Jpeg2000ProgressionOrder.RPCL);
    }

    public byte[] encode(Jpeg2000Raster raster,
            Jpeg2000ProgressionOrder progression) throws IOException {
        if (!reversible) {
            return Htj2kSingleTileFrame.encodeLossy(raster);
        }
        if (progression == null) {
            throw new NullPointerException("progression");
        }
        return Htj2kSingleTileFrame.encode(raster,
                selectableProgression ? progression : Jpeg2000ProgressionOrder.RPCL);
    }

    public Jpeg2000Raster decode(byte[] codestream) throws IOException {
        if (!reversible) {
            return Htj2kSingleTileFrame.decodeLossy(codestream, this);
        }
        return Htj2kSingleTileFrame.decode(codestream, this);
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
