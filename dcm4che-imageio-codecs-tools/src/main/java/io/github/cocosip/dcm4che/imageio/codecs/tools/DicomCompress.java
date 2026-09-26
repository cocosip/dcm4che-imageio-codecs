package io.github.cocosip.dcm4che.imageio.codecs.tools;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.imageio.codec.Compressor;
import org.dcm4che3.imageio.codec.Decompressor;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;

import io.github.cocosip.dcm4che.imageio.codecs.core.registry.CodecRegistrations;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg.JpegImageWriter;
import io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.Jpeg2000Codec;
import io.github.cocosip.dcm4che.imageio.codecs.jpegls.JpegLsCodec;
import io.github.cocosip.dcm4che.imageio.codecs.rle.RleCodec;

/** Compresses a DICOM file with the codecs provided by this repository. */
public final class DicomCompress {
    private static final Target[] TARGETS = {
            new Target("rle", "1.2.840.10008.1.2.5"),
            new Target("jpeg_baseline", "1.2.840.10008.1.2.4.50"),
            new Target("jpeg_process2_4", "1.2.840.10008.1.2.4.51"),
            new Target("jpeg_lossless_14", "1.2.840.10008.1.2.4.57"),
            new Target("jpeg_lossless_sv1", "1.2.840.10008.1.2.4.70"),
            new Target("jpegls_lossless", "1.2.840.10008.1.2.4.80"),
            new Target("jpegls_near_lossless", "1.2.840.10008.1.2.4.81"),
            new Target("j2k_lossless", "1.2.840.10008.1.2.4.90"),
            new Target("j2k_lossy", "1.2.840.10008.1.2.4.91"),
            new Target("htj2k_lossless", "1.2.840.10008.1.2.4.201"),
            new Target("htj2k_lossless_rpcl", "1.2.840.10008.1.2.4.202"),
            new Target("htj2k_lossy", "1.2.840.10008.1.2.4.203")
    };

