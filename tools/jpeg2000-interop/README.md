# JPEG 2000 Java interoperability tool

This manual command uses the JPEG 2000 encoder and decoder in this Java
repository. The C# fo-dicom.Codecs reference utility that generates foreign
fixtures is in `tools/fo-dicom-fixtures/jpeg2000`; it is never run by Maven or
CI. Committed fixture tests consume saved files only.

After building `dcm4che-imageio-codecs-jpeg2000`, compile the Java command:

```powershell
javac -cp dcm4che-imageio-codecs-jpeg2000/target/classes tools/jpeg2000-interop/Jpeg2000Interop.java
java -cp "dcm4che-imageio-codecs-jpeg2000/target/classes;tools/jpeg2000-interop" Jpeg2000Interop 90 source.raw output.j2k encode 67 65 8 false 1
java -cp "dcm4che-imageio-codecs-jpeg2000/target/classes;tools/jpeg2000-interop" Jpeg2000Interop 90 source.raw foreign.j2k verify 67 65 8 false 1
```

The arguments are `<90|91> <source.raw> <codestream.j2k>
<encode|decode|verify> <width> <height> <precision> <signed> <components>
[tolerance]`. Raw 16-bit samples are little-endian and three-component samples
are interleaved. Lossless verification requires exact pixels; lossy
verification defaults to a maximum error of 12 sample codes.
