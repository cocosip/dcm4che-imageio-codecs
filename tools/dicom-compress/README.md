# DICOM compression fixture tool

This independent C# command follows the `fo-dicom.PureCodecs.Tools` output
convention. It reads one DICOM file and saves compressed DICOM copies through
the public fo-dicom.Codecs C# transcoder API. The tool is outside the Maven
reactor and CI; Java tests may consume committed files generated with it.

```powershell
dotnet run --project tools/dicom-compress -- input.dcm
dotnet run --project tools/dicom-compress -- input.dcm --format htj2k_lossless --output-dir output
```

By default, output goes to `<input-name>_compressed` beside the input. Each
file is named `<input-name>_<format>.dcm`. The command reports the saved file's
byte count and SHA-256, and checks its transfer syntax and frame count after
reopening it. It attempts RLE, JPEG, JPEG-LS, JPEG 2000, and HTJ2K compression
formats unless `--format` selects one. Unsupported input/format combinations
are reported individually, so another target can still succeed.

This tool requires the `fo-dicom.Codecs` NuGet package and its runtime assets;
it does not call any native codec API directly. When promoting generated output
to a Java test fixture, record the source dimensions, precision, signedness,
photometric interpretation, target UID, source pixel hash, and expected pixel
tolerance alongside the fixture.
