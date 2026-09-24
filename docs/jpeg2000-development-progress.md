# JPEG 2000 Development Progress

## 1. Purpose

This document is the execution ledger for the pure-Java classic JPEG 2000
codec. The design authority is
[`jpeg2000-development-plan.md`](jpeg2000-development-plan.md). If this ledger
and the design disagree, the design wins and this ledger must be corrected.

Only DICOM JPEG 2000 Lossless (`1.2.840.10008.1.2.4.90`) and JPEG 2000 Lossy
(`1.2.840.10008.1.2.4.91`) are in scope. HTJ2K (`.201/.202/.203`) remains
deferred until every release gate in Section 7 is complete.

## 2. Status Rules

Each phase has one of four states:

- `NOT_STARTED`: no implementation evidence has been accepted;
- `IN_PROGRESS`: tests or implementation exist, but the phase exit gate is not
  complete;
- `BLOCKED`: work cannot continue without a named decision, fixture, or external
  dependency;
- `COMPLETE`: the phase exit gate passed and its evidence is recorded here.

A phase is not complete because code exists or a self-round-trip passes. The
tests, interoperability evidence, malformed-input checks, and documentation
named by its exit gate must all pass. Status changes must include the date,
commit or working-tree reference, verification command, and result in Section
8.

## 3. Current Snapshot

Snapshot date: 2026-09-24

| Item | State | Evidence |
| --- | --- | --- |
| Classic JPEG 2000 design | `COMPLETE` | `docs/jpeg2000-development-plan.md`, commit `2fd09f2` |
| Java implementation | `IN_PROGRESS` | P1-P4 complete; P5 lossless vertical slice is next. |
| SPI registration | `NOT_STARTED` | Reader and writer service entries remain commented out. |
| External interoperability | `NOT_STARTED` | No committed `.90/.91` fixtures or external pixel checks exist. |
| Active phase | `P5` | `.90` lossless codec vertical slice |

Implementation progress is **4 of 9 phases complete**. Design completion is
tracked separately and is not counted as codec implementation.

## 4. Delivery Sequence

| Phase | Deliverable | Depends on | State |
| --- | --- | --- | --- |
| P1 | Bounded codestream and marker foundation | Design baseline | `COMPLETE` |
| P2 | Geometry, raster normalization, RCT, and reversible 5/3 DWT | P1 | `COMPLETE` |
| P3 | MQ coder and EBCOT code-block coding | P1-P2 | `COMPLETE` |
| P4 | Tag trees, packets, progression orders, tiles, and tile-parts | P1-P3 | `COMPLETE` |
| P5 | `.90` lossless codec vertical slice | P1-P4 | `NOT_STARTED` |
| P6 | 9/7 DWT, quantization, PCRD rate allocation, and `.91` | P1-P5 | `NOT_STARTED` |
| P7 | Full required decoder compatibility and resource hardening | P1-P6 | `NOT_STARTED` |
| P8 | ImageIO/dcm4che integration and syntax-specific SPI | P5-P7 | `NOT_STARTED` |
| P9 | Bidirectional external interoperability and release gate | P5-P8 | `NOT_STARTED` |

Phases are ordered by dependency, not calendar estimate. A later phase may add
fixtures or failing tests early, but its production path cannot be declared
complete before its dependencies pass.

## 5. Planned File Ownership

The implementation uses Java 8-compatible classes; Java records and post-Java
8 APIs are not allowed by the parent build.

| Package | Planned responsibility |
| --- | --- |
| `jpeg2000.common` | Bounded byte/bit I/O, marker framing, immutable value classes, checked limits, raster normalization, component transforms, DWT math, and stateless progression coordinates |
| `jpeg2000.classic` | Classic marker policy, quantization, MQ/EBCOT, classic tag trees and packet state, PCRD allocation, and classic encode/decode orchestration |
| `jpeg2000` | Public syntax-specific ImageIO reader/writer SPI, write parameters, and DICOM raster adapters |

Mutable packet contribution state, mutable tag trees, MQ contexts, EBCOT state,
and classic rate allocation stay in `jpeg2000.classic`. They must not be placed
in `jpeg2000.common` for possible HTJ2K reuse.