    private DicomCompress() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            Options options = Options.parse(args);
            if (options.help) {
                out.println("Usage: dicom-compress <input.dcm> [-o <output-dir>] [-f <format>]");
                out.println("Formats: " + formatNames());
                return 0;
            }
            if (!Files.isRegularFile(options.input)) {
                throw new IOException("Input DICOM file does not exist: " + options.input);
            }
            registerCodecs();
            Path outputDirectory = options.outputDirectory != null
                    ? options.outputDirectory
                    : options.input.toAbsolutePath().getParent().resolve(
                            baseName(options.input) + "_compressed");
            Files.createDirectories(outputDirectory);
            Target[] selected = options.format == null
                    ? TARGETS : new Target[] {findTarget(options.format)};
            int successes = 0;
            for (Target target : selected) {
                Path output = outputDirectory.resolve(baseName(options.input)
                        + "_" + target.suffix + ".dcm");
                try {
                    compress(options.input, output, target);
                    out.println(target.suffix + ": " + output + " ("
                            + Files.size(output) + " bytes)");
                    successes++;
                } catch (Exception failure) {
                    err.println(target.suffix + ": " + failure.getMessage());
                }
            }
            out.println(successes + "/" + selected.length + " formats saved");
            return successes == 0 ? 1 : 0;
        } catch (Exception failure) {
            err.println(failure.getMessage());
            return 2;
        }
    }

    private static void registerCodecs() throws IOException {
        RleCodec.register();
        CodecRegistrations.load(JpegImageWriter.class,
                "readers.properties", "writers.properties");
        JpegLsCodec.register();
        Jpeg2000Codec.register();
    }

    private static void compress(Path input, Path output, Target target) throws IOException {
        Path temporary = Files.createTempFile(output.getParent(), ".dicom-compress-", ".dcm");
        Path uncompressed = null;
        try {
            String sourceSyntax;
            int sourceFrames;
            Attributes dataset;
            try (DicomInputStream stream = new DicomInputStream(input.toFile())) {
                stream.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
                dataset = stream.readDataset();
                Attributes fmi = stream.getFileMetaInformation();
                sourceSyntax = fmi == null ? UID.ImplicitVRLittleEndian
                        : fmi.getString(Tag.TransferSyntaxUID, UID.ImplicitVRLittleEndian);
                sourceFrames = dataset.getInt(Tag.NumberOfFrames, 1);
            }
            if (sourceSyntax.equals(target.uid)) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            } else {
                if (isEncapsulated(sourceSyntax)) {
                    if (!new Decompressor(dataset, sourceSyntax).decompress()) {
                        throw new IOException("No decoder for source transfer syntax " + sourceSyntax);
                    }
                    uncompressed = Files.createTempFile(output.getParent(),
                            ".dicom-uncompressed-", ".dcm");
                    try (DicomOutputStream stream = new DicomOutputStream(uncompressed.toFile())) {
                        stream.writeDataset(dataset.createFileMetaInformation(
                                UID.ExplicitVRLittleEndian), dataset);
                    }
                    try (DicomInputStream stream = new DicomInputStream(uncompressed.toFile())) {
                        stream.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
                        dataset = stream.readDataset();
                    }
                    sourceSyntax = UID.ExplicitVRLittleEndian;
                }
                try (Compressor compressor = new Compressor(dataset, sourceSyntax)) {
                    if (!compressor.compress(target.uid)) {
                        throw new IOException("No encoder for transfer syntax " + target.uid);
                    }
                    try (DicomOutputStream stream = new DicomOutputStream(temporary.toFile())) {
                        stream.writeDataset(dataset.createFileMetaInformation(target.uid), dataset);
                    }
                }
            }
            try (DicomInputStream stream = new DicomInputStream(temporary.toFile())) {
                Attributes saved = stream.readDataset();
                Attributes fmi = stream.getFileMetaInformation();
                if (fmi == null || !target.uid.equals(fmi.getString(Tag.TransferSyntaxUID))
                        || saved.getInt(Tag.NumberOfFrames, 1) != sourceFrames) {
                    throw new IOException("Saved transfer syntax or frame count differs");
                }
            }
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
            if (uncompressed != null) {
                Files.deleteIfExists(uncompressed);
            }
        }
    }

    private static boolean isEncapsulated(String uid) {
        return uid.equals("1.2.840.10008.1.2.5")
                || uid.startsWith("1.2.840.10008.1.2.4.");
    }

    private static Target findTarget(String name) {
        for (Target target : TARGETS) {
            if (target.suffix.equalsIgnoreCase(name)) {
                return target;
            }
        }
        throw new IllegalArgumentException("Unknown format: " + name);
    }

    private static String baseName(Path path) {
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot <= 0 ? filename : filename.substring(0, dot);
    }

    private static String formatNames() {
        StringBuilder names = new StringBuilder();
        for (Target target : TARGETS) {
            if (names.length() != 0) {
                names.append(", ");
            }
            names.append(target.suffix);
        }
        return names.toString();
    }

    private static final class Target {
        final String suffix;
        final String uid;

        Target(String suffix, String uid) {
            this.suffix = suffix;
            this.uid = uid;
        }
    }

    private static final class Options {
        final Path input;
        final Path outputDirectory;
        final String format;
        final boolean help;

        Options(Path input, Path outputDirectory, String format, boolean help) {
            this.input = input;
            this.outputDirectory = outputDirectory;
            this.format = format;
            this.help = help;
        }

        static Options parse(String[] args) {
            if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
                return new Options(null, null, null, true);
            }
            Path input = null;
            Path output = null;
            String format = null;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--output-dir".equals(arg) || "-o".equals(arg)) {
                    if (++i == args.length) throw new IllegalArgumentException("Missing output directory");
                    output = Paths.get(args[i]);
                } else if ("--format".equals(arg) || "-f".equals(arg)) {
                    if (++i == args.length) throw new IllegalArgumentException("Missing format");
                    format = args[i];
                } else if (arg.startsWith("-") || input != null) {
                    throw new IllegalArgumentException("Unexpected argument: " + arg);
                } else {
                    input = Paths.get(arg);
                }
            }
            if (input == null) throw new IllegalArgumentException("Missing input DICOM file");
            return new Options(input, output, format, false);
        }
    }
}
