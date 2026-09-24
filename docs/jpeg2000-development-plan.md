# JPEG 2000 Codec Design

## 1. Purpose

This document defines the pure-Java design for the classic JPEG 2000 DICOM
transfer syntaxes in `dcm4che-imageio-codecs-jpeg2000`. It is the design
authority for the first implementation phase. HTJ2K is specified separately in
[`htj2k-development-plan.md`](htj2k-development-plan.md); it may reuse the
codestream and DICOM boundary infrastructure described here, but it must not
reuse the classic entropy-coding entry point.

The implementation is Java-only. It must not use JNI, P/Invoke, C/C++ source,
OpenJPEG/OpenJPH binaries, native fallback, or an existing native ImageIO
plugin. The native `fo-dicom.Codecs` package and the managed
`fo-dicom.PureCodecs` package are behavioral and algorithm references only.

## 2. Scope

| Transfer syntax | UID | Transform | Entropy path | Default mode |
| --- | --- | --- | --- | --- |
| JPEG 2000 Lossless | `1.2.840.10008.1.2.4.90` | reversible 5/3 | EBCOT with MQ | lossless |
| JPEG 2000 Lossy | `1.2.840.10008.1.2.4.91` | reversible 5/3 or irreversible 9/7 | EBCOT with MQ | irreversible |

The phase consumes and produces raw JPEG 2000 codestreams (`J2K`) inside DICOM
encapsulated frames. JP2 file-format boxes, JPIP/JPT, JPEG 2000 Part 2
multi-component extensions (`.92/.93`), component subsampling, and arbitrary
file-format metadata are outside this module and must be rejected with a
managed `IIOException`.

## 3. Reference Boundary

The public behavior baseline is the `DicomJpeg2000Codec` adapter in
`fo-dicom.Codecs`, with algorithm and DICOM layout details cross-checked against
`fo-dicom.PureCodecs`. OpenJPEG source may be read to understand codestream
semantics and rate allocation, but no native source or binary may be copied,
compiled, linked, loaded, or executed by this project.

Acceptance is based on decoded pixels and valid codestream semantics, not on
the Java encoder reproducing OpenJPEG bytes. When byte comparison is useful,
compare the logical codestream from SOC through EOC and exclude DICOM's
even-length padding byte.

## 4. ImageIO and DICOM Contract

### 4.1 SPI surface

The module uses syntax-bound SPI registrations. A writer cannot infer the target
Transfer Syntax from `ImageDescriptor` or a generic `ImageWriteParam`, so one
generic writer pair is not sufficient.

- `.90`: `Jpeg2000LosslessImageReaderSpi` / `Jpeg2000LosslessImageWriterSpi`
- `.91`: `Jpeg2000LossyImageReaderSpi` / `Jpeg2000LossyImageWriterSpi`

The reader implementations may delegate to the same internal decoder, and the
writer implementations may delegate to the same classic encoder with a fixed
syntax policy. dcm4che registration and writer lookup use only exact,
syntax-specific format names such as `jpeg2000-lossless` and
`jpeg2000-lossy`. A generic `jpeg2000` writer alias is forbidden because it
cannot distinguish `.90` from `.91`. Readers may share implementation details,
but registration still uses the exact format name. The service files under
`src/main/resources/META-INF/services` are enabled only after the
syntax-specific tests pass.

### 4.2 Frame boundary

`setInput`/`setOutput` requires an `ImageInputStream`/`ImageOutputStream` that
exposes an `ImageDescriptor`. A codec invocation handles exactly one logical
frame. Multi-frame orchestration and DICOM fragment iteration remain in
dcm4che. The reader drains one frame, validates SOC/SIZ/EOC boundaries, and
never treats bytes after EOC as codestream data. The writer emits one raw J2K
codestream and leaves fragment encapsulation to dcm4che.

`ImageDescriptor` is a read-only description of the source image. The codec
must not attempt to change its photometric interpretation, planar
configuration, or any other dataset field. dcm4che `Compressor` owns the DICOM
metadata update for the destination Transfer Syntax. A direct ImageIO caller
that bypasses `Compressor` has the same responsibility.

The adapter validates:

- positive rows/columns and checked Java array sizes;
- `SamplesPerPixel` equal to 1 or 3;
- `BitsAllocated` equal to 8 or 16 and `1 <= BitsStored <= BitsAllocated`;
- valid planar/interleaved layout and exact photometric-specific uncompressed
  frame length, including subsampled `YBR_FULL_422` layout;
- supported MONOCHROME, RGB, Palette Color, and the explicitly documented YBR
  variants;
- Palette Color samples are treated as indexed grayscale codes; applying the
  DICOM palette LUT is a rendering concern outside this codec module;
- signed samples represented by the SIZ component signedness flag;
- sign extension at the raster/DICOM boundary; MONOCHROME1 sample codes are
  preserved and display inversion remains a rendering concern;
