# JPEG 2000 DICOM fixtures

These five DICOM files come from `fo-dicom.PureCodecs` revision
`1dab2d8f4188f731e1d7387c6a6fd9dc495f48eb`, directory
`tests/fo-dicom.PureCodecs.Tests/TestSupport/Fixtures/Regression/Jpeg2000Baseline`.
They exercise the full DICOM parsing and dcm4che decompression path, in
addition to the raw codestream fixtures in the parent directory. Each contains
one frame. Their `PatientName` and `PatientID` fields contain test or redacted
identifiers.

| File | Bytes | Transfer syntax | Image | SHA-256 |
| --- | ---: | --- | --- | --- |
| `fo_dicom_codecs_j2k_lossless.dcm` | 175006 | `.90` | 888x459, signed 16-bit MONOCHROME2 | `3B46554F8D76CC0F3922965E8158A2DC87EE91B395F310331D0F03E49F0B7E56` |
| `fo_dicom_codecs_j2k_lossy.dcm` | 42596 | `.91` | 888x459, signed 16-bit MONOCHROME2 | `B67C474ACB0F2C1F0513558A09262BE3FBEFD5453039F9BE1DDBE4F50D873F4C` |
| `fo_dicom_codecs_local2_j2k_lossless.dcm` | 117324 | `.90` | 552x386, signed 16-bit MONOCHROME2 | `98453A23960AEF0C3B44A0ED9BE80C262FA87B16F52B79A989DCCE9519E9DC60` |
| `fo_dicom_codecs_unit8_j2k_lossy.dcm` | 40422 | `.91` | 512x512, unsigned 8-bit YBR_ICT | `B901E0772B3A5F479C0C4384C6AE9DBE322A2AF6744A60ED66870A32DC3DC2F4` |
| `purecodecs_unit8_j2k_lossy.dcm` | 40378 | `.91` | 512x512, unsigned 8-bit YBR_ICT | `892210E45B2F6965C98F58E8A411CA963C967C1952B9278C5DC8754854C9984D` |

Independent pixel baselines were produced with `fo-dicom.Codecs` 5.16.7 in a
separate .NET 10 process, using `dotnet tools/fo-dicom-fixtures/jpeg2000/bin/Debug/net10.0/Jpeg2000Interop.dll
decode <input.dcm> <output.raw>` from this repository's root:

| DICOM file | Native decoded frame SHA-256 | Java assertion |
| --- | --- | --- |
| `fo_dicom_codecs_j2k_lossless.dcm` | `71C16DD9D66487FDC5DC25A7278C70E9F6CE992F29D5910BD73509A4585C2B99` | Exact 815184 bytes |
| `fo_dicom_codecs_local2_j2k_lossless.dcm` | `2B40E05483B31F85A81155AA3E90ADD2E4185CEE4677209BE48B414468871A01` | Exact 426144 bytes |
| `fo_dicom_codecs_j2k_lossy.dcm` | `C2FB1CE81F58FCAF25DDF81B4861D9F42D86684657F117A504030EB2A7448D23` | Maximum signed-sample error 16 |
| Both `unit8_j2k_lossy` files | `5B37F5C8582C6207C7DE711FFAF215828F81DB1561FC12A107AEFE9B1EAF03FE` | Maximum byte-sample error 6 |

The signed 16-bit lossy native result is stored as
`fo_dicom_codecs_j2k_lossy_native.raw` (815184 bytes, SHA-256
`C2FB1CE81F58FCAF25DDF81B4861D9F42D86684657F117A504030EB2A7448D23`).
The 8-bit native result is the parent directory's
`fo_dicom_codecs_unit8_lossy_native.raw.gz`. The external decoder is used
only to prepare fixed acceptance data; Maven tests use the pure Java codec.
