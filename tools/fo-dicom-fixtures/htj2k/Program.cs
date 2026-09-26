using System;
using System.IO;
using FellowOakDicom;
using FellowOakDicom.Imaging;
using FellowOakDicom.Imaging.Codec;
using FellowOakDicom.Imaging.NativeCodec;
using FellowOakDicom.IO.Buffer;

if (args.Length != 10) {
    Console.Error.WriteLine("usage: encode|decode syntax width height bitsAllocated bitsStored components signed input output");
    return 2;
}

new DicomSetupBuilder()
    .RegisterServices(services => services.AddFellowOakDicom()
        .AddTranscoderManager<NativeTranscoderManager>())
    .SkipValidation()
    .Build();

var encode = args[0] == "encode";
var syntax = args[1] switch {
    "201" => DicomTransferSyntax.HTJ2KLossless,
    "202" => DicomTransferSyntax.HTJ2KLosslessRPCL,
    "203" => DicomTransferSyntax.HTJ2K,
    _ => throw new ArgumentException("Unknown syntax")
};
var width = int.Parse(args[2]);
var height = int.Parse(args[3]);
var bitsAllocated = int.Parse(args[4]);
var bitsStored = int.Parse(args[5]);
var components = int.Parse(args[6]);
var signed = int.Parse(args[7]);
var source = encode ? DicomTransferSyntax.ExplicitVRLittleEndian : syntax;
var destination = encode ? syntax : DicomTransferSyntax.ExplicitVRLittleEndian;
var dataset = new DicomDataset(source);
dataset.AddOrUpdate(DicomVR.US, DicomTag.Rows, (ushort)height);
dataset.AddOrUpdate(DicomVR.US, DicomTag.Columns, (ushort)width);
dataset.AddOrUpdate(DicomVR.US, DicomTag.BitsAllocated, (ushort)bitsAllocated);
dataset.AddOrUpdate(DicomVR.US, DicomTag.BitsStored, (ushort)bitsStored);
dataset.AddOrUpdate(DicomVR.US, DicomTag.HighBit, (ushort)(bitsStored - 1));
dataset.AddOrUpdate(DicomVR.US, DicomTag.PixelRepresentation, (ushort)signed);
dataset.AddOrUpdate(DicomVR.US, DicomTag.SamplesPerPixel, (ushort)components);
dataset.AddOrUpdate(DicomVR.CS, DicomTag.PhotometricInterpretation,
    components == 1 ? "MONOCHROME2" : encode ? "RGB" : args[1] == "203" ? "YBR_ICT" : "YBR_RCT");
if (components == 3)
    dataset.AddOrUpdate(DicomVR.US, DicomTag.PlanarConfiguration, (ushort)0);

var pixels = DicomPixelData.Create(dataset, true);
pixels.AddFrame(new MemoryByteBuffer(File.ReadAllBytes(args[8])));
var converted = dataset.Clone(destination);
var result = DicomPixelData.Create(converted).GetFrame(0).Data;
File.WriteAllBytes(args[9], result);
Console.WriteLine($"{args[0]} {args[1]}: {result.Length} bytes");
return 0;
