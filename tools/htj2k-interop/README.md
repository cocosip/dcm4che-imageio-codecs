# HTJ2K C# interoperability probe

This probe calls the public fo-dicom.Codecs C# transcoder. It never calls the
OpenJPH C++ API directly. The native DLL is required by fo-dicom.Codecs itself.
The codec source assembly and native DLL paths are supplied at build time:

```powershell
dotnet build tools/htj2k-interop/Interop.csproj -c Release '-p:FoDicomCodecsAssembly=<path-to-fo-dicom.Codecs.dll>' '-p:FoDicomNativeDll=<path-to-Dicom.Native.dll>'
javac -cp dcm4che-imageio-codecs-jpeg2000/target/classes tools/htj2k-interop/Htj2kExchange.java
```

The C# executable accepts `encode|decode syntax width height bitsAllocated
bitsStored components signed input output`. The Java tool accepts `syntax raw
codestream encode|decode|dump [width height bitsStored signed components]`; it creates a
deterministic 128x128 8-bit source frame by default. Compare the C# decoded
raw frame with the generated raw frame, masking unused high bits for 12-bit
samples.
The Java `decode` action enforces exact pixels for `.201/.202` and a maximum
absolute sample error of 12 for `.203`.

The local fo-dicom.Codecs 16-bit RGB encoder reads every component from the
same row start. Its own decoder does not reproduce the source color frame.
Use the C# decoder for Java 12-bit RGB streams and use grayscale for
bidirectional 12-bit checks until this reference encoder is corrected.