- no implicit embedded-overlay masking. Overlay masking is outside this codec
  contract unless a later descriptor-level feature and fixture set is approved.

### 4.3 Pixel and color policy

The frame adapter first converts planar RGB to interleaved samples. `YBR_FULL`
and `YBR_FULL_422` are normalized to RGB before the multi-component transform.
The reference-compatible encode path accepts these YBR conversions only for
8-bit allocated samples; `YBR_PARTIAL_422` and `YBR_PARTIAL_420` are rejected
on encode. Decode compatibility for `YBR_PARTIAL_422` is explicit and produces
RGB raster output. For three components, the public DICOM writer path uses:

- reversible color transform (RCT) for lossless output;
- irreversible color transform (ICT) for irreversible lossy output.

This is required because dcm4che `Compressor` maps `.90` to `YBR_RCT` and `.91`
to `YBR_ICT`; the COD MCT state must agree with the metadata written by the
host. An internal low-level no-MCT encoder may exist for standards testing, but
it must not be exposed by the normal syntax writer unless an integration layer
can update the dataset consistently. On decode, the COD marker determines
whether MCT was actually present. The codec returns the reconstructed raster;
it does not mutate the source descriptor or destination dataset metadata.

The same integration constraint applies to reversible `.91`: fo-dicom can set
`YBR_RCT` when `irreversible=false`, but dcm4che `Compressor` selects `YBR_ICT`
from the `.91` Transfer Syntax. The normal three-component `.91` writer must
therefore reject `irreversible=false`. Reversible `.91` remains available for
one-component data and for a low-level integration that can write matching
`YBR_RCT` metadata.

## 5. Parameters

`Jpeg2000ImageWriteParam` extends `ImageWriteParam` and keeps the ImageIO API
small while exposing the behavior needed by the reference adapter:

| Parameter | Meaning | Validation |
| --- | --- | --- |
| `irreversible` | select `.91` reversible 5/3 or irreversible 9/7 coding | `.90` always reversible; `.91` defaults to `true` |
| `rate` | compression-ratio/rate-control factor used for layer truncation | finite, non-negative |
| `rateLevels` | descending compression-ratio layer targets | strictly descending and bounded |
| `progressionOrder` | LRCP, RLCP, RPCL, PCRL, or CPRL | reject unknown values |
| `numLayers` | number of derived layers in `targetRatio` mode | `1..65535`; ignored outside that mode |
| `includeFinalLosslessLayer` | append one zero-rate layer after the derived `targetRatio` layers | required for `.90`; forbidden for `.91` |
| `targetRatio` | PureCodecs/Java extension that derives layer targets | unset/zero, or finite and greater than `1` |
| `encodeSignedAsUnsigned` | preserve signed bit pattern as unsigned codestream samples | explicit opt-in |

The reference-compatible defaults are `Rate=20`, descending rate levels
`1280, 640, 320, 160, 80, 40, 20, 10, 5`, three-component MCT enabled, and
signed values preserved. `.90` always uses reversible coding. `.91` uses
irreversible coding by default, but an explicit `irreversible=false` selects
reversible 5/3 coding while retaining the `.91` Transfer Syntax. Therefore
`rate=0` does not make the default `.91` path lossless, but `.91` with reversible
coding and a complete zero-rate layer can reconstruct exactly.

Rate/layer selection follows this precedence and is resolved before encoding:

| Priority | Mode | Layer construction |
| --- | --- | --- |
| 1 | Explicit `targetRatio > 1` | This mode wins over `rate`/`rateLevels`. For each of `numLayers` derived layers at index `i`, use `targetRatio * 2^(numLayers-i-1)`. `.90` requires `includeFinalLosslessLayer=true` and appends one zero-rate layer; `.91` requires it to be false. Derived plus final layers must not exceed 65535. |
| 2 | Native-compatible `rate` mode | Start with `Rate=20`. Append configured `rateLevels` greater than `rate`, then append `rate * BitsStored / BitsAllocated`, including zero. For `.90` with a positive terminal rate, append a final zero-rate lossless layer. |
| 3 | Generic ImageIO quality | Map quality through one documented deterministic function only when neither explicit JPEG 2000 mode is set. |

`ImageWriteParam.MODE_DISABLED` is rejected because JPEG 2000 is always
compressed. Final-layer validation is resolved before any coefficient work.

## 6. Architecture

The module is organized into narrow internal layers. The exact class names may
change during implementation, but each responsibility must remain isolated.

### 6.1 DICOM/ImageIO adapters

- syntax-bound reader/writer adapters: stream lifecycle and checked
  `IIOException` conversion, delegating to shared classic frame codecs.
