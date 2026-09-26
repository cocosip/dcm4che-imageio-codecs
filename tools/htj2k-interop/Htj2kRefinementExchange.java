package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import javax.imageio.stream.MemoryCacheImageInputStream;

import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Limits;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common.Jpeg2000Raster;

/** Produces a deterministic full codestream with a three-pass LL code-block. */
public final class Htj2kRefinementExchange {
    public static void main(String[] args) throws Exception {
        int[] source = new int[64 * 64];
        Arrays.fill(source, 129);
        Jpeg2000Raster raster = Jpeg2000Raster.of(64, 64, 8, 8, false,
                "MONOCHROME2", new int[][] {source}, Jpeg2000Limits.defaults());
        Htj2kFrameCodec codec = Htj2kFrameCodec.forTransferSyntax(
                Htj2kFrameCodec.LOSSLESS_UID);
        byte[] encoded = codec.encode(raster);
        Htj2kCodestream parsed = codec.inspect(new MemoryCacheImageInputStream(
                new ByteArrayInputStream(encoded)), encoded.length);
        Htj2kCodestream.TilePart first = parsed.tileParts().get(0);
        int kmax = Htj2kQuantizer.kmax(parsed.quantizationPayload(), 0, 0);
        byte[] cleanup = Htj2kCleanupPassEncoder.encode(new Htj2kCodeBlock(
                2, 2, kmax, new int[] {1, 0, 0, 0}));
        byte[] data = Arrays.copyOf(cleanup, cleanup.length + 2);
        data[data.length - 2] = 1;
        data[data.length - 1] = 1;
        byte[] packet = Htj2kPacketCodec.encode(Collections.singletonList(
                new Htj2kPacketCodec.Band(1, 1, Collections.singletonList(
                        new Htj2kPacketCodec.Contribution(kmax - 3,
                                data, cleanup.length, 3)))));
        byte[] changed = new byte[encoded.length - first.dataLength + packet.length];
        System.arraycopy(encoded, 0, changed, 0, first.dataOffset);
        System.arraycopy(packet, 0, changed, first.dataOffset, packet.length);
        System.arraycopy(encoded, first.dataOffset + first.dataLength, changed,
                first.dataOffset + packet.length,
                encoded.length - first.dataOffset - first.dataLength);
        int sot = first.dataOffset - 14;
        put32(changed, sot + 6, packet.length + 14);
        int tlm = -1;
        for (int i = 0; i < sot - 1; i++) {
            if ((changed[i] & 0xff) == 0xff && (changed[i + 1] & 0xff) == 0x55) {
                tlm = i;
                break;
            }
        }
        if (tlm < 0) {
            throw new AssertionError("TLM marker missing");
        }
        put32(changed, tlm + 6, packet.length + 14);
        byte[] expected = codec.decode(changed).toFrame(false);
        Files.write(Path.of(args[0]), changed);
        Files.write(Path.of(args[1]), expected);
        System.out.println("three-pass codestream " + changed.length
                + " bytes, cleanup " + cleanup.length + ", packet " + packet.length);
    }

    private static void put32(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
