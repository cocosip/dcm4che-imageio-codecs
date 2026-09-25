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

Snapshot date: 2026-09-25

| Item | State | Repository evidence |
| --- | --- | --- |
| HTJ2K design | Available | `docs/htj2k-development-plan.md` defines the scope and release gates. |
| Classic prerequisite | Available for assessment | `jpeg2000.common` contains marker I/O, geometry, transforms, raster normalization, limits, and progression iteration; classic `.90/.91` implementations and tests exist. HT reuse still needs validation. |
| HT implementation and tests | `IN_PROGRESS` | `jpeg2000.htj2k` has structural inspection, bounded MEL/VLC/MagSgn primitives, and a one-pass cleanup block encoder/decoder checked against OpenJPH bytes. HT packets, full-frame coding, and ImageIO adapters are absent. |
| HT SPI registration | Disabled | Reader/writer service files contain only commented, generic HTJ2K placeholders; no `.201/.202/.203` providers are registered. |
| External HT interoperability | `NOT_STARTED` | No committed HT codestream fixtures or foreign-decoder pixel checks are present. |

**Implementation progress: 0 of 6 phases complete.** H1 is in progress. The
classic codec remains a prerequisite, not evidence that an HT phase has passed.
Structural inspection does not decode HT packets or produce an HT codestream.

## 3. Delivery sequence and exit gates

Phases follow Section 10 of the design. Tests and fixtures may be added ahead of
their phase, but a phase cannot be completed before its dependencies and exit
gate pass.

| Phase | Deliverable | Depends on | State |
| --- | --- | --- | --- |
| H1 | Validate shared foundation and establish HT-specific boundaries | Classic `.90/.91` baseline | `IN_PROGRESS` |
| H2 | HT bounded bit views and MEL/VLC/MagSgn primitives | H1 | `IN_PROGRESS` |
| H3 | HT cleanup, packets, and `.201` grayscale lossless path | H2 | `NOT_STARTED` |
| H4 | RGB/MCT, signed/planar samples, progression, and `.202` | H3 | `NOT_STARTED` |
| H5 | `.203` irreversible path and all-syntax external interoperability | H4 | `NOT_STARTED` |
| H6 | ImageIO/DICOM integration, hardening, and SPI release | H3-H5 | `NOT_STARTED` |

### H1: Shared foundation and HT boundaries

- [x] Verify that shared marker framing, immutable geometry, raster normalization,
  RCT/ICT, DWT math, and stateless progression coordinates satisfy HT needs.
- [ ] Establish the `jpeg2000.htj2k` package and a distinct `Htj2kFrameCodec`
  selected at the transfer-syntax adapter boundary.
- [x] Keep mutable classic packet/tag-tree state, MQ/EBCOT, PCRD, and HT entropy
  state in their respective packages; do not dispatch through a classic coder flag.
- [ ] Define bounded CAP, HT `Rsiz`/profile, tile-part, and marker policy. Reject
  unsupported RGN/PPM/PPT and JP2 wrapping with contextual `IIOException`.

**Exit gate:** focused structural tests verify shared contracts and HT-specific
profile rejection without changing classic `.90/.91` behavior.

The new parser checks SOC/SIZ, optional CAP, HT COD style, QCD shape, optional
TLM, ordered SOT/SOD tile-parts, EOC, and DICOM padding. The H1 gate remains
open: the transfer-syntax ImageIO adapter is not connected, and CAP/Ccap and
`Rsiz` profile policy still need validation against committed foreign HT vectors.

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

### H3: `.201` lossless vertical slice

- [ ] Implement HT packet contributions and tag trees, reversible 5/3 policy,
  one-layer 64x64 code blocks, and a one-full-image-tile encoder.
- [ ] Emit CAP and exact TLM/`Psot` accounting for resolution tile-parts; end the
  raw codestream at EOC without counting DICOM padding.
- [ ] Decode a foreign `.201` grayscale codestream and verify exact pixels; have
  a foreign HT decoder verify Java `.201` output.
- [ ] Check SIZ/descriptor agreement, CAP/profile constraints, packet bounds,
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

**Exit gate:** the lossless design matrix and both directions of external pixel
comparison pass for `.201/.202`.

### H5: `.203` irreversible path and interoperability

- [ ] Add HT 9/7 transform and quantization policy, mandatory three-component
  ICT, default `targetRatio=0` policy, and the specified quality-hint mapping.
- [ ] Enforce one layer and the supported write parameters; reject unsupported
  precision, sampling, and profile features.
- [ ] Run `.203` foreign encode to Java decode and Java encode to foreign decode
  with fixed recorded pixel tolerances.
- [ ] Commit HT fixtures with provenance, dimensions, precision, signedness,
  transfer syntax, parameters, and expected pixels or tolerance.

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

For each later status change, append the exact command or fixture, result, date,
and commit or working-tree reference here. Do not mark a phase complete until its
exit gate and the relevant release checks are evidenced.
