using System.Security.Cryptography;
using FellowOakDicom;
using FellowOakDicom.Imaging;
using FellowOakDicom.Imaging.Codec;
using FellowOakDicom.Imaging.NativeCodec;
using FellowOakDicom.IO.Buffer;

if (args.Length < 2 || args.Length > 5)
{
    Console.Error.WriteLine("Usage: Jpeg2000Interop synthesize <output.dcm> | decode <input.dcm> <output.raw> | encode <source.dcm> <frame.j2k> <90|91> [reference.raw] | verify <source.dcm> <frame.j2k> <90|91>");
    return 2;
}

new DicomSetupBuilder()
    .RegisterServices(services => services.AddFellowOakDicom()
        .AddTranscoderManager<NativeTranscoderManager>())
    .Build();

if (args[0] == "synthesize" && args.Length == 2)
{
    const int width = 67;
    const int height = 65;
    var dataset = new DicomDataset(DicomTransferSyntax.ExplicitVRLittleEndian)
    {
        { DicomTag.SOPClassUID, DicomUID.SecondaryCaptureImageStorage },
        { DicomTag.SOPInstanceUID, "2.25.179030392800000000000000000000001" },
        { DicomTag.Rows, (ushort)height },
        { DicomTag.Columns, (ushort)width },
        { DicomTag.BitsAllocated, (ushort)8 },
        { DicomTag.BitsStored, (ushort)8 },
        { DicomTag.HighBit, (ushort)7 },
        { DicomTag.PixelRepresentation, (ushort)0 },
        { DicomTag.SamplesPerPixel, (ushort)1 },
        { DicomTag.PhotometricInterpretation, "MONOCHROME2" }
    };
    var frame = new byte[width * height];
    for (var y = 0; y < height; y++)
    {
        for (var x = 0; x < width; x++)
        {
            frame[y * width + x] = (byte)((x * 31 + y * 73 + (x ^ y) * 7) & 255);
        }
    }
    DicomPixelData.Create(dataset, true).AddFrame(new MemoryByteBuffer(frame));
    new DicomFile(dataset).Save(args[1]);
    Console.WriteLine($"pixels={frame.Length} sha256={Convert.ToHexString(SHA256.HashData(frame))}");
    return 0;
}

if (args[0] == "decode" && args.Length == 3)
{
    var file = DicomFile.Open(args[1], FileReadOption.ReadAll);
    var raw = new DicomTranscoder(file.Dataset.InternalTransferSyntax,
        DicomTransferSyntax.ExplicitVRLittleEndian).Transcode(file.Dataset);
    var frame = DicomPixelData.Create(raw).GetFrame(0).Data;
    File.WriteAllBytes(args[2], frame);
    Console.WriteLine($"pixels={frame.Length} sha256={Convert.ToHexString(SHA256.HashData(frame))}");
    return 0;
}

if (args[0] == "verify" && args.Length == 4)
{
    var source = DicomFile.Open(args[1], FileReadOption.ReadAll).Dataset;
    var syntax = args[3] == "90" ? DicomTransferSyntax.JPEG2000Lossless
        : args[3] == "91" ? DicomTransferSyntax.JPEG2000Lossy
        : throw new ArgumentException("Transfer syntax must be 90 or 91");
    var compressed = new DicomDataset(syntax);
    foreach (var item in source)
    {
        if (item.Tag != DicomTag.PixelData)
        {
            compressed.Add(item);
        }
    }
    DicomPixelData.Create(compressed, true)
        .AddFrame(new MemoryByteBuffer(File.ReadAllBytes(args[2])));
    var raw = new DicomTranscoder(syntax,
        DicomTransferSyntax.ExplicitVRLittleEndian).Transcode(compressed);
    var expected = DicomPixelData.Create(source).GetFrame(0).Data;
    var actual = DicomPixelData.Create(raw).GetFrame(0).Data;
    if (expected.Length != actual.Length)
    {
        throw new InvalidDataException($"Pixel lengths differ: {expected.Length} vs {actual.Length}");
    }
    var maximumError = 0;
    for (var i = 0; i < expected.Length; i++)
    {
        maximumError = Math.Max(maximumError, Math.Abs(expected[i] - actual[i]));
    }
    Console.WriteLine($"pixels={actual.Length} maxError={maximumError} "
        + $"sha256={Convert.ToHexString(SHA256.HashData(actual))}");
    return 0;
}

if (args[0] == "encode" && (args.Length == 4 || args.Length == 5))
{
    var source = DicomFile.Open(args[1], FileReadOption.ReadAll).Dataset;
    var syntax = args[3] == "90" ? DicomTransferSyntax.JPEG2000Lossless
        : args[3] == "91" ? DicomTransferSyntax.JPEG2000Lossy
        : throw new ArgumentException("Transfer syntax must be 90 or 91");
    var compressed = new DicomTranscoder(source.InternalTransferSyntax, syntax).Transcode(source);
    var frame = DicomPixelData.Create(compressed).GetFrame(0).Data;
    var length = frame.Length;
    if (length >= 3 && frame[length - 1] == 0 && frame[length - 3] == 0xff
        && frame[length - 2] == 0xd9)
    {
        length--;
    }
    if (length < 2 || frame[length - 2] != 0xff || frame[length - 1] != 0xd9)
    {
        throw new InvalidDataException("Foreign JPEG 2000 frame lacks EOC");
    }
    File.WriteAllBytes(args[2], frame.AsSpan(0, length).ToArray());
    if (args.Length == 5)
    {
        var decoded = new DicomTranscoder(syntax,
            DicomTransferSyntax.ExplicitVRLittleEndian).Transcode(compressed);
        File.WriteAllBytes(args[4], DicomPixelData.Create(decoded).GetFrame(0).Data);
    }
    Console.WriteLine($"bytes={length} sha256={Convert.ToHexString(SHA256.HashData(frame.AsSpan(0, length)))}");
    return 0;
}

throw new ArgumentException("Invalid JPEG 2000 interoperability command");
