# DICOM compression tool

This Java tool follows the output layout of `fo-dicom.PureCodecs.Tools` and
compresses DICOM files with this repository's codec implementations. It reads
one input file and writes one DICOM copy for each available target syntax to
`<input-name>_compressed` beside the source. Use `-o` for another directory or
`-f` to select a single format. Each output is reopened to check its transfer
syntax and frame count before it replaces the destination file.

```powershell
mvn install -q
mvn -pl dcm4che-imageio-codecs-tools exec:java '-Dexec.args=input.dcm -f j2k_lossless'
```

Target suffixes: `rle`, `jpeg_baseline`, `jpeg_process2_4`,
`jpeg_lossless_14`, `jpeg_lossless_sv1`, `jpegls_lossless`,
`jpegls_near_lossless`, `j2k_lossless`, `j2k_lossy`, `htj2k_lossless`,
`htj2k_lossless_rpcl`, `htj2k_lossy`.

HTJ2K targets are listed for the final codec release. Their ImageIO providers
are currently unregistered while the HTJ2K release matrix remains open, so
these targets report an unavailable encoder until that gate passes. Other
unsupported input/target combinations fail individually without interrupting
the remaining formats. C# is not used by this tool or its Java tests.
