# dcm4che-imageio-codecs Design Document

> This document records the background research, architecture decisions, and open questions
> for this project. No implementation code has been written yet. This document is the
> baseline for all subsequent development discussions.
>
> The primary algorithm and design reference is **fo-dicom.PureCodecs**, a pure C#
> implementation of the same codec set targeting fo-dicom. Study its per-codec design
> documents before implementing any codec family (see References §11).

---

## 1. Project Background

### Target Reference

This project aims to be the Java/dcm4che equivalent of **fo-dicom.Codecs** — an external
image codec plugin library that provides JPEG, JPEG-LS, JPEG 2000, and related transfer
syntax support without modifying the host library's core source code.

#### fo-dicom.Codecs (native reference)

[fo-dicom.Codecs](https://github.com/Efferent-Health/fo-dicom.Codecs) is the official
production codec package for fo-dicom. It wraps native C/C++ libraries via P/Invoke:

- **JPEG** — libjpeg-turbo (baseline / extended / lossless)
- **JPEG-LS** — CharLS
- **JPEG 2000 / HTJ2K** — OpenJPEG

It defines fo-dicom's codec contract (`IDicomCodec` / `ITranscoderManager`) and registers
codecs via fo-dicom's dependency injection mechanism. The set of supported transfer
syntaxes in fo-dicom.Codecs defines the Phase 1 scope for this project.

#### fo-dicom.PureCodecs (algorithm reference)

**[fo-dicom.PureCodecs](https://github.com/cocosip/fo-dicom.PureCodecs)** is a pure C#
replacement for fo-dicom.Codecs — same transfer
syntax coverage, same fo-dicom interface, but implemented entirely in managed code with no
native dependency. It serves as the primary algorithm and design reference for this project
because:

- All algorithms (RLE PackBits, JPEG DCT/predictive, JPEG-LS Golomb, JPEG 2000 DWT/EBCOT,
  HTJ2K HT block coding) are implemented in a readable, high-level language with detailed
  inline design documents.
- Per-codec DICOM edge cases (photometric interpretation, planar layout, signed pixels,
  multi-fragment frames, etc.) are explicitly handled and documented.
- Validation rules and error handling requirements are defined per codec family.
- It proves that a pure managed-language implementation of all these codecs is viable.

| | fo-dicom.Codecs / fo-dicom.PureCodecs | dcm4che-imageio-codecs (this project) |
|---|---|---|
| Host library | fo-dicom (C#/.NET) | dcm4che (Java) |
| Interface model | fo-dicom's own `IDicomCodec` / `ITranscoderManager` | Java standard `javax.imageio` SPI |
| Registration | DI container, `AddTranscoderManager<PureTranscoderManager>()` | `ImageReaderFactory.getDefault().load(...)` + IIORegistry SPI |
| DICOM parameter passing | Directly via `IDicomCodec.Encode(DicomDataset, ...)` | Side-channel via `SegmentedInputImageStream.getImageDescriptor()` |
| Implementation language | Pure C# (no native dependency) | Pure Java (no JNI, no native dependency) |
| Maven groupId | — | `io.github.cocosip` |
| Java package root | — | `io.github.cocosip.dcm4che.imageio.codecs` |
| Phase 1 codec scope | 12 transfer syntaxes (see Section 2) | Same 12 + RLE (13 total, all in this project) |

### Why This Project Is Needed

- dcm4che historically relied on `jai-imageio-jpeg2000` for JPEG 2000 support, which is
  unmaintained and has known bugs. `dcm4chee-arc-light` even disabled JPEG 2000 transfer
  syntaxes by default to avoid decoder problems.
- Since 5.31.2, dcm4che uses `weasis-core-img` (OpenCV JNI wrapper) which is better but:
  - Requires glibc (no Alpine/musl without compatibility layer).
  - Requires Java 17+ for the native module.
  - HTJ2K encoding depends on upstream OpenJPEG progress.
  - The native library brings a large transitive dependency.
- This project provides an independently maintainable, pure Java codec layer that can be
  dropped into any dcm4che deployment without native library requirements.

---

## 2. Phase 1 Transfer Syntax Scope

Scope is aligned with fo-dicom.PureCodecs Phase 1 — the same set of transfer syntaxes
that `fo-dicom.Codecs` supports (excluding features its README marks as "in development").

### Included in Phase 1

| Codec family | Transfer syntax | UID | Encode | Decode |
|---|---|---|---|---|
| RLE | RLE Lossless | `1.2.840.10008.1.2.5` | Required | Required |
| JPEG | JPEG Baseline Process 1 | `1.2.840.10008.1.2.4.50` | Required | Required |
| JPEG | JPEG Extended Process 2/4 | `1.2.840.10008.1.2.4.51` | Required | Required |
| JPEG | JPEG Lossless Process 14 | `1.2.840.10008.1.2.4.57` | Required | Required |
| JPEG | JPEG Lossless Process 14 SV1 | `1.2.840.10008.1.2.4.70` | Required | Required |
| JPEG-LS | JPEG-LS Lossless | `1.2.840.10008.1.2.4.80` | Required | Required |
| JPEG-LS | JPEG-LS Near-Lossless | `1.2.840.10008.1.2.4.81` | Required | Required |
| JPEG 2000 | JPEG 2000 Lossless | `1.2.840.10008.1.2.4.90` | Required | Required |
| JPEG 2000 | JPEG 2000 Lossy | `1.2.840.10008.1.2.4.91` | Required | Required |
| JPEG 2000 / HTJ2K | HTJ2K Lossless | `1.2.840.10008.1.2.4.201` | Required | Required |
| JPEG 2000 / HTJ2K | HTJ2K Lossless RPCL | `1.2.840.10008.1.2.4.202` | Required | Required |
| JPEG 2000 / HTJ2K | HTJ2K Lossy | `1.2.840.10008.1.2.4.203` | Required | Required |

### Excluded from Phase 1

| Transfer syntax | UID | Reason |
|---|---|---|
| JPEG XL Lossless | `1.2.840.10008.1.2.4.110` | Marked "in development" in fo-dicom.Codecs; excluded from fo-dicom.PureCodecs Phase 1 |
| JPEG XL Near-Lossless | `1.2.840.10008.1.2.4.111` | Same |
| JPEG XL Lossy | `1.2.840.10008.1.2.4.112` | Same |
| JPEG 2000 Part 2 Multi-Component | `1.2.840.10008.1.2.4.92 / .93` | Rare in practice; out of scope |
| JPIP Referenced / JPIP HTJ2K | `.95 / .204 / .205` | JPIP is a streaming protocol, not a standalone codec |

### Note on RLE

dcm4che already ships `org.dcm4che3.imageio.plugins.rle.RLEImageReader` /
`RLEImageWriter` as a pure Java, built-in implementation. This project **re-implements
RLE independently** to own the complete codec set and to use the first (simplest) codec as
a proof-of-concept for the module layout and frame-handling infrastructure.

---

## 3. dcm4che Image Codec Architecture

### 3.1 Overall Data Flow

```
DICOM file / network stream
        ↓
  Transcoder / Decompressor / Compressor
        ↓  (looks up Transfer Syntax UID in mapping table)
  ImageReaderFactory / ImageWriterFactory
        ↓  (resolves implementation via IIORegistry or ServiceLoader)
  javax.imageio.ImageReader / ImageWriter   ← extension point for this project
        ↓
  Decoded pixel data (BufferedImage)
```

### 3.2 Mapping Table (ImageReaderFactory)

`ImageReaderFactory` maintains `TreeMap<String, ImageReaderParam>`:
- Key: Transfer Syntax UID string
- Value: `ImageReaderParam { formatName, className, patchJPEGLS, imageReadParams }`

Default properties file format (one entry per line):
```
<UID>:<formatName>:<ImageReaderClassName>::
```

Built-in defaults that this project overrides (replacing dcm4che's weasis-core-img path):
```properties
1.2.840.10008.1.2.4.50:jpeg-cv:org.dcm4che3.opencv.NativeImageReader::
1.2.840.10008.1.2.4.80:jpeg-ls-cv:org.dcm4che3.opencv.NativeImageReader::
1.2.840.10008.1.2.4.90:jpeg2000-cv:org.dcm4che3.opencv.NativeImageReader::
1.2.840.10008.1.2.4.201:jpeg2000-cv:org.dcm4che3.opencv.NativeImageReader::
1.2.840.10008.1.2.5:rle:org.dcm4che3.imageio.plugins.rle.RLEImageReader::
```

### 3.3 How DICOM Parameters Are Passed to a Codec

**This is the critical implicit contract. There is no formal DICOM codec interface.**

DICOM pixel parameters are passed as a side-channel attached to the `ImageInputStream`:

```
Decompressor constructor:
  new ImageDescriptor(dataset)              ← built from DICOM Attributes
        ↓ set on stream
  siis.setImageDescriptor(imageDescriptor)
        ↓ custom ImageReader.read() retrieves it
  StreamSegment seg = StreamSegment.getStreamSegment(iis, param);
  ImageDescriptor desc = seg.getImageDescriptor();
  // use desc.isSigned(), getPhotometricInterpretation(), getBitsStored() etc.
```

#### ImageDescriptor fields

| Field | DICOM Tag | Notes |
|---|---|---|
| rows / columns | Rows / Columns | Image dimensions |
| samples | SamplesPerPixel | 1=mono, 3=RGB/YBR |
| bitsAllocated | BitsAllocated | Usually 8 or 16 |
| bitsStored | BitsStored | Actual significant bits |
| pixelRepresentation | PixelRepresentation | 0=unsigned, 1=signed |
| photometricInterpretation | PhotometricInterpretation | MONOCHROME2 / RGB / YBR_FULL / YBR_FULL_422 / YBR_RCT / YBR_ICT etc. |
| planarConfiguration | PlanarConfiguration | 0=interleaved, 1=banded (planar) |
| frames | NumberOfFrames | Multi-frame count |
| embeddedOverlays | OverlayData tags | Groups embedded in pixel high bits |

A custom `ImageReader` **must** retrieve `ImageDescriptor` from the input stream.
Without it, none of the DICOM-specific pixel semantics are available through the
standard `javax.imageio.ImageReader` interface.

### 3.4 Input Stream Types

| Type | When used |
|---|---|
| `SegmentedInputImageStream` | Main path — encapsulated pixel data fragments + Basic Offset Table |
| `BytesWithImageImageDescriptor` | In-memory byte array path |
| `PatchJPEGLSImageInputStream` | Additional wrapper for JPEG-LS streams requiring patching |

Custom Readers must handle both `SegmentedInputImageStream` and
`BytesWithImageImageDescriptor`. Use `StreamSegment.getStreamSegment(iis, param)` from
`dcm4che-imageio-opencv` as a unified adapter, or implement the instanceof-check adaptation
manually in the `core` module to avoid an opencv dependency.

### 3.5 Reference Implementations in dcm4che

| Implementation | Class | Notes |
|---|---|---|
| RLE | `org.dcm4che3.imageio.plugins.rle.RLEImageReader/Writer` | Pure Java, no native dependency — simplest reference |
| All compressed | `org.dcm4che3.opencv.NativeImageReader/Writer` | JNI via weasis-core-img (OpenCV) |

---

## 4. External Codec Registration

### Approach: Programmatic Incremental Load (adopted)

```java
// Execute once at application startup
ImageReaderFactory.getDefault().load("classpath:dcm4che-imageio-codecs-readers.properties");
ImageWriterFactory.getDefault().load("classpath:dcm4che-imageio-codecs-writers.properties");
```

- **Behavior**: appends/overwrites only the specified entries. All other default mappings
  remain untouched.
- The alternative (system property override) replaces the entire default map and requires
  maintaining a full copy of dcm4che's built-in entries — not adopted.

### SPI Auto-Discovery

Declare `ImageReaderSpi` / `ImageWriterSpi` implementations in the jar's service files.
`IIORegistry` auto-registers them at startup:

```
META-INF/services/javax.imageio.spi.ImageReaderSpi  → one FQN per line
META-INF/services/javax.imageio.spi.ImageWriterSpi  → one FQN per line
```

`ImageReaderFactory` resolves the implementation via
`ImageIO.getImageReadersByFormatName(formatName)`.

---

## 5. weasis-core-img Relationship

- `weasis-core-img` is a standalone project by Nicolas Roduit, originally for the Weasis
  viewer. dcm4che depends on it as a Maven artifact (`org.weasis.core:weasis-core-img`).
- It is a generic OpenCV JNI wrapper, not a DICOM codec interface — dcm4che bridges the
  DICOM semantics in `NativeImageReader`.
- **This project does NOT depend on weasis-core-img.** All codecs are implemented in pure
  Java with no native library dependency.

---

## 6. Per-Codec-Family Design Reference

This section summarizes the key design points and DICOM edge cases for each codec family,
derived from fo-dicom.PureCodecs design documents (see References §11). Before beginning
implementation of any family, read the corresponding design document in that project.

### 6.1 RLE Lossless

**Complexity**: Low. Pure byte manipulation, no transform. Proves module layout and
frame-handling infrastructure before harder codecs.

Key design points:
- Each compressed frame has a **64-byte header**: 4-byte segment count + 15 × 4-byte
  segment offsets.
- Segment count = `BitsAllocated / 8 × SamplesPerPixel`. Maximum 15 segments; reject if
  more are required.
- Encoding: PackBits-style literal runs (control byte 0–127) and repeat runs (control byte
  −1 to −127). Control byte −128 is a no-op and must not be emitted.
- **Byte order**: most-significant byte first within each sample (segment-major order).
- Must handle both **interleaved** (PlanarConfiguration=0) and **planar** (PlanarConfiguration=1)
  output layouts.
- Validation (decode): frame shorter than 64 bytes; segment count < 1 or > 15; offsets
  outside frame bounds or non-monotonically increasing; runs exceeding output frame size.

**dcm4che built-in**: `RLEImageReader`/`RLEImageWriter` exists in dcm4che (pure Java).
This project re-implements RLE independently for complete ownership of the codec set.

### 6.2 JPEG Family

**Complexity**: Medium. Standard JPEG algorithm (ISO/IEC 10918-1). Well-documented.

Transfer syntaxes:
- JPEG Baseline Process 1 (`.50`): 8-bit samples, sequential DCT, Huffman, lossy.
- JPEG Extended Process 2/4 (`.51`): 8-bit and 12-bit, sequential DCT, Huffman, lossy.
- JPEG Lossless Process 14 (`.57`): predictive coding, Huffman, 8/12/16-bit.
- JPEG Lossless Process 14 SV1 (`.70`): same as above, predictor fixed to 1.

Key design points:
- Marker support required: SOI, EOI, SOF0, SOF1, SOF3, DHT, DQT, DRI, SOS, APPn (skip),
  COM (skip), RST0–RST7 (restart intervals).
- **DRI/RST restart intervals**: decoder must validate modulo-8 order, reset Huffman and
  predictor state at each interval, and reject missing/out-of-sequence markers.
- Photometric interpretations: `MONOCHROME1/2`, `RGB`, `YBR_FULL`, `YBR_FULL_422`.
  For Process 1 and 2/4, default behavior in fo-dicom converts to RGB on decode.
- Lossless Process 14 honors `predictor` parameter (1–7); Process 14 SV1 fixes predictor=1.
- Process 2/4 handles 12-bit in 16-bit DICOM containers (SF444 interleave);
  12-bit SF422 encoding is excluded from Phase 1.
- Progressive, arithmetic-coded, CMYK/YCCK JPEG are excluded from Phase 1.

**dcm4che status**: dcm4che maps `.50/.51/.57/.70` to `NativeImageReader` via opencv.
This project replaces that mapping.

#### JPEG implementation status in this repository

The current `dcm4che-imageio-codecs-jpeg` module implements the four DICOM Phase 1 JPEG
transfer syntaxes. Some raw progressive/arithmetic/differential classes may remain as
internal experiments, but they are not public DICOM capabilities. The following matrix
is the authoritative status for the code currently in the repository:

| Capability | Status | Details |
|---|---|---|
| Baseline transfer syntax `.50` (`1.2.840.10008.1.2.4.50`) | Implemented | Pure Java encoder/decoder and dcm4che reader/writer properties are present. |
| 8-bit precision | Implemented | SOF0 only; samples are stored as unsigned 8-bit values. |
| Sequential Huffman coding | Implemented | DCT, quantization, zig-zag, DC differential coding, AC run-length coding, byte stuffing, DQT/DHT/SOS/EOI handling. |
| Grayscale | Implemented | `SamplesPerPixel=1`, `MONOCHROME1` and `MONOCHROME2`; `MONOCHROME1` is inverted at the image boundary. |
| Three-component RGB | Implemented | `SamplesPerPixel=3` with interleaved 1x1 component sampling. |
| `YBR_FULL` metadata | Implemented | Lossy RGB input is converted to YCbCr at the image boundary; YBR samples remain in YBR order for DICOM output. |
| `YBR_FULL_422` | Implemented | Sequential Huffman paths emit and consume 4:2:2 sampling with shared chroma reconstruction. |
| ImageIO SPI | Implemented | Reader/writer SPI service entries and the `jpeg-ext` format name are registered. |
| Descriptor-backed dcm4che streams | Implemented | Uses the existing `ImageDescriptor` stream contract and reads/writes one logical frame per invocation. |
| JDK interoperability | Verified for covered subset | JDK-generated baseline grayscale JPEG can be decoded; Baseline and 8-bit Extended writer output can be decoded by the JDK JPEG reader. |
| JPEG Extended Process 2/4 `.51` | Implemented | Pure Java SOF1 sequential Huffman path supports unsigned 8/12-bit SF444 samples, with dedicated ImageIO SPI and descriptor-backed 16-bit container handling. |
| JPEG Lossless Process 14 `.57` | Implemented | Pure Java SOF3 predictive Huffman coding supports unsigned 8/12/16-bit monochrome/RGB, predictors 1-7 on decode, predictor 1 encoding, DRI/RST validation, and ImageIO point-transform control. |
| JPEG Lossless Process 14 SV1 `.70` | Implemented | Dedicated ImageIO adapters enforce the fixed predictor-1 Process 14 SV1 contract; DRI/RST and ImageIO point-transform control are supported. |
| Progressive JPEG | Not part of the public DICOM codec | Raw SOF2 support is outside the current scope and is not mapped to a DICOM transfer syntax. |
| Arithmetic-coded JPEG | Not part of the public DICOM codec | Arithmetic JPEG is outside the current scope and is not registered as a DICOM codec. |
| Differential/Hierarchical JPEG | Not implemented | Retired hierarchical transfer syntaxes are deliberately not supported or registered. |
| Restart intervals (`DRI`/`RST`) | Implemented for sequential/lossless Huffman paths | DCT and lossless paths expose or consume restart intervals and validate marker order. |
| CMYK/YCCK JPEG | Not implemented | dcm4che has no public CMYK/YCCK photometric or pixel-data contract; this project deliberately does not add one. |
| ImageReadParam regions/subsampling/band selection | Implemented | Source region, subsampling, destination offset, and source/destination band selection are applied by the reader. |
| Writer quality/compression parameters | Implemented for lossy paths | Baseline and Extended writers map ImageIO compression quality to quantization; lossless uses point transform and restart controls instead of lossy quality. |
| Multi-frame orchestration | Not implemented in this module | The current core exposes one logical frame per ImageIO invocation; dcm4che remains responsible for frame orchestration. |

The implemented public DICOM subset is therefore limited to Baseline `.50` 8-bit,
Extended `.51` unsigned 8/12-bit monochrome/RGB, Lossless `.57` unsigned 8/12/16-bit
monochrome/RGB, and `.70` SV1 through dedicated reader/writer adapters. Progressive,
arithmetic, differential/hierarchical, and CMYK/YCCK JPEG are deliberately outside the
current public codec scope; see `docs/jpeg-complete-development-plan.md` for the active
JPEG work list.

### 6.3 JPEG-LS Family

**Complexity**: Medium. ISO 14495-1. More specialized than baseline JPEG but well-defined.

Transfer syntaxes:
- JPEG-LS Lossless (`.80`): `AllowedError = 0`.
- JPEG-LS Near-Lossless (`.81`): `AllowedError = N` (N > 0), per-sample tolerance check.

Key design points:
- Marker support: SOI, EOI, SOF55, SOS, LSE (preset coding parameters), DRI/RST (2-, 3-,
  and 4-byte forms), APPn/COM (skip). APP8 `mrfx` HP1/HP2/HP3 color-transform metadata
  must be parsed for decoder-side inverse transform compatibility.
- **Encoding modes**: regular mode (Golomb coding + context model) and run mode.
- **Interleave modes**: None (monochrome), Line, Sample — must map to DICOM PlanarConfiguration.
- Planar `YBR_FULL_422` is explicitly rejected. Interleaved `YBR_FULL_422` normalized to RGB
  before encode.
- Near-lossless round-trip: verify `abs(original − decoded) ≤ AllowedError` per sample.
- **Parameters**: `AllowedError`, `InterleaveMode`, `ColorTransform`.

**dcm4che status**: `.80/.81` mapped to `NativeImageReader` via opencv. This project
replaces that mapping.

### 6.4 JPEG 2000 / HTJ2K Family

**Complexity**: High (JPEG 2000) and Very High (HTJ2K). Most complex codec family.

Transfer syntaxes:
- JPEG 2000 Lossless (`.90`): classic JPEG 2000, reversible 5/3 DWT, lossless.
- JPEG 2000 Lossy (`.91`): irreversible 9/7 DWT, EBCOT/MQ entropy coding, lossy.
- HTJ2K Lossless (`.201`): JPEG 2000 Part 15, HT block coding, lossless.
- HTJ2K Lossless RPCL (`.202`): same as `.201` but RPCL progression order enforced.
- HTJ2K Lossy (`.203`): HTJ2K with irreversible path.

Key design points:
- **Accepted input**: raw J2K codestreams only. JP2-wrapped frames (with JP2 file format
  signature) must be detected and rejected.
- **Excluded**: JPEG 2000 Part 2 multi-component (`.92/.93`), JPIP, JPT, component
  subsampling — all fail with managed exceptions.
- JPEG 2000 classic pipeline: validate/normalize input → level shift → optional RCT/ICT →
  reversible/irreversible DWT → quantization (lossy) → tile/precinct/code-block partitioning →
  EBCOT/MQ entropy coding → packet/marker writing.
- HTJ2K pipeline: same outer structure but uses Part 15 MEL/VLC/MagSgn HT block coding
  instead of EBCOT/MQ. Classic and HTJ2K **must maintain separate entry points** even
  when sharing structural infrastructure.
- **Photometric handling**:
  - `YBR_FULL` and `YBR_FULL_422` → normalize to RGB before MCT.
  - Lossless: output photometric → `YBR_RCT` after encode.
  - Lossy: output photometric → `YBR_ICT` after encode.
  - Decoder: obtain MCT state from COD marker; after three-component MCT decode,
    write interleaved RGB metadata.
- **Parameters**: `Irreversible`, `Rate`, `RateLevels`, `AllowMCT`,
  `UpdatePhotometricInterpretation`, `EncodeSignedPixelValuesAsUnsigned`,
  progression order (LRCP/RLCP/RPCL/PCRL/CPRL).
- Supported markers: SIZ, COD, COC, QCD, QCC, SOT, SOD, EOC, COM, POC, RGN, PPM/PPT, SOP/EPH.
- HTJ2K rejects unsupported RGN and PPM/PPT semantics.

**dcm4che status**: `.90/.91/.201/.202/.203` mapped to `NativeImageReader` via
opencv/OpenJPEG. This is the area with the most historical dcm4che issues. This project
replaces those mappings.

---

## 7. Known Technical Issues and Risks

| Issue | Details |
|---|---|
| `StreamSegment` reflection | `StreamSegment` uses reflection to access private fields of `FileImageInputStream`. Causes `InaccessibleObjectException` on Java 9+ without `--add-opens java.desktop/javax.imageio.stream=ALL-UNNAMED`. Plan to fix in dcm4che v6. |
| Multi-fragment frame decoding | PS3.5 §8.2 allows one frame's encoded data to span multiple fragments. Fixed in dcm4che 5.35.0 (#1598). Custom Readers must handle this correctly. |
| PhotometricInterpretation compatibility | dcm4che early JPEG 2000 encoding set `PhotometricInterpretation=YBR_RCT` incorrectly. Custom codec must set the correct value. Also verify `YBR_ICT`/`YBR_FULL_422` handling. |
| Signed pixel handling | For `BitsAllocated=16` / `BitsStored<16` / `PixelRepresentation=1`, decoded data must be sign-extended correctly. `desc.isSigned()` must gate this path. |
| Embedded overlays | `ImageDescriptor.embeddedOverlays` — some legacy devices encode overlay data in pixel high bits. Mask off high bits for affected images. |
| Even-length frame rule | DICOM encapsulated frames must have even-length byte buffers. Pad with zero if necessary. This is easy to forget and causes downstream parse failures. |
| Java 9+ module system | `--add-opens` workaround for `StreamSegment` reflection is an imperfect solution. Prefer `BytesWithImageImageDescriptor` input paths where possible to avoid the reflective `FileImageInputStream.raf` access entirely. |

---

## 8. Project Module Structure

**Decision**: Split by codec family. Each family is a separate Maven module. This mirrors
fo-dicom.PureCodecs' assembly structure and allows each family to carry independent
dependencies without forcing them on users who only need a subset.

### Module layout

```
dcm4che-imageio-codecs/                     ← parent POM (packaging=pom)
├── dcm4che-imageio-codecs-core/            ← factory registration helpers, ImageDescriptor adapter,
│                                           │  shared frame utilities, photometric conversion utils
├── dcm4che-imageio-codecs-rle/             ← RLE Lossless (1.2.840.10008.1.2.5)
├── dcm4che-imageio-codecs-jpeg/            ← JPEG family:
│                                           │  · Baseline Process 1        (1.2.840.10008.1.2.4.50)
│                                           │  · Extended Process 2/4      (1.2.840.10008.1.2.4.51)
│                                           │  · Lossless Process 14       (1.2.840.10008.1.2.4.57)
│                                           │  · Lossless Process 14 SV1  (1.2.840.10008.1.2.4.70)
├── dcm4che-imageio-codecs-jpegls/          ← JPEG-LS family:
│                                           │  · Lossless                  (1.2.840.10008.1.2.4.80)
│                                           │  · Near-Lossless             (1.2.840.10008.1.2.4.81)
└── dcm4che-imageio-codecs-jpeg2000/        ← JPEG 2000 + HTJ2K family:
                                            ·  JPEG 2000 Lossless         (1.2.840.10008.1.2.4.90)
                                            ·  JPEG 2000 Lossy            (1.2.840.10008.1.2.4.91)
                                            ·  HTJ2K Lossless             (1.2.840.10008.1.2.4.201)
                                            ·  HTJ2K Lossless RPCL        (1.2.840.10008.1.2.4.202)
                                            ·  HTJ2K Lossy                (1.2.840.10008.1.2.4.203)
```

### Module dependency rules

```
dcm4che-imageio-codecs-core       ← depends on dcm4che-imageio, dcm4che-core
dcm4che-imageio-codecs-rle        ← depends on core only
dcm4che-imageio-codecs-jpeg       ← depends on core only
dcm4che-imageio-codecs-jpegls     ← depends on core only
dcm4che-imageio-codecs-jpeg2000   ← depends on core only
```

Codec modules must NOT depend on each other. Shared logic (e.g., photometric conversion,
color space utilities) belongs in `core`.

### Development order

Simplest families first, infrastructure proven early, hardest (JPEG 2000 / HTJ2K) last:

1. `core` module — factory registration helpers, ImageDescriptor adapter, base SPI stubs.
2. `rle` module — RLE Lossless (proves the full encode/decode pipeline end-to-end).
3. `jpeg` module — Process 1 → 2/4 → 14 → 14 SV1.
4. `jpegls` module — Lossless → Near-Lossless.
5. `jpeg2000` module — JPEG 2000 Lossless → Lossy → HTJ2K Lossless → RPCL → Lossy.
6. Integration tests, consumer smoke tests, packaging.

---

## 9. Core Module Design (`dcm4che-imageio-codecs-core`)

The `core` module provides the shared infrastructure that all codec modules build on.
Its public API must be stable before any codec module is written.

### 9.1 Package Structure

```
io.github.cocosip.dcm4che.imageio.codecs
├── core/
│   ├── spi/         AbstractDicomImageReader, AbstractDicomImageWriter, DicomImageReaderSpi
│   ├── stream/      DicomStreamAdapter (ImageDescriptor extraction)
│   ├── color/       PhotometricConverter
│   └── util/        FrameAssembler, DicomCodecUtils
└── CodecRegistrar   (entry point: loads all properties files)
```

### 9.2 DicomStreamAdapter

**Responsibility**: Extract `ImageDescriptor` from an `ImageInputStream` regardless of
which concrete stream type dcm4che has passed in.

dcm4che passes two possible stream types to a codec:
- `SegmentedInputImageStream` — encapsulated pixel data; carries `ImageDescriptor` directly
- `BytesWithImageImageDescriptor` — in-memory path; also carries `ImageDescriptor`

Both types expose `getImageDescriptor()` but share no common interface. The adapter
performs the `instanceof` check in one place so codec modules never need to.

**API contract**:
```
DicomStreamAdapter.getImageDescriptor(ImageInputStream stream) → ImageDescriptor
    throws IllegalArgumentException if stream type is unrecognised
```

**Note on `StreamSegment`**: dcm4che-imageio-opencv provides `StreamSegment.getStreamSegment()`
as an alternative adapter, but it uses reflection to access private fields of
`FileImageInputStream`, which breaks on Java 9+ without `--add-opens`. The core adapter
must implement the `instanceof`-based approach directly to avoid this dependency and
the reflection problem.

### 9.3 AbstractDicomImageReader

**Responsibility**: Base class for all Reader implementations. Handles all
`javax.imageio.ImageReader` boilerplate that is identical for every DICOM codec, so
subclasses only implement the actual decoding logic.

**Boilerplate handled by the base class**:

| Method | Implementation |
|---|---|
| `getNumImages(boolean)` | Returns `ImageDescriptor.frames` |
| `getWidth(int)` | Returns `ImageDescriptor.columns` |
| `getHeight(int)` | Returns `ImageDescriptor.rows` |
| `getRawImageType(int)` | Derived from `ImageDescriptor` (bits, samples) |
| `getImageTypes(int)` | Single-element iterator over `getRawImageType` |
| `read(int, ImageReadParam)` | Extracts `ImageDescriptor` via `DicomStreamAdapter`, then delegates to `readDicomFrame` |
| `setInput(...)` | Stores stream reference |
| `reset()` | Clears stream reference |

**Abstract method subclasses must implement**:
```
readDicomFrame(ImageDescriptor descriptor, ImageInputStream stream, int frameIndex)
    → BufferedImage
    throws IOException
```

The subclass receives `ImageDescriptor` directly — it never needs to touch the stream
casting or `StreamSegment` machinery.

**Design note**: The base class does NOT suppress `IOException` or wrap it. Codecs must
throw `IOException` for stream errors and `IIOException` (a subclass) for codec-level
failures (malformed bitstream, unsupported feature, etc.).

### 9.4 AbstractDicomImageWriter

**Responsibility**: Mirror of `AbstractDicomImageReader` for encoding.

**Boilerplate handled by the base class**:

| Method | Implementation |
|---|---|
| `getNumThumbnailsSupported(...)` | Returns 0 |
| `write(IIOMetadata, IIOImage, ImageWriteParam)` | Extracts dimensions and delegates to `writeDicomFrame` |
| `setOutput(...)` | Stores stream reference |
| `reset()` | Clears stream reference |

**Abstract method subclasses must implement**:
```
writeDicomFrame(ImageDescriptor descriptor, BufferedImage image,
                ImageOutputStream stream, ImageWriteParam param)
    → void
    throws IOException
```

**Note on ImageDescriptor for writes**: During encoding, `ImageDescriptor` is provided by
dcm4che's `Compressor` the same way as for reads — attached to the output stream.
The base class extracts it via `DicomStreamAdapter` before calling `writeDicomFrame`.

### 9.5 DicomImageReaderSpi (and DicomImageWriterSpi)

**Responsibility**: Base `ImageReaderSpi` / `ImageWriterSpi` with the common boilerplate
filled in. Each codec module subclasses this to provide only codec-specific metadata
(format name, MIME type, description).

**Boilerplate provided by base**:
- `canDecodeInput(Object source)` — returns `false` (dcm4che never calls this; codec
  selection goes through `ImageReaderFactory`, not `ImageIO.getImageReaders()`)
- `createReaderInstance(Object extension)` — delegates to `newReader()` abstract method
- Standard string arrays for vendor name, version, etc.

**Abstract method subclasses provide**:
```
newReader(ImageReaderSpi originatingSpi) → AbstractDicomImageReader
```

### 9.6 PhotometricConverter

**Responsibility**: All photometric interpretation conversions shared across codec families.
No codec module should implement color space conversion independently.

**Conversions required**:

| From | To | When |
|---|---|---|
| `MONOCHROME1` | `MONOCHROME2` | Decode: invert pixel values |
| `YBR_FULL` | `RGB` | Decode: JPEG/JPEG-LS YCbCr → RGB |
| `YBR_FULL_422` | `RGB` | Decode: 4:2:2 chroma upsample then YCbCr → RGB |
| `YBR_RCT` | `RGB` | Decode: JPEG 2000 reversible color transform inverse |
| `YBR_ICT` | `RGB` | Decode: JPEG 2000 irreversible color transform inverse |
| `RGB` | `YBR_FULL` | Encode: RGB → YCbCr (for codecs that require it) |
| `RGB` | `YBR_RCT` | Encode: JPEG 2000 lossless color transform |
| `RGB` | `YBR_ICT` | Encode: JPEG 2000 lossy color transform |

**Design note**: Conversions must operate on raw `byte[]` / `short[]` pixel buffers at
the precision of the source (`BitsStored`). Do not use `java.awt.color.ColorSpace` — it
converts through float and loses precision for 12/16-bit DICOM images.

### 9.7 FrameAssembler

**Responsibility**: Reassemble a complete frame from one or more pixel data fragments.

DICOM PS3.5 §8.2 allows one frame to span multiple Basic Offset Table fragments.
`SegmentedInputImageStream` in dcm4che 5.35.x handles multi-fragment frames correctly
(fixed in #1598), but the `FrameAssembler` utility provides a tested, isolated helper for
assembling fragment byte arrays into a single contiguous `byte[]` ready for the codec.

**API contract**:
```
FrameAssembler.assemble(List<byte[]> fragments) → byte[]
    // concatenates fragments, pads to even length if needed (DICOM even-length rule)
```

### 9.8 CodecRegistrar

**Responsibility**: Convenience entry point for application code to register all codecs
in one call.

```
CodecRegistrar.registerAll()
    // Loads dcm4che-imageio-codecs-readers.properties and
    //        dcm4che-imageio-codecs-writers.properties
    // into ImageReaderFactory.getDefault() and ImageWriterFactory.getDefault()
```

Applications that want finer control can load the per-family properties files directly.
`CodecRegistrar` is a shortcut for the common case.

### 9.9 What core does NOT contain

- Any codec algorithm logic (RLE, DCT, DWT, etc.) — those belong in the codec modules.
- DICOM file parsing — dcm4che's own `Attributes` / `DicomInputStream` handles this.
- Transfer Syntax UID constants — dcm4che's `UID` class already defines all standard UIDs.

---

## 10. Open Questions (Minor)

### 9.1 Whether to define a higher-level DICOM codec abstraction (internal only)

dcm4che has no equivalent to fo-dicom's `IDicomCodec`. Should this project add its own
internal interface above `javax.imageio` SPI that directly exposes `ImageDescriptor`?

- **Pro**: Eliminates the "cast the stream to get DICOM params" anti-pattern in every
  codec; makes the contract explicit and compile-time safe.
- **Con**: dcm4che's `Transcoder`/`Decompressor` still routes through the javax.imageio
  path anyway; the abstraction only helps within this project's own codebase.

### 9.2 Classic JPEG: implement from scratch or delegate 8-bit path to JDK?

The JDK's built-in `javax.imageio` JPEG reader covers 8-bit sequential DCT (Process 1/2/4
in typical cases). It does NOT support 12-bit or lossless predictive (Process 14/14 SV1).
Options:

- **Full pure Java from scratch** (as fo-dicom.PureCodecs did) — consistent code path,
  full control, no reliance on JDK JPEG internals.
- **Delegate 8-bit baseline to JDK, implement only 12-bit and lossless paths** — less code,
  but two different code paths; JDK JPEG reader has its own quirks around color space handling.

**Recommendation**: implement from scratch for consistency, mirroring fo-dicom.PureCodecs.

---

## 11. Project State

### File structure

```
dcm4che-imageio-codecs/
├── pom.xml                                               # Parent POM, groupId=io.github.cocosip, ${revision}
├── LICENSE                                               # Apache License 2.0
├── .gitignore
├── docs/
│   └── design.md                                         # This file
├── dcm4che-imageio-codecs-core/
│   └── src/main/resources/
│       ├── dcm4che-imageio-codecs-readers.properties     # Aggregated UID→Reader mapping (all families)
│       └── dcm4che-imageio-codecs-writers.properties     # Aggregated UID→Writer mapping (all families)
├── dcm4che-imageio-codecs-rle/
│   └── src/main/resources/META-INF/services/            # SPI auto-discovery for RLE
├── dcm4che-imageio-codecs-jpeg/
│   └── src/main/resources/META-INF/services/            # SPI auto-discovery for JPEG
├── dcm4che-imageio-codecs-jpegls/
│   └── src/main/resources/META-INF/services/            # SPI auto-discovery for JPEG-LS
└── dcm4che-imageio-codecs-jpeg2000/
    └── src/main/resources/META-INF/services/            # SPI auto-discovery for JPEG 2000 + HTJ2K
```

### Status

- Architecture research complete.
- Transfer syntax scope confirmed (12 syntaxes + RLE = 13 total across 5 modules).
- Registration mechanism decided (programmatic incremental load).
- Multi-module Maven structure created (parent + core + rle + jpeg + jpegls + jpeg2000).
- Implementation language decided: **pure Java, no JNI**.
- License: Apache 2.0.
- Version management: `${revision}` in parent POM + `flatten-maven-plugin`.
- Development order decided (core → rle → jpeg → jpegls → jpeg2000).
- Core, RLE, and the JPEG module's Baseline Process 1, Extended Process 2/4,
  Lossless Process 14, and Lossless Process 14 SV1 slices are implemented;
- The JPEG status matrix in Section 6.2 is the source of truth: Baseline
  Process 1 `.50`, Extended Process 2/4 `.51`, Lossless Process 14 `.57`, and
  Lossless Process 14 SV1 `.70` are registered.
- JPEG-LS and JPEG 2000/HTJ2K modules remain scaffolds without codec code.
- Two minor open questions remain (see Section 9).

---

## 12. References

### dcm4che

- [dcm4che GitHub](https://github.com/dcm4che/dcm4che)
- [ImageReaderFactory.java](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-imageio/src/main/java/org/dcm4che3/imageio/codec/ImageReaderFactory.java)
- [ImageReaderFactory.properties](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-imageio/src/main/resources/org/dcm4che3/imageio/codec/ImageReaderFactory.properties)
- [NativeImageReader.java](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-imageio-opencv/src/main/java/org/dcm4che3/opencv/NativeImageReader.java)
- [StreamSegment.java](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-imageio-opencv/src/main/java/org/dcm4che3/opencv/StreamSegment.java)
- [Decompressor.java](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-imageio/src/main/java/org/dcm4che3/imageio/codec/Decompressor.java)
- [weasis-core-img](https://github.com/nroduit/weasis-core-img)
- [dcm4chee-arc-light JPEG 2000 issue #1072](https://github.com/dcm4che/dcm4chee-arc-light/issues/1072)
- [dcm4che multi-fragment frame fix #1598](https://github.com/dcm4che/dcm4che/issues/1598)

### Reference Projects

- **[fo-dicom.Codecs](https://github.com/Efferent-Health/fo-dicom.Codecs)** — official
  native codec package for fo-dicom. Wraps CharLS (JPEG-LS), OpenJPEG (JPEG 2000/HTJ2K),
  and libjpeg-turbo (JPEG) via P/Invoke. Defines the Phase 1 transfer syntax scope and
  the `IDicomCodec` / `ITranscoderManager` interface contract.

- **[fo-dicom.PureCodecs](https://github.com/cocosip/fo-dicom.PureCodecs)** — pure C#
  replacement for fo-dicom.Codecs; the primary algorithm reference for this project.
  Design documents:
  - `docs/design/fo-dicom-pure-codecs-design.md` — overall architecture and Phase 1 scope
  - `docs/design/codec-entry-design.md` — entry layer / registration
  - `docs/design/rle-codec-design.md` — RLE algorithm detail
  - `docs/design/jpeg-codec-design.md` — JPEG family design
  - `docs/design/jpegls-codec-design.md` — JPEG-LS design
  - `docs/design/jpeg2000-codec-design.md` — JPEG 2000 + HTJ2K design

### Standards

- ISO/IEC 10918 — JPEG
- ISO/IEC 14495-1 — JPEG-LS
- ISO/IEC 15444-1 — JPEG 2000 (classic)
- ISO/IEC 15444-15 — HTJ2K (High Throughput JPEG 2000)
- DICOM PS3.5 — Data Structures and Encoding (Transfer Syntaxes, Pixel Data encapsulation)