## 6. Phase Gates

### P1: Bounded codestream and marker foundation

**Production files**

- `jpeg2000/common/Jpeg2000Exception.java`: internal checked codec failure with
  operation and marker context;
- `jpeg2000/common/Jpeg2000Limits.java`: frame, codestream, marker, tile,
  code-block, packet, and layer limits with checked size arithmetic;
- `jpeg2000/common/Jpeg2000Marker.java`: Part 1 marker constants and standalone
  versus length-bearing classification;
- `jpeg2000/common/Jpeg2000Input.java` and `Jpeg2000Output.java`: bounded
  big-endian reads/writes and progress-guaranteed loops;
- `jpeg2000/common/Jpeg2000CodestreamReader.java` and
  `Jpeg2000CodestreamWriter.java`: SOC-through-EOC framing, segment-length
  validation, and EOC boundary handling;
- Java 8-compatible immutable classes for SIZ, COD/COC, QCD/QCC, SOT, COM, and
  the baseline tile-part header state;
- `jpeg2000/classic/Jpeg2000ClassicCodestream.java`,
  `Jpeg2000ClassicCodestreamParser.java`, and
  `Jpeg2000ClassicCodestreamWriter.java`: P1 classic marker policy, one bounded
  tile-part, and deterministic baseline emission.

**Test files**

- `jpeg2000/common/Jpeg2000LimitsTest.java`;
- `jpeg2000/common/Jpeg2000MarkerIoTest.java`;
- `jpeg2000/common/Jpeg2000CodestreamReaderTest.java`;
- `jpeg2000/classic/Jpeg2000ClassicCodestreamTest.java`.

**Execution checklist**

- [x] Add failing tests for valid SOC/SIZ/COD/QCD/COM/SOT/SOD/EOC parsing and
  deterministic baseline marker writing.
- [x] Add failing tests for a JP2 signature, bytes before SOC, duplicate or
  missing required markers, EOC omission, truncated segments, lengths below
  two, declared lengths beyond the bounded frame, unknown semantic markers,
  and stopping exactly at EOC without consuming trailing frame bytes.
- [x] Add failing tests for SIZ dimensions, component precision/signedness,
  unsupported component subsampling, tile bounds, code-block exponents,
  decomposition limits, layer limits, and checked allocation overflow.
- [x] Implement only enough bounded input/output, marker models, and parser
  state to pass those tests.
- [x] Prove that every parser loop consumes input or throws and that no buffer
  is allocated before its declared size passes `Jpeg2000Limits`.
- [x] Run the focused tests and the complete reactor tests.

**Exit gate**

P1 is complete when the marker tests accept the deterministic baseline profile,
reject every named malformed class with contextual `IOException`/`IIOException`
causes, and the full Maven reactor remains green. P1 does not decode packet data
and does not enable ImageIO service entries.

### P2: Geometry, raster normalization, RCT, and reversible 5/3 DWT

- [x] Build immutable image/tile/component/resolution/subband/code-block
  geometry with odd-origin, odd-dimension, clipped edge, empty subband, and
  checked pre-allocation coverage.
- [x] Convert 8/16-bit DICOM raster layouts, planar RGB, `YBR_FULL`, and
  row-bounded `YBR_FULL_422` to normalized components while preserving signed
  sample semantics, allocated container width, and Palette Color indices.
- [x] Implement exact forward/inverse RCT and reversible 5/3 lifting with
  single-sample, odd/even length, multi-level, degenerate-axis, and symmetric
  boundary-extension tests.
- [x] Reject unsupported photometric interpretations, invalid frame lengths,
  `YBR_PARTIAL_422`/`YBR_PARTIAL_420` encode, and implicit overlay masking.

**Exit gate:** exact forward/inverse reconstruction passes for 8/12/16-bit,
signed/unsigned, mono/RGB/Palette, planar/interleaved, and odd-sized fixtures;
all checked geometry limits pass before allocation.

### P3: MQ coder and EBCOT code-block coding

- [x] Add the standard 47-state MQ probability table, context state
  transitions, interval renormalization, bounded byte refill, and flush.
