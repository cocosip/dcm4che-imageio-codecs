# HTJ2K Codec Design

## 1. Purpose

This document defines the second implementation phase for High Throughput JPEG
2000 (HTJ2K) in `dcm4che-imageio-codecs-jpeg2000`. It follows the classic JPEG
2000 design in [`jpeg2000-development-plan.md`](jpeg2000-development-plan.md)
and reuses only its DICOM boundary and validated codestream infrastructure.

HTJ2K is a pure-Java implementation of ISO/IEC 15444-15. It must not use JNI,
P/Invoke, C/C++ source, OpenJPEG/OpenJPH binaries, native fallback, or an
existing native ImageIO plugin. `fo-dicom.Codecs` and `fo-dicom.PureCodecs`
define the behavioral and algorithmic reference boundary, not production
dependencies.

## 2. Scope

| Transfer syntax | UID | Transform | Block coding | Progression policy |
| --- | --- | --- | --- | --- |
| HTJ2K Lossless | `1.2.840.10008.1.2.4.201` | reversible 5/3 | Part 15 HT | fixed/default RPCL |
| HTJ2K Lossless RPCL | `1.2.840.10008.1.2.4.202` | reversible 5/3 | Part 15 HT | caller-selectable, default RPCL |
| HTJ2K Lossy | `1.2.840.10008.1.2.4.203` | irreversible 9/7 | Part 15 HT | fixed/default RPCL |

Frames are raw J2K codestreams carrying HT code-blocks. JP2 boxes, JPIP/JPT,
Part 2 multi-component extensions, component subsampling, and unsupported HT
profile features are rejected with a managed `IIOException`.

## 3. Relationship to Classic JPEG 2000

The following are shared after they have passed the classic implementation's
tests:

- read-only `ImageDescriptor` validation and raster normalization;
- ImageIO reader/writer lifecycle and exact syntax-specific SPI lookup;
- marker framing and bounded byte I/O for identical
  SOC/SIZ/COD/COC/QCD/QCC/SOT/SOD/COM/TLM/EOC syntax;
- immutable tile/component/resolution/precinct geometry and coordinate records;
- identical RCT/ICT and DWT mathematical primitives behind family-specific
  policy objects;
- stateless progression-coordinate iteration where the traversal is identical;
- checked error context and logical SOC-through-EOC frame boundaries.

HT-specific CAP capability markers, `Rsiz`/profile flags, and HT tile-part
accounting remain in the HTJ2K package even though their byte I/O primitives are
shared. The compatibility writer emits CAP and TLM; the reader validates them
when present and accepts a legal foreign codestream that omits optional TLM data.

The following remain independent classes, state, and entry points:

- HT reversible/irreversible transform and quantization policy;
- HT code-block segment layout;
- MEL, VLC, MagSgn, cleanup and refinement coding;
- HT packet contribution and header state, progression restrictions, and rate
  behavior;
- mutable inclusion/zero-bit-plane tag-tree state;
- CAP/Rsiz/profile policy and HT quantization/profile defaults.

The encoder must select a concrete `Htj2kFrameCodec` at the transfer-syntax
adapter boundary. A boolean such as `useHtj2k` inside the classic EBCOT coder is
not permitted because it obscures profile-specific state and makes regressions
cross-contaminate the two families.

## 4. ImageIO and DICOM Contract

### 4.1 SPI surface

Register syntax-bound reader/writer pairs so ImageIO selection cannot lose the
target Transfer Syntax:

- `.201`: `Htj2kLosslessImageReaderSpi` / `Htj2kLosslessImageWriterSpi`
- `.202`: `Htj2kLosslessRpclImageReaderSpi` / `Htj2kLosslessRpclImageWriterSpi`
- `.203`: `Htj2kLossyImageReaderSpi` / `Htj2kLossyImageWriterSpi`

They extend the core abstract DICOM reader/writer and use the same descriptor
side-channel as JPEG, JPEG-LS, and classic JPEG 2000. The SPI service files are
enabled only when the focused HTJ2K reader/writer tests pass.

Registration uses exact names for `.201`, `.202`, and `.203`; no generic
`jpeg2000` or `htj2k` writer alias may select among these syntaxes implicitly.