- `Jpeg2000ImageWriteParam`: `.91` transform selection, progression, rate, and
  layer controls. Public three-component MCT is fixed by the integration policy.
- `Jpeg2000RasterFrames`: conversion between `BufferedImage`, descriptor sample
  order, signed/unsigned 8/16-bit values, and planar/interleaved layouts.

### 6.2 Package and reuse boundary

Classic JPEG 2000 and HTJ2K remain in one Maven module but use three package
areas. Sharing is based on identical state and semantics, not similar class
names:

| Package | Owns | Must not own |
| --- | --- | --- |
| `jpeg2000.common` | marker framing and bounded byte I/O; immutable geometry, coordinate, and value records; identical SIZ/COD/COC/QCD/QCC/SOT/SOD/EOC/COM/TLM syntax; raster normalization; identical RCT/ICT and DWT mathematical primitives; stateless progression-coordinate iteration | mutable packet contribution state, mutable tag trees, entropy-family policy, CAP/Rsiz profile decisions |
| `jpeg2000.classic` | classic quantization/profile defaults, EBCOT/MQ, PCRD allocation, mutable classic packet contributions, inclusion/zero-bit-plane tag trees, and classic marker policy | HT cleanup/refinement state or CAP policy |
| `jpeg2000.htj2k` | CAP/Rsiz/profile policy, HT quantization defaults, MEL/VLC/MagSgn, cleanup/refinement, mutable HT packet contribution state, and HT tile-part scheduling | classic MQ/EBCOT/PCRD state |

The same DWT or component-transform arithmetic may be called through
family-specific policy objects. Mutable packet and tag-tree state is never
shared between the classic and HT entry points.

### 6.3 Classic marker and tile profile

`Jpeg2000CodestreamReader` / `Jpeg2000CodestreamWriter` provide framing and
length/bounds checks. Marker support is split by direction:

| Classic writer marker | Initial policy |
| --- | --- |
| SOC, SIZ, COD, QCD, COM, SOT, SOD, EOC | Emit a deterministic baseline codestream. |
| COC, QCC | Emit only when a later supported component override requires them. |
| POC, RGN, PPM/PPT, PLM/PLT, SOP/EPH, TLM | Do not emit initially. |

| Classic reader marker or feature | Release policy |
| --- | --- |
| SOC, SIZ, COD/COC, QCD/QCC, SOT/SOD/EOC | Required semantic support. |
| POC, Maxshift RGN, PPM/PPT, SOP/EPH | Support to the extent covered by the PureCodecs behavior and committed interoperability fixtures. |
| TLM, PLM, PLT | If accepted, parse and validate lengths, indexes, and bounds; never silently ignore them. |
| COM and an explicitly enumerated advisory marker | May be skipped after its declared length is validated. |
| Unknown marker with possible decoding semantics | Reject with `IIOException`; do not guess that it is advisory. |

`RESET` and `VSC` are code-block style flags carried by COD/COC, not marker
segments. They are validated and implemented as coding-style behavior.

The initial encoder writes one full-image tile, matching the reference default.
The release decoder accepts multiple tiles and ordered tile-parts and validates
`Isot`, `TPsot`, `TNsot`, and `Psot` completeness and ordering. This broader
decode target is deliberate Native/OpenJPEG parity; it does not imply that the
initial encoder can create arbitrary tiling.

### 6.4 Classic transform and quantization

- `Jpeg2000Dwt53`: reversible lifting steps with integer arithmetic and exact
  inverse reconstruction.
- `Jpeg2000Dwt97`: irreversible lifting steps with controlled fixed-point
  arithmetic and documented rounding.
- `Jpeg2000ComponentTransform`: RCT/ICT forward and inverse transforms.
- `Jpeg2000Quantizer`: reversible bypass and irreversible subband quantization,
  QCD/QCC exponent/mantissa encoding, and guard-bit validation.
- `Jpeg2000TileModel`: tile boundaries, resolution levels, precincts and code
  blocks, with no implicit global-image assumptions.

The classic encoder uses the reference profile of five decomposition levels,
64x64 code blocks, and the documented reversible/irreversible quantization
defaults. These are profile constants in the first implementation, not hidden
algorithm choices.

### 6.5 Classic entropy and packets

- `Jpeg2000MqCoder` / `Jpeg2000MqDecoder`: JPEG 2000 context state machine,
  byte stuffing, termination, and bounded refill/flush behavior.
- `Jpeg2000EbcotEncoder` / `Jpeg2000EbcotDecoder`: significance propagation,
  magnitude refinement, cleanup passes, pass truncation, and code-block state.
- `Jpeg2000PacketEncoder` / `Jpeg2000PacketDecoder`: tag trees, inclusion and
  zero-bit-plane coding, packet headers, quality-layer contribution, and
  progression traversal.