- [x] Add independent state-table, long-sequence round-trip, reset, context,
  and invalid-input tests for the MQ foundation.
- [x] Complete MQ byte-stuffing and baseline MQ termination with an independent
  fo-dicom conformance vector. `BYPASS`, `TERMALL`, and `PTERM` are rejected
  explicitly until their separate raw/terminated segment paths are implemented.
- [x] Implement significance propagation, magnitude refinement, cleanup,
  pass boundaries, truncation points, and the COD/COC `RESET` and `VSC`
  styles.
- [x] Add code-block result metadata and deterministic round-trip tests for
  all three pass types, truncation, zero blocks, and `SEGMARK`.
- [x] Keep encoder and decoder tests independent where a fixed standard or
  foreign vector is available; self-round-trip remains supplemental evidence.

**Exit gate:** fixed MQ and code-block vectors pass in both directions,
truncation at every accepted pass boundary is bounded, and malformed entropy
data cannot over-read or loop without progress.

### P4: Tag trees, packets, progression orders, tiles, and tile-parts

- [x] Implement classic inclusion and zero-bit-plane tag trees with mutable
  state owned by one classic precinct/subband packet codec.
- [x] Implement bounded inline packet-header bit I/O, multi-layer code-block
  contribution state, pass counts, `Lblock`, and contribution lengths.
- [x] Implement stateless LRCP/RLCP/RPCL/PCRL/CPRL coordinate iteration with
  checked packet limits.
- [x] Integrate precinct/code-block traversal with tile geometry.
- [x] Decode multiple tiles and ordered tile-parts with complete `Isot`, `TPsot`,
  `TNsot`, and `Psot` validation; the writer remains one full-image tile.

**Exit gate:** packet and tile tests prove all five progression orders,
multi-layer contribution state, inline packet headers, tile-part ordering, and
malformed length/index rejection without sharing mutable state with HTJ2K.

### P5: `.90` lossless codec vertical slice

- Assemble the classic lossless encoder and decoder using five decomposition
  levels, 64x64 code blocks, reversible quantization, and a required final
  zero-rate layer.
- Add syntax-bound lossless reader/writer adapters without enabling service
  registration.
- Cover grayscale first, then RGB/RCT, signed samples, planar input, Palette
  Color, and one-frame ImageIO stream boundaries.
- Add at least one foreign `.90` decode fixture before declaring the decoder
  path complete.

**Exit gate:** exact pixels pass for the design matrix; the Java decoder reads a
foreign `.90` codestream exactly; the produced codestream has valid marker and
packet semantics. Java-encode to foreign-decode is recorded in P9.

### P6: 9/7 DWT, quantization, PCRD rate allocation, and `.91`

- Implement controlled-rounding 9/7 lifting, irreversible subband
  quantization, QCD/QCC exponent/mantissa encoding, and guard-bit validation.
- Implement distortion-length slope calculation, PCRD-style layer truncation,
  and the complete parameter precedence for `targetRatio`, native-compatible
  `rate`/`rateLevels`, and generic ImageIO quality.
- Enforce `.91` irreversible defaults, one-component reversible `.91`, and the
  rejection of reversible three-component public `.91` output.
- Test deterministic output, monotonic layer sizes, fixed error bounds, MCT/COD
  agreement, and DICOM padding exclusion from `Psot`.

**Exit gate:** `.91` default and explicit reversible paths pass their pixel/error
contracts and at least one foreign `.91` codestream decodes within a committed,
fixed tolerance.

### P7: Full required decoder compatibility and resource hardening

- Complete COC/QCC overrides, POC, Maxshift RGN, PPM/PPT, SOP/EPH, and accepted
  TLM/PLM/PLT semantics against committed interoperability fixtures.
- Apply `ImageReadParam` source regions and subsampling at the raster boundary,
  without changing codestream geometry.
- Fuzz or systematically mutate marker lengths, tile indexes, packet counts,
  truncation points, stuffing bytes, and EOC boundaries.
- Verify per-instance and concurrent reuse behavior and discard partial output
  after failure.

**Exit gate:** all release-reader features in the design matrix are either
implemented and fixture-backed or rejected exactly where the design requires;
resource-limit and malformed-input suites pass without unbounded allocation or
non-progress loops.