### 4.2 Frame and pixel rules

The adapter processes one logical frame and validates rows, columns, samples,
bits allocated/stored, signedness, photometric-specific frame length, planar
configuration, and supported photometric interpretation before invoking the HT
coder. Input
`YBR_FULL` and `YBR_FULL_422` is normalized to interleaved RGB only for 8-bit
allocated samples; planar `YBR_FULL_422` input is rejected. Palette Color is
accepted as one-component palette-index sample codes; the codec does not apply
the palette LUT. Partial YBR variants are not part of the initial HT encode
contract. MONOCHROME1 sample codes are preserved; display inversion is outside
the codec.
Three-component HT output always uses the reference HT MCT so the host can
declare `YBR_RCT` for lossless or `YBR_ICT` for lossy output. There is no
public `employColorTransform` toggle in the reference-compatible profile.

`ImageDescriptor` is immutable. The codec never changes photometric
interpretation or planar configuration. dcm4che `Compressor`, or a direct
ImageIO caller that bypasses it, owns destination dataset metadata. The public
three-component writers must emit COD MCT state that agrees with dcm4che's
`YBR_RCT` mapping for `.201/.202` and `YBR_ICT` mapping for `.203`.

On decode, MCT state comes from the COD marker rather than from the requested
transfer syntax alone. After inverse MCT the codec returns an interleaved RGB
raster but still does not mutate the descriptor. Signed pixels retain their low
`BitsStored` two's-complement pattern and are sign-extended only while
constructing the Java destination image. The initial Java profile writes
`BitsStored` as codestream precision and preserves the SIZ signedness flag;
this deliberate divergence from the Native HT adapter's `BitsAllocated`
precision follows the PureCodecs/other codec-family contract and requires
external 12-bit-in-16-bit interoperability tests before release.

## 5. HT Parameters

`Htj2kImageWriteParam` exposes the parameters that have a meaningful ImageIO
mapping:

| Parameter | Meaning | Validation |
| --- | --- | --- |
| `progressionOrder` | LRCP, RLCP, RPCL, PCRL, or CPRL | meaningful only for `.202`; default RPCL |
| `targetRatio` | PureCodecs/Java `.203` quality hint | `0` or finite and greater than `1` |
| `numLayers` | quality layers | exactly `1` until HT packet-layer contributions are implemented |

The compatibility profile fixes five decomposition levels, 64x64 code blocks,
single quality layer, resolution-based tile-part division, mandatory TLM output,
CAP output, and reference three-component MCT behavior. The transform is fixed
by Transfer Syntax: `.201/.202` use reversible 5/3 and `.203` uses irreversible
9/7. Inherited controls such as `irreversible`, `numberOfDecompositions`,
`employColorTransform`, and `insertTlmMarkers` are not meaningful public HT
parameters.

`.201` and `.203` ignore a caller progression value and use the OpenJPH-compatible
RPCL default. `.202` accepts all five progression orders and defaults to RPCL;
PureCodecs explicitly exercises CPRL. The implementation rejects
`numLayers != 1` in the compatibility profile.

`targetRatio` is a quality hint, not a guaranteed compressed-size ratio. For
`targetRatio > 1`, use the deterministic PureCodecs mapping:

```text
tolerance = targetRatio > 1 ? max(1, ceil(targetRatio - 1)) : 0
quality = clamp(96 - 4 * max(1, tolerance), 30, 95)
```

`targetRatio=0` selects the OpenJPH-compatible default irreversible quantization
step policy (`-1.0`), not a request for exact lossless size or ratio.

## 6. HTJ2K Architecture

### 6.1 Shared structure, HT-specific policy

Reuse the classic parser's marker model only for syntax that is identical. The
HT parser must additionally validate Part 15 profile constraints, parse and
validate CAP capability data when present, validate the HT `Rsiz`/profile bits,
and reject classic-only packet-header features that are not legal for the selected HT
profile. RGN and PPM/PPT are rejected unless a later fixture-driven design
explicitly proves their HT semantics; the initial phase does not guess.

The package boundary is explicit:

| Package | Reused by HTJ2K | HTJ2K-owned state |
| --- | --- | --- |
| `jpeg2000.common` | bounded marker/byte framing, immutable geometry/value records, raster normalization, identical transform math, stateless progression coordinates | none of the mutable packet, tag-tree, quantization-profile, or entropy state |
| `jpeg2000.classic` | no direct runtime dependency from the HT entry point | classic EBCOT/MQ, PCRD, mutable classic contributions/tag trees |
| `jpeg2000.htj2k` | consumes only the common contracts | CAP/Rsiz/profile policy, HT quantization defaults, MEL/VLC/MagSgn, cleanup/refinement, mutable HT contributions/tag trees, tile-part scheduling |

This keeps one deployable Maven module without coupling the two entropy
families through flags or shared mutable state.

### 6.2 Transform and quantization

- `Htj2kDwt53` and `Htj2kDwt97` provide explicit HT entry points, even when
  their lifting arithmetic is shared internally with classic transforms.
- `Htj2kQuantizer` maps guard bits, exponents, mantissas, and irreversible
  normalization to the COD/QCD representation expected by HT decoders.
- `Htj2kTileEncoder` and `Htj2kTileDecoder` own HT tile-part scheduling,
  code-block geometry, segment boundaries, and packet contribution order.

No HT class may call the classic EBCOT pass truncation code as a fallback.

### 6.3 Part 15 block coding

The HT block coder is split into independently testable primitives:

- `Htj2kMelEncoder` / `Htj2kMelDecoder` for the MEL run-length stream;
- `Htj2kVlcEncoder` / `Htj2kVlcDecoder` for VLC symbols and context state;
- `Htj2kMagSgnEncoder` / `Htj2kMagSgnDecoder` for magnitude/sign bits;
- `Htj2kCleanupPassEncoder` / `Htj2kCleanupPassDecoder` for the HT cleanup
  pass and segment termination;
- `Htj2kRefinementPass` for refinement data where the profile requires it;
- `Htj2kCodeBlock` for coefficient state, significance maps, and bounds.

Each primitive operates on bounded bit views and reports the first invalid bit
position. Tests must include empty blocks, all-zero blocks, one-symbol blocks,
maximum magnitude/sign combinations, byte stuffing, termination, and truncated
segments.

### 6.4 Packets and progression

`Htj2kPacketEncoder` / `Htj2kPacketDecoder` own all mutable HT packet headers,
tag trees, and contribution semantics. Only immutable geometry and stateless
coordinates are reused. RPCL traversal is implemented
as an explicit iterator over resolution, position, component, and layer. The
iterator must be tested for duplicate/missing packets and for tiles whose right
or bottom edge is smaller than the nominal tile size.

TLM markers are mandatory for the compatibility writer and are checked against
the exact `Psot` value for each tile-part. `Psot` includes SOT, SOD, and tile data
but excludes DICOM's even-length padding. The reference tile-part policy divides
at resolutions and concatenates the parts for PCRL/CPRL as required by the
OpenJPH-compatible layout. SOP/EPH support is accepted only where the parsed HT
coding style permits it; malformed marker lengths and packet bounds fail before
allocation.

The compatibility encoder writes one full-image tile and divides it into the
required resolution tile-parts. The release decoder accepts multiple tiles and
ordered tile-parts and validates complete `Isot`, `TPsot`, `TNsot`, `Psot`, and
TLM relationships. This is a decode-compatibility target, not arbitrary tiling
support in the initial writer.

## 7. Encode and Decode Flow

### Encode

1. Validate the descriptor and resolve the transfer-syntax-specific HT
   parameters; use the caller progression for `.202` and the codec default for
   `.201/.203`.
2. Normalize the frame, level-shift unsigned component samples, and apply the
   transfer-syntax-required RCT/ICT for three-component data.
3. Run the HT-specific reversible or irreversible DWT and quantization.
4. Partition tiles/precincts/code-blocks and encode each block through MEL/VLC/
   MagSgn cleanup and refinement stages.
5. Assemble HT packets in the selected progression, emit TLM with exact
   tile-part accounting, and write a raw codestream ending in EOC.
6. Return the codestream to ImageIO; dcm4che owns fragmentation and padding.

