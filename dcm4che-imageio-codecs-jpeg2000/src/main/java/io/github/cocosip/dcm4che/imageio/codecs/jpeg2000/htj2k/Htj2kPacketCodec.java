package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.htj2k;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.imageio.IIOException;

/** One-layer inline HT packet with cleanup and optional refinement segments. */
final class Htj2kPacketCodec {
    private Htj2kPacketCodec() {
    }

    static byte[] encode(List<Band> bands) throws IIOException {
        if (bands == null || bands.size() > 3) {
            throw new IllegalArgumentException("HT packet needs zero to three bands");
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
                if (block.passes == 1) {
                    header.bit(0);
                } else if (block.passes == 2) {
                    header.bit(1);
                    header.bit(0);
                } else {
                    header.bits(0xC, 4);
                }
                int lengthBits = Math.max(32 - Integer.numberOfLeadingZeros(block.cleanupLength),
                        32 - Integer.numberOfLeadingZeros(block.data.length - block.cleanupLength)
                                - (block.passes == 3 ? 1 : 0));
                int extra = Math.max(0, lengthBits - 3);
                for (int j = 0; j < extra; j++) {
                    header.bit(1);
                }
                header.bit(0);
                header.bits(block.cleanupLength, 3 + extra);
                if (block.passes > 1) {
                    header.bits(block.data.length - block.cleanupLength,
                            3 + extra + (block.passes == 3 ? 1 : 0));
                }
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
        Decoded decoded = decodeNext(packet, 0, packet == null ? 0 : packet.length,
                bandSizes);
        if (decoded.bytesConsumed != packet.length) {
            throw new IIOException("HT packet has trailing bytes");
        }
        return decoded.bands;
    }

    static Decoded decodeNext(byte[] packet, int offset, int end,
            List<int[]> bandSizes) throws IIOException {
        if (packet == null || offset < 0 || end <= offset || end > packet.length
                || bandSizes == null
                || bandSizes.size() > 3) {
            throw new IIOException("Invalid HT packet data or band layout");
        }
        Htj2kPacketBits.Reader header = new Htj2kPacketBits.Reader(
                packet, offset, end - offset);
        boolean present = header.bit() != 0;
        if (present && bandSizes.isEmpty()) {
            throw new IIOException("HT packet has data without a subband");
        }
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
                int passes = readPasses(header);
                int placeholders = (passes - 1) / 3;
                missingMsbs += placeholders;
                passes -= placeholders * 3;
                if (missingMsbs > 29) {
                    throw new IIOException("HT packet missing bitplanes exceed supported precision");
                }
                int extra = 0;
                while (header.bit() != 0) {
                    if (++extra > 13) {
                        throw new IIOException("HT packet Lblock exceeds cleanup limit");
                    }
                }
                int cleanupLength = header.bits(3 + extra
                        + 31 - Integer.numberOfLeadingZeros(placeholders * 3 + 1));
                int refinementLength = passes == 1 ? 0
                        : header.bits(3 + extra + (passes == 3 ? 1 : 0));
                int length = cleanupLength + refinementLength;
                if (cleanupLength < 2 || cleanupLength > 32768
                        || refinementLength >= 2047 || (passes > 1 && refinementLength == 0)
                        || total > end - offset - length) {
                    throw new IIOException("Invalid HT packet cleanup length");
                }
                total += length;
                bandLengths[i] = length;
                blocks.add(new Contribution(missingMsbs, new byte[0], cleanupLength, passes));
            }
            lengths.add(bandLengths);
            result.add(new Band(size[0], size[1], blocks));
        }
        header.align();
        int position = offset + header.bytesRead();
        if (total > end - position) {
            throw new IIOException("HT packet header/body length mismatch");
        }
        List<Band> decoded = new ArrayList<Band>();
        for (int bandIndex = 0; bandIndex < result.size(); bandIndex++) {
            Band band = result.get(bandIndex);
            List<Contribution> blocks = new ArrayList<Contribution>();
            for (int i = 0; i < band.blocks.size(); i++) {
                int length = lengths.get(bandIndex)[i];
                Contribution block = band.blocks.get(i);
                blocks.add(new Contribution(block.missingMsbs,
                        Arrays.copyOfRange(packet, position, position + length),
                        block.cleanupLength, block.passes));
                position += length;
            }
            decoded.add(new Band(band.width, band.height, blocks));
        }
        return new Decoded(Collections.unmodifiableList(decoded), position - offset);
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
        final int cleanupLength;
        final int passes;

        Contribution(int missingMsbs, byte[] data) {
            this(missingMsbs, data, data == null ? 0 : data.length, 1);
        }

        Contribution(int missingMsbs, byte[] data, int cleanupLength, int passes) {
            if (missingMsbs < 0 || missingMsbs > 30 || data == null
                    || passes < 1 || passes > 3 || cleanupLength < 0 || cleanupLength > 32768
                    || (data.length != 0 && (cleanupLength < 2 || cleanupLength > data.length
                    || data.length - cleanupLength >= 2047
                    || (passes == 1 && cleanupLength != data.length)))) {
                throw new IllegalArgumentException("Invalid HT packet contribution");
            }
            this.missingMsbs = missingMsbs;
            this.data = data.clone();
            this.cleanupLength = cleanupLength;
            this.passes = passes;
        }
    }

    private static int readPasses(Htj2kPacketBits.Reader header) throws IIOException {
        if (header.bit() == 0) {
            return 1;
        }
        if (header.bit() == 0) {
            return 2;
        }
        int value = header.bits(2);
        if (value < 3) {
            return 3 + value;
        }
        value = header.bits(5);
        return value < 31 ? 6 + value : 37 + header.bits(7);
    }

    static final class Decoded {
        final List<Band> bands;
        final int bytesConsumed;

        private Decoded(List<Band> bands, int bytesConsumed) {
            this.bands = bands;
            this.bytesConsumed = bytesConsumed;
        }
    }
}