- `Jpeg2000RateAllocator`: distortion-length slope calculation and PCRD-style
  layer truncation. The logical `Psot` excludes a DICOM padding byte.

Classic and HTJ2K may share immutable geometry and stateless progression
coordinates, but not mutable packet/tag-tree state. The EBCOT/MQ classes must
never dispatch to HTJ2K block coding based on a runtime flag.

## 7. Encode and Decode Flow

### Encode

1. Read the descriptor and normalize one frame to interleaved sample codes,
   retaining signedness and the DICOM little-endian sample representation.
2. Validate dimensions, precision, photometric interpretation, rate/layer
   parameters, and the selected transfer syntax.
3. Level-shift unsigned component samples, apply the required RCT/ICT for a
   three-component public DICOM encode, then apply the selected 5/3 or 9/7 DWT.
4. Quantize subbands, partition tiles/precincts/code-blocks, and run EBCOT/MQ.
5. Allocate quality layers and write a raw J2K codestream with bounded marker
   lengths and a terminating EOC.
6. Write the logical codestream to the ImageIO output stream; dcm4che owns
   encapsulation and even-length padding.

### Decode

1. Read a bounded frame and reject a JP2 signature or bytes before SOC.
2. Parse SIZ and coding/quantization markers before allocating image buffers.
   Validate SIZ width, height, component count, precision, signedness, supported
   component sampling, and checked output size against `ImageDescriptor`.
3. Decode tile-parts and packets, reconstruct code-blocks with EBCOT/MQ, and
   reverse quantization and the selected DWT.
4. Apply inverse RCT/ICT only when COD declares MCT, undo the component level
   shift/sample representation, then repack samples to the descriptor's planar
   layout, signed representation, and image type.
5. Apply ImageReadParam source region/subsampling through the raster boundary;
   do not alter codestream geometry while decoding.

## 8. Errors and Resource Limits

All malformed input, descriptor/codestream mismatches, and unsupported
combinations become `IIOException` with a
cause that identifies operation, frame, marker, tile, component, and packet
context where available. The implementation must reject integer overflow before
allocation, cap marker lengths and code-block dimensions, and ensure every
reader loop consumes input or fails. Partial output is discarded on failure.

## 9. Verification Matrix

The implementation is accepted only after all applicable layers pass:

1. Unit tests for marker parsing/writing, bit stuffing, MQ state transitions,
   lifting transforms, quantization, tag trees, packet progression, rate-layer
   truncation, signed samples, planar conversion, and malformed streams.
2. Codec tests for exact `.90` round trips across 8/12/16-bit allocated samples,
   monochrome/RGB/Palette Color, planar/interleaved input, odd dimensions, tile
   boundaries, multiple tiles/tile-parts on decode, and multi-frame caller
   orchestration.
3. `.91` tests for its default irreversible path and explicit reversible path,
   deterministic output, finite error bounds, rate-mode precedence, quality
   layer ordering, COD MCT agreement with dcm4che metadata policy, and
   `ImageReadParam` region/subsampling.
4. External acceptance tests using standard J2K fixtures and codestreams
   produced by fo-dicom.Codecs/OpenJPEG in a separate process. Lossless pixels
   must match exactly; lossy assertions use fixed, recorded tolerances.
5. DICOM integration tests proving raw-frame extraction, fragment spanning,
   exact syntax-specific SPI lookup, host-owned metadata updates, and that
   DICOM padding is excluded before SOC-through-EOC parsing.

Self-round-trip and compressed size are not sufficient evidence of correctness.
Every release claim requires at least one foreign-decoder or foreign-encoder
pixel check for `.90` and `.91`.

The codec also has bounded resource limits before allocation: maximum frame and
codestream bytes, sample count, tile/precinct/code-block count, packet count,
marker payload length, and quality-layer count. Limits are checked with checked
integer arithmetic and produce `IIOException` with operation and marker context.

## 10. Implementation Order

1. Add marker/bitstream models and parser/writer with malformed-input tests.
2. Implement reversible geometry, 5/3 DWT, and lossless EBCOT/MQ for grayscale.
3. Add RGB/RCT, signed samples, planar conversion, and ImageIO adapters.
4. Add packet progression, tile/precinct/tag-tree handling, and external decode.
5. Add 9/7, quantization, rate allocation, `.91`, and lossy acceptance tests.
6. Enable SPI service entries and update the module description only after the
   focused and external matrices pass.

HTJ2K work starts only after the classic `.90/.91` structural and DICOM gates
are stable. See [`htj2k-development-plan.md`](htj2k-development-plan.md).

## 11. Non-goals

- Native libraries, JNI, C/C++ translation, or runtime native selection.
- JP2 file boxes, JPIP/JPT, Part 2 multi-component transforms, and arbitrary
  component subsampling.
- Silent recovery from malformed codestreams or implicit frame reconstruction.
