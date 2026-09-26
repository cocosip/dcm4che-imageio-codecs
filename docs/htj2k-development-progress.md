# HTJ2K Development Progress

## 1. Purpose and status rules

This document tracks implementation of the pure-Java HTJ2K codec specified by
[`htj2k-development-plan.md`](htj2k-development-plan.md). The design is the
authority when the two documents disagree. The scope is the three DICOM transfer
syntaxes `.201` (lossless), `.202` (lossless with selectable progression), and
`.203` (lossy) in `dcm4che-imageio-codecs-jpeg2000`.

Each phase is `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, or `COMPLETE`. A phase is
`COMPLETE` only after its exit gate has passed and the command, result, date, and
commit or working-tree reference have been recorded in Section 5. Code presence
and self-round-trip alone do not establish HTJ2K conformance. Record a specific
missing fixture, decision, or dependency before marking a phase `BLOCKED`.

## 2. Current snapshot

Snapshot date: 2026-09-26

| Item | State | Repository evidence |
| --- | --- | --- |
| HTJ2K design | Available | `docs/htj2k-development-plan.md` defines the scope and release gates. |
| Classic prerequisite | Available for assessment | `jpeg2000.common` contains marker I/O, geometry, transforms, raster normalization, limits, and progression iteration; classic `.90/.91` implementations and tests exist. HT reuse still needs validation. |
| HT implementation and tests | `IN_PROGRESS` | `jpeg2000.htj2k` has one-pass cleanup, one-layer packets, a one-tile reversible/irreversible encoder, and a multi-tile decoder. Syntax-bound ImageIO reader/writer classes and write parameters exist and have direct-call tests. |
| HT SPI registration | Disabled | Six syntax-specific SPI classes exist, but service files and UID property maps do not register `.201/.202/.203` until the final release matrix passes. |
| External HT interoperability | `IN_PROGRESS` | Committed OpenJPH 8/16-bit grayscale, RGB CPRL, four-tile RGB, and lossy RGB codestreams pass Java pixel checks. OpenJPH decodes Java `.201/.202` output exactly and `.203` 8/12-bit RGB output within recorded tolerances. Refinement and broader foreign matrix remain open. |

**Implementation progress: 1 of 6 phases complete.** H1 passed its structural
and adapter gate. H2-H6 remain in progress; implemented frame paths do not yet
establish the complete HT profile or justify SPI release.

## 3. Delivery sequence and exit gates

Phases follow Section 10 of the design. Tests and fixtures may be added ahead of
their phase, but a phase cannot be completed before its dependencies and exit
gate pass.

| Phase | Deliverable | Depends on | State |
| --- | --- | --- | --- |
| H1 | Validate shared foundation and establish HT-specific boundaries | Classic `.90/.91` baseline | `COMPLETE` |
| H2 | HT bounded bit views and MEL/VLC/MagSgn primitives | H1 | `IN_PROGRESS` |
| H3 | HT cleanup, packets, and `.201` grayscale lossless path | H2 | `IN_PROGRESS` |
| H4 | RGB/MCT, signed/planar samples, progression, and `.202` | H3 | `IN_PROGRESS` |
| H5 | `.203` irreversible path and all-syntax external interoperability | H4 | `IN_PROGRESS` |
| H6 | ImageIO/DICOM integration, hardening, and SPI release | H3-H5 | `IN_PROGRESS` |

### H1: Shared foundation and HT boundaries

- [x] Verify that shared marker framing, immutable geometry, raster normalization,
  RCT/ICT, DWT math, and stateless progression coordinates satisfy HT needs.
- [x] Establish the `jpeg2000.htj2k` package and a distinct `Htj2kFrameCodec`
  selected at the transfer-syntax adapter boundary.
- [x] Keep mutable classic packet/tag-tree state, MQ/EBCOT, PCRD, and HT entropy
  state in their respective packages; do not dispatch through a classic coder flag.
- [x] Define bounded CAP, HT `Rsiz`/profile, tile-part, and marker policy. Reject
  unsupported RGN/PPM/PPT and JP2 wrapping with contextual `IIOException`.

**Exit gate:** focused structural tests verify shared contracts and HT-specific
profile rejection without changing classic `.90/.91` behavior.

The HT parser validates SOC/SIZ, optional CAP including Ccap15/QCD agreement,
HT COD/QCD style, optional TLM, ordered SOT/SOD tile-parts, EOC, and DICOM
padding. Syntax-specific adapters select `Htj2kFrameCodec`; focused parser,
adapter, and classic tests pass. The adapters are directly callable, while
SPI service registration remains an H6 gate.

### H2: Part 15 block primitives

- [x] Implement bounded bit views and independently testable MEL, VLC, MagSgn,
  one-pass cleanup, and code-block state.
- [ ] Implement and validate cleanup plus refinement passes required by foreign
  codestreams.
- [x] Test all-zero/one-symbol blocks, large magnitude/sign values, stuffing,
  termination, and malformed bounds against fixed HT block vectors.
- [ ] Report the first invalid bit position without over-read or non-progress.

**Exit gate:** fixed external or standard-backed vectors pass in both directions;
malformed and truncated segments fail within configured limits.

The fixed cleanup vectors were generated with the local OpenJPH block encoder in
`D:/dotnet-source-code/fo-dicom.Codecs/Native/Common/OpenJPH` (fo-dicom.Codecs
revision `da3fe114fc918756285ce1f25be265e7b74360a3`). The Java cleanup
encoder matches those bytes and its decoder recovers the exact coefficients.
These block vectors do not establish packet or full-codestream interoperability.

A manually constructed 2x2 cleanup/SPP/MRP vector (`FE0063000101`, cleanup
length 4, refinement length 2, missing MSBs 5, three passes) has Java unit
coverage for nonzero magnitudes 7 and 3. A broader foreign refinement matrix
and bit-position/error checks remain open. The C# fo-dicom.Codecs encoder
currently emits one cleanup pass, so it cannot produce a refinement fixture.

### H3: `.201` lossless vertical slice

- [x] Add an independently testable, one-layer inline HT packet header/body
  codec with tag-tree inclusion, missing-bitplane values, length bounds, and
  malformed-header rejection.
- [x] Implement HT packet contributions and tag trees, reversible 5/3 policy,
  one-layer 64x64 code blocks, and a one-full-image-tile encoder.
- [x] Emit CAP and exact TLM/`Psot` accounting for resolution tile-parts; end the
  raw codestream at EOC without counting DICOM padding.
- [x] Decode a foreign `.201` grayscale codestream and verify exact pixels; have
  a foreign HT decoder verify Java `.201` output.
- [x] Check SIZ/descriptor agreement, CAP/profile constraints, packet bounds,
  multiple tiles, and ordered tile-parts in the decoder.

**Exit gate:** exact grayscale encode/decode and bidirectional foreign pixel
checks pass, including odd dimensions and malformed structure cases.

### H4: Color, sample layouts, and `.202`

- [ ] Add required three-component RCT, signed and 8/12/16-bit sample handling,
  planar/interleaved RGB, Palette Color indices, and supported YBR normalization.
- [ ] Add explicit RPCL traversal and all five selectable `.202` progression
  orders, with default RPCL and fixed RPCL for `.201`.
- [ ] Verify no duplicate or missing packets at clipped tile edges and correct
  COD MCT state and host-owned photometric metadata.
- [ ] Check exact pixels and foreign encode/decode for `.201` color and `.202`,
  including CPRL.

The frame-level `.201/.202` implementation applies RCT for RGB and accepts all
five `.202` progression orders. Focused tests cover 8/12/16-bit signed and
unsigned samples and odd dimensions. The OpenJPH RGB CPRL fixture passes exact
Java pixel comparison, while OpenJPH decoded Java RGB CPRL at 8-bit unsigned,
12-bit unsigned, and 16-bit signed to byte-identical frames. ImageIO planar,
Palette Color, YBR normalization, and the remaining external matrix are open.

**Exit gate:** the lossless design matrix and both directions of external pixel
comparison pass for `.201/.202`.

### H5: `.203` irreversible path and interoperability

- [x] Add HT 9/7 transform and quantization policy, mandatory three-component
  ICT, default `targetRatio=0` policy, and the specified quality-hint mapping.
- [x] Enforce one layer and the supported write parameters; reject unsupported
  precision, sampling, and profile features.
- [x] Run `.203` foreign encode to Java decode and Java encode to foreign decode
  with fixed recorded pixel tolerances.
- [ ] Commit HT fixtures with provenance, dimensions, precision, signedness,
  transfer syntax, parameters, and expected pixels or tolerance.

The default `.203` frame path uses 9/7, ICT for RGB, one HT cleanup pass, and
OpenJPH-matched QCD/CAP defaults. The 128x128 unsigned 8-bit OpenJPH RGB RPCL
fixture decodes within 12 sample codes per component. OpenJPH decodes Java
`.203` RGB output with maximum per-component absolute errors of `2/1/3` for
8-bit and `2/2/2` for 12-bit source samples. `targetRatio=2` maps to quality
hint 92 and a doubled quantization step; OpenJPH decodes the Java stream with
maximum component errors `5/3/5`. Broader source layouts and the full 12-bit
foreign-to-Java matrix remain open.

**Exit gate:** lossy tolerance and parameter tests pass; all three syntaxes have
bidirectional foreign pixel evidence, including 12-bit-in-16-bit cases.

### H6: ImageIO/DICOM integration and release

- [ ] Implement syntax-specific reader/writer SPI pairs for `.201`, `.202`, and
  `.203`; remove the generic commented placeholders and enable exact service
  entries only after focused tests pass.
- [ ] Verify one logical frame per call, fragment-spanning input, SOC-to-EOC
  bounds, descriptor immutability, metadata ownership, and source region,
  subsampling, bands, and destination offset at the raster boundary.
- [ ] Check malformed markers, CAP/TLM inconsistencies, tile-part ordering,
  entropy truncation, checked allocation limits, and failure isolation.
- [ ] Run focused module tests, the full Maven reactor, clean packaging, and a
  built-JAR SPI lookup smoke test.

**Exit gate:** every gate in Section 4 passes before HT SPI entries are treated
as released. Generic `jpeg2000` or `htj2k` writer aliases remain absent.

## 4. Release checklist

- [ ] `.201/.202` exact-pixel matrix passes for supported mono, RGB, Palette,
  signed/unsigned, planar/interleaved, and 8/12/16-bit samples.
- [ ] `.203` default and ratio-mapped output pass fixed pixel tolerances.
- [ ] All five `.202` progressions, multiple decode tiles, ordered tile-parts,
  CAP/profile validation, and TLM/`Psot` accounting pass.
- [ ] Malformed marker, packet, bitstream, precision, and resource-limit tests
  fail with contextual `IIOException` and no unbounded allocation.
- [ ] Each syntax has committed foreign-to-Java and Java-to-foreign pixel checks;
  a self-round-trip or byte-size comparison is insufficient.
- [ ] DICOM/ImageIO frame boundaries, fragment spanning, padding exclusion,
  metadata ownership, partial reads, and exact syntax-specific SPI lookup pass.
- [ ] Focused tests, full reactor, clean package, and built-JAR registration
  smoke test pass.

## 5. Verification evidence

| Date | Phase | Reference | Command or artifact | Result |
| --- | --- | --- | --- | --- |
| 2026-09-25 | Baseline | Working tree | Repository inspection of HT design, JPEG 2000 sources/tests, fixtures, and SPI service files | Classic foundation exists; HT source, tests, fixtures, and active SPI entries are absent. No HT phase is complete. |
| 2026-09-25 | H1 | Working tree | `mvn -pl dcm4che-imageio-codecs-jpeg2000 -am test -q` | Passed after adding HT structural parser tests; classic and shared tests remain passing. H1 remains in progress pending adapter wiring and foreign CAP/profile vectors. |
| 2026-09-25 | H2 | Working tree | `mvn -pl dcm4che-imageio-codecs-jpeg2000 -am test -q`; `Htj2kCleanupPassTest` vectors from local OpenJPH | 173 tests passed. MEL/VLC/MagSgn primitives and cleanup block encode/decode match recorded OpenJPH bytes and coefficients; refinement and full-frame interoperability remain open. |
| 2026-09-26 | H3 | Working tree after `a15af20` | `mvn -pl dcm4che-imageio-codecs-jpeg2000 -am test -q`; `Htj2kPacketCodecTest` | Passed. One-layer inline packet headers, empty/mixed subbands, and bounded decoding have local tests; no foreign codestream packet or pixel comparison yet. |
| 2026-09-26 | H3 | Working tree after `f4f06cf` | `mvn -pl dcm4che-imageio-codecs-jpeg2000 -am test -q`; `Htj2kForeignFrameTest`; `src/test/resources/jpeg2000/htj2k_openjph_gray128.j2c` | Passed. Java decoded the OpenJPH 128x128 unsigned 8-bit grayscale fixture to 16,384 exact expected pixels. The fixture was generated with local OpenJPH in fo-dicom.Codecs revision `da3fe114fc918756285ce1f25be265e7b74360a3`. OpenJPH decoded Java-generated output (16,129 bytes) to raw pixels with SHA-256 `ED4EF963EBE8FDCF159BBCDCB4E65B583EC1EAAF8F4260EFC7BB2CC50EB4CCA0`; expected raw pixels have the same hash. The OpenJPH fixture is 16,166 bytes. Other precision, color, progression, multi-tile, and ImageIO gates remain open. |
| 2026-09-26 | H4 | Working tree after `252ff7d` | `mvn -pl dcm4che-imageio-codecs-jpeg2000 -am test -q`; `Htj2kForeignFrameTest`; `src/test/resources/jpeg2000/htj2k_openjph_rgb128_cprl.j2c` | Passed. The 11,161-byte 128x128 unsigned 8-bit RGB CPRL fixture was generated with the same local OpenJPH revision; Java matched all 49,152 component samples exactly. OpenJPH decoded Java-generated RGB CPRL streams at 8-bit unsigned (53,718 bytes), 12-bit unsigned (67,245 bytes), and 16-bit signed (22,050 bytes) to byte-identical raw frames. Matching expected/decoded SHA-256 values are respectively `991F1C2FCF1182E8AAFB782F33EC643FB144D7901A102AD88AE410C233EE946C`, `DDD3C920DDD32DCFC6ECE7E647A08C139DAD49A2CA4A92309EB796DC1B65E26A`, and `21686458ACDD399371CA5FCF36963C6CDB715D7C7F155BCC2ECD47170CD70AFD`. This does not complete H4. |
| 2026-09-26 | H5 | Working tree after `c9892ae` | `mvn -pl dcm4che-imageio-codecs-jpeg2000 -am test -q`; `Htj2kQuantizerTest`; `src/test/resources/jpeg2000/htj2k_openjph_lossy_rgb128.j2c`; local OpenJPH decoder | Passed. The 13,427-byte 128x128 unsigned 8-bit RGB RPCL fixture was generated from the local OpenJPH revision above. Java QCD output matches its 33-byte QCD exactly, and Java decodes its pixels within 12 codes. OpenJPH decoded Java `.203` RGB frames at 8 and 12 bits with maximum absolute component errors `2/1/3` and `2/2/2`. Default quantization only; H5 is not complete. |
| 2026-09-26 | H1 | Working tree after `3395fd4` | `mvn test -q`; `Htj2kCodestreamParserTest`; `Htj2kImageIoTest`; existing classic JPEG 2000 tests | Passed. Syntax-bound adapters, CAP/Ccap and Rsiz profile rejection, forbidden markers, and the classic baseline pass in the full reactor. H1 exit gate is complete; SPI release remains separate. |
| 2026-09-26 | H3/H4 | Working tree after `3395fd4` | `mvn -pl dcm4che-imageio-codecs-jpeg2000 -am test -q`; `src/test/resources/jpeg2000/htj2k_openjph_gray16.j2c`; `src/test/resources/jpeg2000/htj2k_openjph_rgb128_four_tiles.j2c` | Passed. Java exactly decoded the 594-byte OpenJPH 128x128 unsigned 16-bit grayscale fixture and 12,555-byte four-tile (64x64 tiles) unsigned 8-bit RGB fixture. Both were generated with local OpenJPH revision `da3fe114fc918756285ce1f25be265e7b74360a3`. Refinement and odd-dimension foreign fixtures remain open. |
| 2026-09-26 | H5/H6 | Working tree after `3395fd4` | `mvn test -q`; direct syntax-bound ImageIO tests; local OpenJPH decoder on Java `.203 targetRatio=2` output | Passed. Ratio-hinted 128x128 RGB stream is 44,469 bytes and OpenJPH maximum component errors are `5/3/5`. Direct ImageIO tests cover `.201` mono/Palette, `.202` planar RGB CPRL with region/subsampling and fragmented input, `.203` RGB tolerance, and cross-syntax parameter rejection. SPI service entries remain disabled. |
| 2026-09-26 | H2/H6 | Working tree after refinement changes | `mvn -pl dcm4che-imageio-codecs-jpeg2000 -am clean test -q`; `mvn -pl dcm4che-imageio-codecs-jpeg2000 -am package -DskipTests -q`; `mvn test -q` | Passed. Packet headers parse Part 15 pass counts and bounded cleanup/refinement lengths; the compatibility writer remains one cleanup pass. SPI registration was reverted pending foreign refinement vectors and the final release matrix. |
| 2026-09-26 | H2 | Working tree after `856e575` | `mvn -pl dcm4che-imageio-codecs-jpeg2000 -am test -q` | Passed. Java tests cover the fixed 2x2 refinement vector, packet segment lengths, and truncated MRP. C# fo-dicom.Codecs interoperability remains to be verified. |
| 2026-09-26 | H3-H5 | Working tree after `856e575` | `tools/htj2k-interop` Java/C# probe using fo-dicom.Codecs C# API and native package `6.0.0-beta1`; 128x128 unsigned 8-bit deterministic frames | Passed bidirectionally: `.201` grayscale and `.202` RGB differ by 0 sample codes; `.203` RGB maximum absolute error is 3 for Java encode to C# decode and 4 for C# encode to Java decode. C# source assembly came from local fo-dicom.Codecs checkout. This does not cover 12-bit, signed, odd-size, or refinement foreign matrices. |

For each later status change, append the exact command or fixture, result, date,
and commit or working-tree reference here. Do not mark a phase complete until its
exit gate and the relevant release checks are evidenced.