### P8: ImageIO/dcm4che integration and syntax-specific SPI

- Provide separate lossless/lossy reader and writer SPI classes and exact
  `jpeg2000-lossless` / `jpeg2000-lossy` registrations.
- Prove one logical frame per invocation, fragment-spanning input, host-owned
  metadata changes, descriptor immutability, multi-frame caller orchestration,
  and exclusion of DICOM even-length padding.
- Enable the two classic reader and two classic writer service entries only
  after their focused and integration suites pass. Do not enable HTJ2K entries
  or a generic `jpeg2000` writer alias.

**Exit gate:** dcm4che finds only the exact syntax-specific providers; `.90/.91`
compress/decompress integration passes with expected photometric metadata; all
HTJ2K registrations remain disabled.

### P9: Bidirectional external interoperability and release gate

- For both `.90` and `.91`, record foreign encode -> Java decode and Java encode
  -> foreign decode using `fo-dicom.Codecs`/OpenJPEG in a separate process.
- Commit redistributable raw J2K/DICOM fixtures plus provenance, encoder version,
  parameters, expected dimensions, precision, signedness, photometric metadata,
  and pixel hash or lossy tolerance.
- Run focused module tests, full reactor tests, clean packaging, and a service
  registration smoke test from the built JAR.
- Reconcile this ledger, the design, module description, SPI resources, and the
  tested capability matrix.

**Exit gate:** every gate in Section 7 passes. Only then may classic JPEG 2000 be
declared complete and HTJ2K planning move into implementation.

## 7. Classic JPEG 2000 Release Gates

- [ ] `.90` exact encode/decode matrix passes for 8/12/16-bit allocated samples.
- [ ] `.91` irreversible and permitted reversible paths pass fixed error/exact
  pixel contracts.
- [ ] All five progression orders, quality layers, multiple tiles, and ordered
  tile-parts pass the required decode matrix.
- [ ] Signed samples, RGB/RCT/ICT, planar/interleaved input, Palette Color,
  `YBR_FULL`, `YBR_FULL_422`, and decode-compatible `YBR_PARTIAL_422` pass.
- [ ] ImageReadParam region/subsampling and multi-frame caller orchestration pass.
- [ ] Malformed marker, packet, entropy, overflow, and resource-limit tests pass.
- [ ] `.90` and `.91` each have bidirectional external pixel evidence.
- [ ] DICOM fragment spanning, padding exclusion, metadata ownership, and exact
  syntax-specific lookup pass.
- [ ] SPI service entries expose only the completed classic providers.
- [ ] Full Maven reactor and clean package verification pass.

## 8. Verification Evidence

