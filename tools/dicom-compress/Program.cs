using System.Security.Cryptography;
using FellowOakDicom;
using FellowOakDicom.Imaging;
using FellowOakDicom.Imaging.Codec;
using FellowOakDicom.Imaging.NativeCodec;

var targets = new (string Suffix, DicomTransferSyntax Syntax)[]
{
    ("rle", DicomTransferSyntax.RLELossless),
    ("jpeg_baseline", DicomTransferSyntax.JPEGProcess1),
    ("jpeg_process2_4", DicomTransferSyntax.JPEGProcess2_4),
    ("jpeg_lossless_14", DicomTransferSyntax.JPEGProcess14),
    ("jpeg_lossless_sv1", DicomTransferSyntax.JPEGProcess14SV1),
    ("jpegls_lossless", DicomTransferSyntax.JPEGLSLossless),
    ("jpegls_near_lossless", DicomTransferSyntax.JPEGLSNearLossless),
    ("j2k_lossless", DicomTransferSyntax.JPEG2000Lossless),
    ("j2k_lossy", DicomTransferSyntax.JPEG2000Lossy),
    ("htj2k_lossless", DicomTransferSyntax.HTJ2KLossless),
    ("htj2k_lossless_rpcl", DicomTransferSyntax.HTJ2KLosslessRPCL),
    ("htj2k_lossy", DicomTransferSyntax.HTJ2K)
};

try
{
    if (args.Length == 0 || args[0] is "--help" or "-h")
    {
        PrintUsage(targets);
        return args.Length == 0 ? 2 : 0;
    }

    string? inputPath = null;
    string? outputDirectory = null;
    string? selectedFormat = null;
    for (var index = 0; index < args.Length; index++)
    {
        switch (args[index])
        {
            case "--output-dir" or "-o":
                outputDirectory = RequireValue(args, ref index);
                break;
            case "--format" or "-f":
                selectedFormat = RequireValue(args, ref index);
                break;
            default:
                if (args[index].StartsWith('-') || inputPath is not null)
                    throw new ArgumentException($"Unexpected argument: {args[index]}");
                inputPath = args[index];
                break;
        }
    }

    if (inputPath is null || !File.Exists(inputPath))
        throw new FileNotFoundException("Input DICOM file was not found", inputPath);
    var selected = selectedFormat is null
        ? targets
        : targets.Where(target => string.Equals(target.Suffix, selectedFormat,
            StringComparison.OrdinalIgnoreCase)).ToArray();
    if (selected.Length == 0)
        throw new ArgumentException($"Unknown format: {selectedFormat}");

    new DicomSetupBuilder()
        .RegisterServices(services => services.AddFellowOakDicom()
            .AddTranscoderManager<NativeTranscoderManager>())
        .Build();

    var file = DicomFile.Open(inputPath, FileReadOption.ReadAll);
    var sourceSyntax = file.Dataset.InternalTransferSyntax;
    var sourceFrames = DicomPixelData.Create(file.Dataset).NumberOfFrames;
    var basename = Path.GetFileNameWithoutExtension(inputPath);
    outputDirectory ??= Path.Combine(Path.GetDirectoryName(Path.GetFullPath(inputPath))!,
        basename + "_compressed");
    Directory.CreateDirectory(outputDirectory);

    var successes = 0;
    foreach (var target in selected)
    {
        var outputPath = Path.Combine(outputDirectory, $"{basename}_{target.Suffix}.dcm");
        try
        {
            var converted = sourceSyntax == target.Syntax
                ? file.Dataset.Clone()
                : new DicomTranscoder(sourceSyntax, target.Syntax).Transcode(file.Dataset);
            new DicomFile(converted).Save(outputPath);
            var saved = DicomFile.Open(outputPath, FileReadOption.ReadAll);
            if (saved.Dataset.InternalTransferSyntax != target.Syntax
                || DicomPixelData.Create(saved.Dataset).NumberOfFrames != sourceFrames)
                throw new InvalidDataException("Saved transfer syntax or frame count differs");
            var bytes = File.ReadAllBytes(outputPath);
            Console.WriteLine($"{target.Suffix}: {outputPath} ({bytes.Length} bytes, "
                + $"SHA-256 {Convert.ToHexString(SHA256.HashData(bytes))})");
            successes++;
        }
        catch (Exception error) when (error is not OperationCanceledException)
        {
            Console.Error.WriteLine($"{target.Suffix}: {error.Message}");
        }
    }
    Console.WriteLine($"{successes}/{selected.Length} formats saved");
    return successes == 0 ? 1 : 0;
}
catch (Exception error) when (error is not OperationCanceledException)
{
    Console.Error.WriteLine(error.Message);
    return 2;
}

static string RequireValue(string[] arguments, ref int index)
{
    if (++index >= arguments.Length || arguments[index].StartsWith('-'))
        throw new ArgumentException("Option requires a value");
    return arguments[index];
}

static void PrintUsage((string Suffix, DicomTransferSyntax Syntax)[] targets)
{
    Console.WriteLine("Usage: dotnet run --project tools/dicom-compress -- <input.dcm> "
        + "[--output-dir <directory>] [--format <suffix>]");
    Console.WriteLine("Formats: " + string.Join(", ", targets.Select(target => target.Suffix)));
}
