# JPEG Baseline Process 1 Design

**Status:** Approved

## Goal

Implement a pure-Java JPEG Baseline Process 1 codec in
`dcm4che-imageio-codecs-jpeg`, integrated with the existing dcm4che ImageIO SPI
and core descriptor/stream contracts. The first slice supports 8-bit,
sequential, Huffman-coded JPEG for monochrome and three-component images.

## Scope

Supported in this slice:

- JPEG Baseline Process 1, SOF0, 8-bit precision.
- Sequential Huffman entropy coding.
- DQT, DHT, DRI, SOS, SOI, EOI, APPn, and COM markers.
- One compressed DICOM frame per reader/writer invocation, including input
  bytes assembled from multiple dcm4che stream fragments.
- `SamplesPerPixel` 1 or 3.
- `MONOCHROME1`, `MONOCHROME2`, `RGB`, and `YBR_FULL` metadata on the DICOM
  descriptor. The codec emits/consumes interleaved RGB samples for three
  components; `MONOCHROME1` is inverted at the image boundary.
- ImageIO SPI discovery through the existing service-file mechanism.

Explicitly rejected with `IIOException`:

- 12-bit or other non-8-bit JPEG precision.
- SOF1, SOF2, SOF3, arithmetic coding, progressive scans, CMYK/YCCK, and
  unsupported component counts or sampling factors.
- Region/subsampling/band-selection write/read parameters not supported by the
  existing RLE reader contract.
- DICOM `YBR_FULL_422` in this first slice; sampling conversion is deferred to
  the Extended Process work.

Multi-frame orchestration remains owned by dcm4che's compressor/decompressor.
The current project core intentionally exposes one logical frame per ImageIO
reader invocation, so this slice tests fragmented single-frame input rather
than changing that public core contract.

## Architecture

The JPEG module is split into a codec-neutral byte/marker layer and the
ImageIO adapters:

```text
JpegImageReader / JpegImageWriter
        |
BaselineJpegCodec
        +-- JpegMarkerReader/Writer
        +-- BitReader/BitWriter
        +-- HuffmanTable / HuffmanCodec
        +-- QuantizationTable
        +-- DctTransform
        +-- JpegRasterFrames
```

The marker layer validates the input structure and exposes frame metadata to
the baseline decoder. The entropy layer handles byte stuffing and restart
interval state. The DCT layer owns the integer/float arithmetic and zig-zag
ordering. `JpegRasterFrames` converts between the descriptor-defined Java image
sample layout and the codec's component-major MCU input.

The writer uses a deterministic baseline stream: standard luminance/chrominance
tables, 8x8 blocks, four-component-free SOF0, and optional DRI only when a
future write parameter explicitly requests a restart interval. The reader
accepts arbitrary valid baseline DQT/DHT tables and sampling factors only when
all components use 1x1 sampling. This keeps the first decoder interoperable
without introducing unverified 4:2:2 resampling behavior.

## Data Flow

1. `AbstractDicomImageReader` obtains `ImageDescriptor` from the dcm4che
   stream and passes the stream to `JpegImageReader`.
2. The reader consumes the current compressed frame bytes, validates the SOI,
   SOF0, tables, scan, and EOI markers, and decodes the scan into component
   sample buffers.
3. Component samples are interleaved into the destination created by
   `DicomImageTypes`. For `MONOCHROME1`, samples are inverted using 8-bit
   `255 - value`.
4. `JpegImageWriter` converts the descriptor-compatible `RenderedImage` into
   component buffers, applies the same monochrome policy, and writes one
   baseline JPEG frame to the attached `ImageOutputStream`.
5. The dcm4che caller remains responsible for encapsulation, even-length
   padding, transfer-syntax mapping, and persistence of the compressed frame.

## Error Handling

- Stream read/write failures propagate as `IOException`.
- Malformed or unsupported JPEG syntax produces `IIOException` with a marker or
  feature-specific message.
- Truncated entropy data, invalid Huffman codes, invalid quantization values,
  duplicate table definitions, invalid restart markers, and MCU overrun are
  rejected rather than silently padded.
- Image dimensions and descriptor/sample-model mismatches are rejected before
  encoding begins.
- No native library, JDK-internal JPEG implementation, or external codec
  dependency is used.

## Testing Strategy

Tests are organized in the JPEG module:

1. **Unit tests:** marker parsing, byte stuffing, bit-reader/writer boundaries,
   canonical Huffman tables, zig-zag ordering, quantization, and DCT round-trip
   tolerance.
2. **Codec tests:** lossless component-buffer round-trip for monochrome and RGB
   fixtures, deterministic stream markers, and rejection of non-baseline input.
3. **ImageIO tests:** SPI discovery, descriptor-backed reader/writer setup,
   `BufferedImage` round-trip, `MONOCHROME1` inversion, and unsupported
   `ImageReadParam` rejection.
4. **Interoperability tests:** decode a checked-in standard baseline JPEG
   fixture and verify that a writer-produced stream can be decoded by the JDK
   ImageIO JPEG reader. Tests compare decoded samples with an explicit lossy
   tolerance, not byte-for-byte equality.

The first implementation does not claim Process 2/4, Process 14, 4:2:2,
progressive, arithmetic, or multi-frame support. Those capabilities require
separate design and regression gates.

## Compatibility and Extension Points

The public surface is limited to the existing ImageIO SPI classes and their
constructors. Internal codec classes remain package-private where possible.
The marker model, bit I/O, Huffman tables, quantization tables, and descriptor
conversion are deliberately reusable by later JPEG Process 2/4 and Process 14
implementations without changing the core module API.
