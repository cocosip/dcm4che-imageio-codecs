# JPEG Baseline Process 1 Implementation Plan

**Goal:** Add a pure-Java 8-bit sequential Huffman JPEG codec for DICOM Baseline Process 1.

**Architecture:** Keep JPEG syntax, bit I/O, Huffman coding, quantization, DCT, and raster conversion inside the JPEG module. Adapt the existing `AbstractDicomImageReader` and `AbstractDicomImageWriter` so dcm4che remains responsible for descriptor attachment and frame encapsulation.

**Tech Stack:** Java 8, Maven, `javax.imageio`, JUnit 5, dcm4che 5.35.1.

## Global Constraints

- Pure Java only; no JNI, JDK-internal JPEG implementation, or external codec dependency.
- First slice supports 8-bit SOF0, sequential Huffman coding, 1 or 3 components, and 1x1 sampling.
- Reject unsupported precision, progressive/arithmetic/lossless JPEG, CMYK/YCCK, 4:2:2, and unsupported ImageIO parameters with `IIOException`.
- Preserve the current core contract: one logical compressed frame per reader/writer invocation.
- Keep design and plan files under ordinary `docs/`; do not add ignored `.superpowers/` paths or commit these documents.

### Task 1: Define JPEG marker and bitstream primitives

**Files:**
- Create: `dcm4che-imageio-codecs-jpeg/src/main/java/io/github/cocosip/dcm4che/imageio/codecs/jpeg/internal/JpegException.java`
- Create: `.../internal/BitReader.java`
- Create: `.../internal/BitWriter.java`
- Create: `.../internal/JpegMarker.java`
- Create: `.../internal/JpegMarkerReader.java`
- Create: `.../internal/JpegMarkerWriter.java`
- Test: `.../src/test/java/.../internal/BitStreamTest.java`
- Test: `.../src/test/java/.../internal/JpegMarkerReaderTest.java`

**Interfaces:** `JpegMarkerReader` consumes an `ImageInputStream` and returns validated marker payloads; `JpegMarkerWriter` writes marker prefixes and big-endian payload lengths; `BitReader`/`BitWriter` implement JPEG MSB-first bits, stuffed `0x00` bytes, and byte alignment.

- [ ] Write tests for bit boundaries, byte stuffing, marker length validation, and truncated input.
- [ ] Run `mvn -pl dcm4che-imageio-codecs-jpeg -am -Dtest=BitStreamTest,JpegMarkerReaderTest test` and verify failure because classes are absent.
- [ ] Implement the smallest primitives with `IIOException` for malformed JPEG input.
- [ ] Re-run the focused tests and verify all pass.

### Task 2: Implement canonical Huffman tables and JPEG tables

**Files:**
- Create: `.../internal/HuffmanTable.java`
- Create: `.../internal/HuffmanCodec.java`
- Create: `.../internal/JpegTables.java`
- Create: `.../internal/QuantizationTable.java`
- Test: `.../src/test/java/.../internal/HuffmanCodecTest.java`
- Test: `.../src/test/java/.../internal/JpegTablesTest.java`

**Interfaces:** `HuffmanTable.fromCodeLengths(...)`, `HuffmanCodec.encodeSymbol(...)`, `HuffmanCodec.decodeSymbol(...)`, `JpegTables.standardLuminance()`, `JpegTables.standardChrominance()`, and quantization lookup in natural/zig-zag order.

- [ ] Test canonical code assignment, JPEG category/amplitude encoding, invalid codes, and standard table contents.
- [ ] Run the focused tests and verify the expected red state.
- [ ] Implement canonical table construction, symbol encode/decode, and validated quantization tables.
- [ ] Run the focused tests and verify all pass.

### Task 3: Implement DCT and baseline MCU codec

**Files:**
- Create: `.../internal/JpegZigZag.java`
- Create: `.../internal/JpegDct.java`
- Create: `.../internal/BaselineJpegCodec.java`
- Create: `.../internal/JpegFrame.java`
- Test: `.../src/test/java/.../internal/JpegDctTest.java`
- Test: `.../src/test/java/.../internal/BaselineJpegCodecTest.java`