| Date | Phase | Reference | Command or artifact | Result |
| --- | --- | --- | --- | --- |
| 2026-09-24 | Baseline | `2fd09f2` | Repository inspection | Design present; JPEG 2000 module contains no Java implementation/tests; SPI entries commented. |
| 2026-09-24 | P1 | `ca02e85` | `./mvnw.cmd -pl dcm4che-imageio-codecs-jpeg2000 -am "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=Jpeg2000LimitsTest,Jpeg2000MarkerIoTest,Jpeg2000CodestreamReaderTest,Jpeg2000ClassicCodestreamTest" test` | 24 JPEG 2000 tests passed. |
| 2026-09-24 | P1 | `ca02e85` | `./mvnw.cmd test` | Full reactor passed: 275 tests, 0 failures, 0 errors, 0 skipped. |
| 2026-09-24 | P2 | Working tree | `./mvnw.cmd -pl dcm4che-imageio-codecs-jpeg2000 -am "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=Jpeg2000GeometryTest,Jpeg2000Dwt53Test,Jpeg2000ComponentTransformTest,Jpeg2000RasterNormalizerTest" test` | 34 P2 tests passed, including the 8/12/16-bit signed/unsigned reversible raster matrix. |
| 2026-09-24 | P2 | Working tree | `./mvnw.cmd test` | Full reactor passed: 309 tests, 0 failures, 0 errors, 0 skipped; JPEG 2000 module passed 58 tests. |
| 2026-09-24 | P3 | Working tree | `./mvnw.cmd -pl dcm4che-imageio-codecs-jpeg2000 -am "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=Jpeg2000MqCoderTest" test` | 5 MQ foundation tests passed; state table, refill/flush round-trip, reset, byte-range, and invalid context checks are green. |
| 2026-09-24 | P3 | Working tree | `./mvnw.cmd test` | Full reactor passed: 314 tests, 0 failures, 0 errors, 0 skipped; JPEG 2000 module passed 63 tests. |
| 2026-09-24 | P3 | Working tree | `./mvnw.cmd -pl dcm4che-imageio-codecs-jpeg2000 -am "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=Jpeg2000EbcotTest" test` | 5 EBCOT tests passed; all three pass types, truncation, zero blocks, RESET/VSC/SEGMARK, and Integer.MIN_VALUE rejection are covered. |
| 2026-09-24 | P3 | fo-dicom PureCodecs independent vectors | MQ `B98D284C028E`; EBCOT `0EC977D2CB764A` and `04CB5B8B` | Java encoding matches all three foreign vectors and Java decoding reconstructs their symbol/coefficient sequences. |
| 2026-09-24 | P3 | Working tree | `./mvnw.cmd -pl dcm4che-imageio-codecs-jpeg2000 -am "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=Jpeg2000MqCoderTest,Jpeg2000EbcotTest" test` | 19 P3 tests passed; every recorded pass prefix is bounded, all orientations and accepted styles round-trip, and malformed stuffing, metadata, payload boundaries, and segmentation symbols are rejected. |
| 2026-09-24 | P3 | Working tree | `./mvnw.cmd -pl dcm4che-imageio-codecs-jpeg2000 -am test` | JPEG 2000 module passed 77 tests, 0 failures, 0 errors, 0 skipped. |
| 2026-09-24 | P3 | Working tree | `./mvnw.cmd test` | Full reactor passed: 328 tests, 0 failures, 0 errors, 0 skipped. P3 exit gate satisfied. |
| 2026-09-24 | P4 | fo-dicom PureCodecs independent vector | Inline packet `EA201020` | Java packet encoding matches the foreign inclusion, zero-bit-plane, pass-count, `Lblock`, length, and body bytes. |
| 2026-09-24 | P4 | Working tree | `./mvnw.cmd -pl dcm4che-imageio-codecs-jpeg2000 -am test` | JPEG 2000 module passed 90 tests; packet bit I/O, tag trees, multi-layer contributions, malformed lengths, and all five progression orders are covered. |
| 2026-09-24 | P4 | Working tree | `./mvnw.cmd test` | Full reactor passed 341 tests, 0 failures, 0 errors, 0 skipped. |
| 2026-09-24 | P4 | Working tree | `./mvnw.cmd -pl dcm4che-imageio-codecs-jpeg2000 -am clean test` | JPEG 2000 module passed 99 tests; COD-driven precinct/code-block geometry, spatial progression, multiple tiles, ordered/interleaved tile-parts, and malformed `Isot`/`TPsot`/`TNsot`/`Psot` are covered. P4 exit gate satisfied. |
| 2026-09-24 | P4 | Working tree | `./mvnw.cmd test` | Full reactor passed 350 tests, 0 failures, 0 errors, 0 skipped. |

For focused Java tests, use this PowerShell command shape and replace the test
class name with the current phase suite:

```powershell
.\mvnw.cmd -pl dcm4che-imageio-codecs-jpeg2000 -am `
  -Dsurefire.failIfNoSpecifiedTests=false `
  "-Dtest=Jpeg2000MarkerIoTest" test
```

The full regression command is:

```powershell
.\mvnw.cmd test
```

External tools are evidence generators or consumers only. They are not Maven
dependencies, runtime fallbacks, or substitutes for the pure-Java implementation.

## 9. Next Work Item

Start P5 by assembling the classic `.90` lossless grayscale vertical slice from
the existing reversible transform, EBCOT/MQ, packet, geometry, and tile-part
layers. Add syntax-bound ImageIO adapters without enabling service registration,
and obtain a foreign `.90` decode fixture before declaring P5 complete.
