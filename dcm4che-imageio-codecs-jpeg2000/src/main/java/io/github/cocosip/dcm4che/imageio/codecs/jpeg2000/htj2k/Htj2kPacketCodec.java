package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.imageio.IIOException;

/** One-layer inline HT packet with a single cleanup pass per included block. */
final class Htj2kPacketCodec {
    private Htj2kPacketCodec() {
    }

    static byte[] encode(List<Band> bands) throws IIOException {
        if (bands == null || bands.isEmpty() || bands.size() > 3) {
            throw new IllegalArgumentException("HT packet needs one to three bands");
        }
        Htj2kPacketBits.Writer header = new Htj2kPacketBits.Writer();
        boolean present = false;
        for (Band band : bands) {
            for (Contribution block : band.blocks) {
                present |= block.data.length != 0;
            }
        }
        header.bit(present ? 1 : 0);
        if (!present) {
            return header.finish();
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Band band : bands) {
            boolean bandPresent = false;
            for (Contribution block : band.blocks) {
                bandPresent |= block.data.length != 0;
            }
            if (!bandPresent) {
                header.bit(0);
                continue;
            }
            Htj2kTagTree inclusion = new Htj2kTagTree(band.width, band.height);
            Htj2kTagTree missing = new Htj2kTagTree(band.width, band.height);
            for (int i = 0; i < band.blocks.size(); i++) {
                Contribution block = band.blocks.get(i);
                inclusion.set(i % band.width, i / band.width,
                        block.data.length == 0 ? 1 : 0);
                missing.set(i % band.width, i / band.width, block.missingMsbs);
            }
            for (int i = 0; i < band.blocks.size(); i++) {
                int x = i % band.width;
                int y = i / band.width;
                Contribution block = band.blocks.get(i);
                inclusion.encode(header, x, y, 1);
                if (block.data.length == 0) {
                    continue;
                }
                missing.encode(header, x, y, block.missingMsbs + 1);
                header.bit(0);
                int lengthBits = 32 - Integer.numberOfLeadingZeros(block.data.length);
                int extra = Math.max(0, lengthBits - 3);
                for (int j = 0; j < extra; j++) {
                    header.bit(1);
                }
                header.bit(0);
                header.bits(block.data.length, 3 + extra);
                body.write(block.data, 0, block.data.length);
            }
        }
        byte[] headerBytes = header.finish();
        byte[] bodyBytes = body.toByteArray();
        byte[] packet = Arrays.copyOf(headerBytes, headerBytes.length + bodyBytes.length);
        System.arraycopy(bodyBytes, 0, packet, headerBytes.length, bodyBytes.length);
        return packet;
    }

    static List<Band> decode(byte[] packet, List<int[]> bandSizes) throws IIOException {
        if (packet == null || packet.length == 0 || bandSizes == null
                || bandSizes.isEmpty() || bandSizes.size() > 3) {
            throw new IIOException("Invalid HT packet data or band layout");
        }
        Htj2kPacketBits.Reader header = new Htj2kPacketBits.Reader(
                packet, 0, packet.length);
        boolean present = header.bit() != 0;
        List<Band> result = new ArrayList<Band>();
        List<int[]> lengths = new ArrayList<int[]>();
        int total = 0;
        for (int[] size : bandSizes) {
            if (size == null || size.length != 2 || size[0] <= 0 || size[1] <= 0
                    || (long) size[0] * size[1] > 4096) {
                throw new IIOException("Invalid HT packet band grid");
            }
            List<Contribution> blocks = new ArrayList<Contribution>();
            int[] bandLengths = new int[size[0] * size[1]];
            Htj2kTagTree inclusion = new Htj2kTagTree(size[0], size[1]);
            Htj2kTagTree missing = new Htj2kTagTree(size[0], size[1]);
            boolean bandPresent = present && header.bit() != 0;
            if (bandPresent) {
                inclusion.primeRootKnownZero();
            }
            for (int i = 0; i < bandLengths.length; i++) {
                int x = i % size[0];
                int y = i / size[0];
                if (!bandPresent || !inclusion.decode(header, x, y, 1)) {
                    blocks.add(new Contribution(0, new byte[0]));
                    continue;
                }
                int missingMsbs = missing.decodeValue(header, x, y);
                if (header.bit() != 0) {
                    throw new IIOException("HT packet refinement passes are unsupported");
                }
                int extra = 0;
                while (header.bit() != 0) {
                    if (++extra > 13) {
                        throw new IIOException("HT packet Lblock exceeds cleanup limit");
                    }
                }
                int length = header.bits(3 + extra);
                if (length < 2 || length > 32768 || total > packet.length - length) {
                    throw new IIOException("Invalid HT packet cleanup length");
                }
                total += length;
                bandLengths[i] = length;
                blocks.add(new Contribution(missingMsbs, new byte[0]));
            }
            lengths.add(bandLengths);
            result.add(new Band(size[0], size[1], blocks));
        }
        header.align();
        int position = header.bytesRead();
        if (position + total != packet.length) {
            throw new IIOException("HT packet header/body length mismatch");
        }
        List<Band> decoded = new ArrayList<Band>();
        for (int bandIndex = 0; bandIndex < result.size(); bandIndex++) {
            Band band = result.get(bandIndex);
            List<Contribution> blocks = new ArrayList<Contribution>();
            for (int i = 0; i < band.blocks.size(); i++) {
                int length = lengths.get(bandIndex)[i];
                blocks.add(new Contribution(band.blocks.get(i).missingMsbs,
                        Arrays.copyOfRange(packet, position, position + length)));
                position += length;
            }
            decoded.add(new Band(band.width, band.height, blocks));
        }
        return Collections.unmodifiableList(decoded);
    }

    static final class Band {
        final int width;
        final int height;
        final List<Contribution> blocks;

        Band(int width, int height, List<Contribution> blocks) {
            if (width <= 0 || height <= 0 || (long) width * height > 4096
                    || blocks == null || blocks.size() != width * height) {
                throw new IllegalArgumentException("Invalid HT packet band");
            }
            this.width = width;
            this.height = height;
            this.blocks = Collections.unmodifiableList(new ArrayList<Contribution>(blocks));
        }
    }

    static final class Contribution {
        final int missingMsbs;
        final byte[] data;

        Contribution(int missingMsbs, byte[] data) {
            if (missingMsbs < 0 || missingMsbs > 30 || data == null
                    || (data.length != 0 && (data.length < 2 || data.length > 32768))) {
                throw new IllegalArgumentException("Invalid HT packet contribution");
            }
            this.missingMsbs = missingMsbs;
            this.data = data.clone();
        }
    }
}