**Interfaces:** `BaselineJpegCodec.encode(JpegFrame)` returns one complete JPEG byte array; `BaselineJpegCodec.decode(byte[])` returns decoded component planes plus dimensions. `JpegFrame` carries 8-bit one- or three-component samples and validates 1x1 sampling.

- [ ] Add DCT round-trip tolerance tests and monochrome/RGB component round-trip fixtures.
- [ ] Run the focused tests and verify they fail before implementation.
- [ ] Implement level shift, forward/inverse 8x8 DCT, quantization, zig-zag scan, DC differential coding, AC run-length coding, EOB/ZRL, and SOF0/SOS framing.
- [ ] Implement decoder validation for SOI, SOF0, DQT, DHT, SOS, EOI, restart markers, unsupported sampling, and truncated entropy data.
- [ ] Run the focused tests and verify all pass.

### Task 4: Adapt raster samples and DICOM metadata

**Files:**
- Create: `.../JpegRasterFrames.java`
- Test: `.../src/test/java/.../JpegRasterFramesTest.java`

**Interfaces:** `JpegRasterFrames.fromImage(ImageDescriptor, RenderedImage)` creates a validated `JpegFrame`; `JpegRasterFrames.toImage(ImageDescriptor, JpegFrame, ImageReadParam)` creates/populates the destination image.

- [ ] Test grayscale, RGB interleaving, descriptor dimension checks, and `MONOCHROME1` inversion.
- [ ] Run the focused tests and verify the red state.
- [ ] Implement byte sample extraction through the image raster, descriptor photometric checks, and 8-bit destination population.
- [ ] Reject `YBR_FULL_422`, non-8-bit descriptors, unsupported sample counts, and unsupported read parameters.
- [ ] Run the focused tests and verify all pass.

### Task 5: Add ImageIO reader/writer and SPI registration

**Files:**
- Create: `.../JpegImageReader.java`
- Create: `.../JpegImageWriter.java`
- Create: `.../JpegImageReaderSpi.java`
- Create: `.../JpegImageWriterSpi.java`
- Modify: `dcm4che-imageio-codecs-jpeg/src/main/resources/META-INF/services/javax.imageio.spi.ImageReaderSpi`
- Modify: `dcm4che-imageio-codecs-jpeg/src/main/resources/META-INF/services/javax.imageio.spi.ImageWriterSpi`
- Test: `.../src/test/java/.../JpegImageIoTest.java`

**Interfaces:** Reader and writer extend the existing core abstract classes and use the internal codec; SPI names are `jpeg-ext` / `JPEG-EXT` and must advertise the paired reader/writer classes.

- [ ] Add tests for SPI discovery, descriptor-backed `setInput`/`setOutput`, round-trip, and unsupported parameters.
- [ ] Run the focused tests and verify failure before adapters exist.
- [ ] Implement frame-byte reading to EOF, writer output, and SPI metadata.
- [ ] Run the focused tests and verify all pass.

### Task 6: Add interoperability fixtures and full module verification

**Files:**
- Add: `dcm4che-imageio-codecs-jpeg/src/test/resources/.../baseline-gray.jpg`
- Add: `dcm4che-imageio-codecs-jpeg/src/test/resources/.../baseline-rgb.jpg`
- Modify: `dcm4che-imageio-codecs-jpeg/src/test/java/.../JpegImageIoTest.java`

- [ ] Test decoding checked-in JDK-compatible baseline JPEG fixtures and decoding writer output with the JDK reader, using explicit lossy sample tolerances.
- [ ] Run `mvn -pl dcm4che-imageio-codecs-jpeg -am test`.
- [ ] Run `mvn test` from the repository root and inspect all module results.
- [ ] Run `git diff --check` and inspect `git status --short`; leave implementation changes available for review without committing unless explicitly requested.