### Decode

1. Reject JP2 signatures and parse bounded SIZ/CAP/COD/QCD/TLM state before
   allocating coefficient or raster buffers. Validate SIZ width, height,
   component count, precision, signedness, supported sampling, and checked
   output size against `ImageDescriptor`.
2. Validate the HT profile, progression, tile-part lengths, and packet markers.
3. Decode MEL/VLC/MagSgn segments, reconstruct coefficients, inverse quantize,
   and run the HT-specific inverse DWT.
4. Apply inverse MCT only when COD declares it, undo component level
   shift/sample representation, then repack to descriptor signedness, planar
   configuration, and requested `BufferedImage` type. Apply
   `ImageReadParam` source region, subsampling, bands, and destination offset at
   this raster boundary; never alter codestream geometry for a partial read.

## 8. Errors, Safety, and Compatibility

Malformed markers, descriptor/codestream mismatches, invalid HT profile flags,
unsupported RGN/PPM/PPT semantics, bad progression ordering, truncated
MEL/VLC/MagSgn data, tile-part overflow, integer overflow, and invalid
precision/layout combinations become `IIOException` with
transfer syntax, operation, frame, marker, tile, and bit-position context.

Before allocating arrays, enforce configured limits for frame/codestream bytes,
sample count, tile-part count, packet count, code-block count, marker payload
length, and layer count. A present but inconsistent CAP/TLM entry is an error;
the reader does not infer missing packet data from a missing optional marker.

The implementation must never infer a missing packet, pad a truncated block, or
fall back to classic EBCOT. A codestream that self-decodes but fails a foreign
HTJ2K decoder is not accepted.

## 9. Verification Matrix

1. Primitive tests cover MEL, VLC, MagSgn, cleanup/refinement, bit stuffing,
   termination, and malformed/truncated segments.
2. Structural tests cover SIZ/CAP/COD/QCD parsing, descriptor mismatch, HT
   profile validation, multi-tile/tile-part bounds, TLM/Psot accounting, all
   supported packet progressions, and exclusion of DICOM padding from the
   logical codestream.
3. Lossless tests cover exact pixels for 8/12/16-bit allocated samples,
   monochrome/RGB/Palette Color, signed data, planar/interleaved input, odd
   dimensions, tile-edge geometry, and both lossless transfer syntaxes
   (`.201/.202`).
4. Lossy tests cover the exact `targetRatio` quality mapping and zero/default
   quantization policy, fixed pixel tolerances, host-owned metadata, and
   mandatory three-component MCT behavior.
5. External tests run bounded worker processes against fo-dicom.Codecs/OpenJPH
   through public APIs. Test all directions for `.201/.202/.203`; lossless
   comparisons are exact and lossy comparisons use fixed recorded tolerances.
6. DICOM/ImageIO tests verify exact syntax-specific SPI lookup, one-frame
   streams, fragment spanning, SOC-through-EOC boundaries, host-owned metadata,
   source-region/subsampling behavior, and no descriptor mutation.

Committed HTJ2K codestream fixtures and foreign-decoder pixel checks are release
gates. Self-round-trip, matching byte length, or matching compression ratio
alone is not evidence of HTJ2K correctness.

## 10. Implementation Order

1. Stabilize the classic shared marker/tile/packet models and DICOM adapters.
2. Add HT bit views and MEL/VLC/MagSgn primitives with vector tests.
3. Implement lossless HT code-block coding and `.201` grayscale round trips.
4. Add RGB/MCT, signed/planar samples, progression traversal, and `.202` options.
5. Add `.203` irreversible transform, quantization, layers, and external tests.
6. Enable HTJ2K SPI entries only after all three transfer syntaxes pass the
   structural, lossless/lossy, and foreign-decoder matrices.

## 11. Non-goals

- Any C/C++ implementation, JNI/P/Invoke bridge, native fallback, or runtime
  library selection.
- JP2/JPIP/JPT wrappers, Part 2 multi-component extensions, or speculative
  support for HT features without fixtures and standard-backed tests.
- Treating `.201`, `.202`, and `.203` as aliases of classic JPEG 2000.
