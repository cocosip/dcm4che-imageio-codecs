# JPEG-LS 编解码设计与开发基线

本文档是 `dcm4che-imageio-codecs-jpegls` 后续实现、测试和走查的完整基线，不按“第一版/第二版”
拆分功能。文档中的必做项必须一次性纳入总体开发范围。

目标：

- 完整实现 DICOM JPEG-LS Lossless 和 Near-Lossless Transfer Syntax；
- 覆盖这些 Transfer Syntax 使用到的 ISO/IEC 14495-1 / ITU-T T.87 Part 1 能力；
- 对齐当前 DICOM PS3.5 的 Pixel Data 约束；
- 与 `fo-dicom.PureCodecs` 和 `fo-dicom.Codecs`/CharLS 的有效互操作行为对齐；
- 不机械复制参考实现中过时、未接线或仅用于通用 `.jls` 文件的功能；
- 保持纯 Java，不依赖 JNI、CharLS 或其他 Native codec。

## 1. 依据与判定规则

### 1.1 优先级

1. ISO/IEC 14495-1 / ITU-T T.87：JPEG-LS Part 1 码流和算法；
2. 当前 DICOM PS3.5：JPEG-LS Transfer Syntax、Photometric Interpretation 和封装；
3. dcm4che 5.35.1：ImageIO SPI、`ImageDescriptor`、`Compressor`/`Decompressor`；
4. 当前 `fo-dicom.PureCodecs` 和 `fo-dicom.Codecs`/CharLS 行为；
5. 参考实现历史代码只作为兼容证据，不覆盖标准。

参考资料：

- ITU-T T.87：<https://www.itu.int/rec/T-REC-T.87-199806-I/en>；
- DICOM PS3.5 JPEG-LS：<https://dicom.nema.org/medical/dicom/current/output/chtml/part05/sect_8.2.3.html>；
- `docs/design.md`；
- `fo-dicom.PureCodecs/docs/design/jpegls-codec-design.md`；
- `fo-dicom.PureCodecs/src/fo-dicom.PureCodecs.JpegLs`；
- `fo-dicom.Codecs/Codec/DicomJpegLsCodec.cs`；
- `fo-dicom.Codecs/Native/Common/CharLS`。

### 1.2 参考实现不能直接定义范围

每个候选功能按四类判定：

| 分类 | 是否必做 | 判定 |
| --- | --- | --- |
| DICOM `.80/.81` 必需 | 是 | DICOM 规定或正常 DICOM 互操作需要 |
| Part 1 且可出现在 DICOM JPEG-LS frame | 是 | 即使本地参考版本未实现 |
| 非标准但现实 DICOM 兼容需要 | 解码必做，编码按必要性 | 例如历史 `mrfx` HP transform |
| 通用 `.jls` 文件 API、过时参数或未接线功能 | 否 | 不因参考库存在就纳入 DICOM codec |

参考实现的源码版本、适配层和公开参数必须分开记录。某个 API 存在不代表 dcm4che
适配层实际使用了它；某个本地快照没有实现某个 Part 1 能力，也不代表标准允许省略该能力。
本项目以标准和 DICOM 约束定范围，以参考实现的实际调用链定互操作测试，不以任一仓库的
当前缺口或过时扩展定范围。

### 1.3 fo-dicom.Codecs/CharLS 的实际使用矩阵

已检查的入口和源码：

- `fo-dicom.Codecs/Codec/DicomJpegLsCodec.cs`；
- `fo-dicom.Codecs/Native/Dicom.Imaging.Codec.JpegLS.cpp`；
- bundled CharLS `2.4.4` 的 `JpegLSEncode`、`JpegLSDecode` 和 marker reader/writer；
- `fo-dicom.PureCodecs/src/fo-dicom.PureCodecs.JpegLs`。

| 能力或参数 | fo-dicom.Codecs 适配层的事实 | PureCodecs 当前源码事实 | 本项目处理 |
| --- | --- | --- | --- |
| `JpegLSEncode`/`JpegLSDecode` | 通过 native wrapper 调用；每个 DICOM frame 一次 | 使用 managed frame/scan codec | 以相同 frame 语义做双向互操作 |
| width/height/bits/stride/components | 编码实际传入；解码由码流 header 填充 | 编解码器实际校验并使用 | 必须实现并与 descriptor 校验 |
| NEAR / `AllowedError` | `.80` 为 0；`.81` 传入参数 | `.80` 为 0；`.81` 传入参数 | 按 T.87/DICOM 范围校验，允许 `.81 + NEAR=0` |
| ILV None/Line/Sample | 编码按 SamplesPerPixel/PlanarConfiguration 推导；公开 `InterleaveMode` 不参与 native 调用 | `InterleaveMode` 参数未传入 frame codec，编码同样按 PixelData 推导 | core 支持三种 ILV；adapter 明确选择并测试，不复制未接线参数 |
| color transform | 编码把 `colorTransformation` 强制设为 None；`ColorTransform` 属性未用于编码 | `ColorTransform` 属性未传入 `EncodeFrame` | writer 固定 no-transform；reader 按标准/历史 `mrfx` 兼容规则处理 |
| preset coding parameters | legacy `custom` 结构保持零值，使用 CharLS 默认参数 | 使用标准默认值，支持码流中的 preset | 支持 LSE `0x01`，显式参数按标准校验 |
| restart interval | 当前 fo-dicom.Codecs 适配层没有公开 restart 参数，编码不主动配置 DRI；CharLS decoder 可消费码流中的 restart | PureCodecs core 有 restart 处理，公开 DICOM 参数未提供 | reader/writer 都实现 Part 1 DRI/RST；这是标准要求，不由参考 API 缺口决定 |
| mapping table | 当前 C# 适配层没有 mapping 参数；bundled CharLS 源码没有 mapping-table 实现 | 当前 PureCodecs 没有 mapping-table marker/selector 实现 | 仍必须实现 LSE `0x02/0x03` 和 `Tm`，用标准固定向量验收 |
| DNL / oversize dimension | bundled CharLS marker reader 注释明确标为不支持 DNL；适配层也没有 descriptor 之外的尺寸路径 | 当前 PureCodecs 没有 DNL/oversize writer/reader 路径 | DICOM writer 不产生；reader 按 Part 1 支持并在 adapter 边界拒绝不可表达尺寸 |
| APP8 `mrfx` HP transform | bundled CharLS marker reader 有 HP transform 识别/解码路径；fo-dicom.Codecs 适配层没有单独参数或 writer 声明 | PureCodecs 显式解析 `mrfx` 并做 inverse transform；公开 `ColorTransform` 仍未接入 encode | reader 做受控兼容解码；writer 不主动生成 HP transform |
| SPIFF/JFIF/APP/COM writer | legacy encode 要求 `jfif.version == 0`，没有写 SPIFF/JFIF；适配层没有 APP/COM API | writer 只输出 DICOM frame 所需 marker | 不提供通用文件 writer API；reader 安全跳过合法 metadata |
| ROI decode | CharLS 有 `JpegLsDecodeRect`，但 fo-dicom.Codecs 适配层不调用 | PureCodecs 读取完整 frame | ImageIO region/subsampling 在解码后的 adapter 层实现，不能宣称 CharLS ROI 已被使用 |
| YBR/planar 处理 | encode path 及现有 decode 分支包含 Planar→Interleaved、YBR_FULL/YBR_FULL_422→RGB 调用 | adapter 也保留这类兼容路径 | 仅作为历史兼容证据；与 JPEG-LS core 分离，标准 profile 和兼容路径必须分别验证 |

因此，“对齐 fo-dicom”在本计划中表示：对齐其实际 DICOM frame 输入输出、默认值、布局
转换和可验证的共同码流；不表示复制 CharLS 的全部文件格式 API，也不表示把未接线的
PureCodecs 属性继续暴露为 Java 功能。

## 2. Transfer Syntax 和共同参数

| Transfer Syntax | UID | 编码 | 解码 |
| --- | --- | --- | --- |
| JPEG-LS Lossless | `1.2.840.10008.1.2.4.80` | 必须 | 必须 |
| JPEG-LS Near-Lossless | `1.2.840.10008.1.2.4.81` | 必须 | 必须 |

- `.80` 每个 scan 强制 `NEAR=0`；
- `.81` 允许 `NEAR=0`，也允许 Near-Lossless；
- `0 <= NEAR <= min(255, MAXVAL / 2)`；
- dcm4che properties 对 `.81` 的默认值为 `nearLossless=2`，这是兼容策略，不是标准默认；
- 显式 `JpegLsImageWriteParam.allowedError` 覆盖该值；
- 支持 `.81` 时必须同时支持 `.80`；
- 每个 DICOM frame 是一个 JPEG-LS Interchange Format；frame 可以跨 fragment，fragment 不能包含多个 frame；
- 码流必须自包含解码参数，不能依赖外部 abbreviated-format table。

JPEG-LS Part 2/SOF57 不属于 `.80/.81`；当前两套参考适配层也没有该路径，因此不属于本模块范围。

## 3. 当前仓库和模块边界

`dcm4che-imageio-codecs-jpegls` 当前只有 POM 和注释状态的 SPI 文件，没有 Java 编解码实现。

建议结构：

```text
dcm4che-imageio-codecs-jpegls
├── JpegLsLosslessImageReader / Writer
├── JpegLsNearLosslessImageReader / Writer
├── JpegLsImageWriteParam
├── reader / writer SPI
├── JpegLsRasterFrames
└── internal
    ├── JpegLsMarkerReader / Writer
    ├── JpegLsFrameHeader / ScanHeader
    ├── JpegLsPresetCodingParameters
    ├── JpegLsMappingTable
    ├── JpegLsTraits / Predictor / Context
    ├── JpegLsGolombReader / Writer
    ├── JpegLsScanCodec
    └── JpegLsFrameCodec
```

边界：

- codestream core 不依赖 dcm4che；
- core 实现 DICOM frame 会使用的 Part 1 marker、scan、mapping 和算法；
- mapping entry 是不透明 byte vector，core 不猜测 palette/RGB 含义；
- DICOM/ImageIO adapter 负责 Raster、signed container、Photometric 和布局；
- dcm4che 负责多 frame/fragment 编排及压缩后的 Dataset 属性更新；
- writer 每次只输出一个完整 frame，不写 DICOM 偶数长度 padding。

## 4. 数据流

编码：

```text
RenderedImage + ImageDescriptor + JpegLsImageWriteParam
  -> DICOM profile / sample container 校验
  -> sample code 和布局归一化
  -> frame、LSE、scan 和可选 mapping table
  -> regular/run mode + Golomb + restart
  -> SOI + SOF55 + LSE/DRI + SOS + entropy + EOI
```

解码：

```text
ImageInputStream
  -> marker state machine
  -> frame、LSE、DRI 和 per-scan state
  -> regular/run mode decode
  -> mapping table lookup（Tm != 0）
  -> 必要的历史 HP inverse transform
  -> DICOM sample container / Raster / ImageReadParam
```

EOI 后不把 trailing garbage 当作另一个 frame。fragment padding 不属于 JPEG-LS frame。

## 5. Marker 和 frame 状态机

### 5.1 必须处理的 marker

| Marker | 编码 | 解码 | 说明 |
| --- | --- | --- | --- |
| SOI / EOI | 是 | 是 | 唯一且顺序正确 |
| SOF55 | 是 | 是 | Part 1 frame header |
| SOS | 是 | 是 | 单 scan 和多 scan |
| LSE `0x01` | 是 | 是 | preset parameters |
| LSE `0x02` | 是 | 是 | mapping table specification |
| LSE `0x03` | 是 | 是 | mapping table continuation |
| LSE `0x04` | 不由 DICOM writer 产生 | 是 | oversize dimensions；DICOM descriptor 会限制尺寸 |
| DNL | 不由 DICOM writer 产生 | 是 | 对合法外部码流兼容 |
| DRI / RST0-RST7 | 是 | 是 | 2/3/4 byte DRI，restart encode/decode |
| APP0-APP15 / COM | 否 | 是 | 安全跳过，APP8 另做兼容识别 |

DICOM writer 不需要提供通用 APP/COM/SPIFF 文件编辑 API。reader 必须安全跳过合法 metadata，不能把
metadata 的存在误判为码流错误。

Part 2 marker、未知 marker、重复 SOI/SOF、非法位置 EOI/RST 和不完整 marker 明确失败。

### 5.2 SOF55 校验

- precision 为 `2..16`；
- component count 非零；DICOM adapter 只接受 descriptor 允许的 SamplesPerPixel；
- component identifier 唯一；
- sampling factor 与 Part 1 和 DICOM profile 一致，DICOM writer 写 `0x11`；
- 保留的 quantization table selector 为 `0`；
- width/height 必须与 descriptor 一致；外部码流的零 dimension 只能按 LSE `0x04`/DNL 合法补全；
- dimension 和 sample count 使用溢出检查和资源上限。

### 5.3 SOS 和 per-scan state

每个 SOS 快照：

```text
component selectors
mapping table selector Tm per component
NEAR
ILV
point transform byte
effective preset parameters
effective restart interval
```

- scan component count 为 1 或完整 frame component count；
- 支持一个 interleaved scan，以及每 component 一个 `ILV=None` scan；
- selector 必须引用 SOF component，不能重复或遗漏；
- `Tm=0` 无 mapping；`Tm=1..255` 引用完整 table；
- ILV 只能 None/Line/Sample，单 component 必须 None；
- point transform 必须满足 T.87，当前 DICOM JPEG-LS 路径要求零；
- `.80` 的所有 scan NEAR 为零；`.81` 每 scan 独立校验；
- LSE/DRI 在 scan 之间更新，只对后续 scan 生效。

## 6. LSE、mapping table 和 DNL

### 6.1 Preset coding parameters `0x01`

支持 `MAXVAL/T1/T2/T3/RESET`。零值恢复标准默认值；threshold 按 effective MAXVAL/NEAR 计算。

```text
1 <= MAXVAL <= (1 << precision) - 1
NEAR + 1 <= T1 <= T2 <= T3 <= MAXVAL
3 <= RESET <= max(255, MAXVAL)
0 <= NEAR <= min(255, MAXVAL / 2)
```

### 6.2 Mapping table specification `0x02`

mapping table 属于 Part 1，完整编解码：

- `TID=1..255`；
- `Wt=1..255`，表示 entry byte width；
- table 包含 effective `MAXVAL + 1` 个 entry；
- entry 按 reconstructed sample `Rx=0..MAXVAL` 存储；
- decode 用 Rx 索引 table，输出原始 Wt bytes；
- 同 TID 的新 specification 替换旧 table；
- SOS 引用前 table 必须完整，entry 数与 effective MAXVAL 一致；
- 普通 DICOM encode 默认 `Tm=0`；显式 mapping encode 需要调用方提供 table 和 component selector。

```java
record JpegLsMappingTable(int tableId, int entryWidth, byte[] entries) {
    int entryCount();
    byte[] entry(int reconstructedSample);
}
```

### 6.3 Mapping table continuation `0x03`

- 单 segment 放不下 table 时自动拆分；
- `0x02` 后连续一个或多个 `0x03`；
- continuation 必须引用当前活动的 TID，并继承对应 specification 的 `Wt`；
- entry 按继承的 `Wt` 连续追加；无 preceding specification、插入其他 marker、TID 不匹配、
  entry 不整除/不足/过量均失败；
- decoder 跨 continuation 重组完整 table。

必须提供 palletised 固定向量和强制 continuation 的大 table 向量。

### 6.4 Oversize `0x04` 和 DNL

- reader 支持合法外部 Part 1 码流的 oversize dimensions 和 DNL；
- dcm4che `Rows/Columns` 为 16-bit US，超过 65535 或与 descriptor 不一致时 adapter 返回明确异常；
- DICOM writer 的输入无法表达 oversize dimensions，因此不产生 `0x04`/DNL；
- 不因为 writer 不产生就省略 reader 的标准兼容能力。

## 7. Coding core

1. traits：MAXVAL、T1/T2/T3、RESET、RANGE、LIMIT、qbpp；
2. bit stuffing、Golomb quotient/remainder/escape；
3. prediction error map/unmap；
4. regular mode；
5. run mode 和 run interruption；
6. Near-Lossless quantization/reconstruction；
7. restart state reset；
8. mapping table post-processing。

malformed input 不得造成死循环、无界分配或公共 API 泄漏数组越界异常。

## 8. Interleave 和 restart

### 8.1 Interleave

| ILV | 含义 | DICOM 使用 |
| --- | --- | --- |
| 0 | None | mono；多 scan color |
| 1 | Line | 多 component line interleave |
| 2 | Sample | interleaved color |

JPEG-LS ILV 与 DICOM Planar Configuration 是不同概念。adapter 根据 descriptor/Raster 组织 sample，
码流按 SOS 声明解释，不能相互替代。

### 8.2 Restart

- DRI payload 为 2/3/4 byte unsigned big-endian；
- interval 单位为完成的 image line；
- reader 和 writer 都实现 restart；
- writer 参数为 0 时不输出 DRI/RST；
- RST 顺序 `RST0..RST7..RST0`；
- restart 后重置 regular/run contexts、run index 和 line-neighbor state；
- 提前、缺失、截断、顺序错误、无 DRI 或尾部多余 RST 均失败。

## 9. APP8 `mrfx` 兼容边界

HP color transform 不是 DICOM `.80/.81` 必需参数，但现实 CharLS/PureCodecs 码流存在，因此：

- reader 精确识别 5-byte `mrfx + transform`；
- transform 0/1/2/3 为 none/HP1/HP2/HP3；
- reader 实现 HP1/HP2/HP3 inverse transform；
- 4/5 返回 recognized-but-unsupported，其他值为 invalid data；
- 普通 APP8、SPIFF APP8、其他 APPn 和 COM 安全跳过；
- 相同重复声明允许，冲突声明失败；
- writer 默认并固定输出 color transform none，不暴露未被 DICOM 路径需要的 HP encode 参数。

PureCodecs 当前虽暴露 `ColorTransform`，但 encode 未接入；CharLS 的 HP encode 是通用 API。两者都不构成
DICOM writer 必须输出 HP transform 的依据。

## 10. DICOM Pixel Data adapter

### 10.1 标准 DICOM profile

支持：

- MONOCHROME1、MONOCHROME2；
- PALETTE COLOR；
- RGB；
- YBR_FULL；
- unsigned 8/16-bit；
- signed MONOCHROME1/2；
- `BitsAllocated=8/16`，`BitsStored=2..BitsAllocated`，`HighBit=BitsStored-1`。

DICOM 标准不允许用于 JPEG-LS 的 YBR_FULL_422、YBR_PARTIAL_422、YBR_PARTIAL_420、YBR_RCT、YBR_ICT
在标准 adapter 路径中明确拒绝。

### 10.2 fo-dicom 历史兼容路径

PureCodecs 和 fo-dicom.Codecs 都有 YBR_FULL_422 转 RGB 逻辑，但这是 adapter 兼容策略，不是当前 DICOM
JPEG-LS profile。为读取或转码历史数据：

- 提供受控 compatibility decode/transcode path，将 interleaved YBR_FULL_422 转 RGB；
- 覆盖 odd-width frame length；
- planar YBR_FULL_422 明确失败；
- 输出 metadata 必须更新为 RGB、PlanarConfiguration 0；
- 标准 dcm4che `Compressor` 在 writer 前会拒绝 subsampled Photometric，不能私改 Dataset 绕过校验；
- 兼容路径和标准路径测试必须分开，不能声称历史输入符合当前 DICOM 标准。

YBR_FULL 和 planar RGB 的转换策略必须以最终码流、Dataset metadata 和外部互操作结果为准；不能仅因
PureCodecs 做了转换就无条件复制。

### 10.3 Signed sample

- core 始终处理 `0..MAXVAL` sample code；
- adapter 读取 `rawContainer & MAXVAL`，保留低 BitsStored 位二进制补码；
- Java 负 short 不直接进入 predictor/context；
- 解码写回低 BitsStored 位，需要 signed scalar 时按符号位 sign extension；
- unused high bits 和 embedded overlay 不参与编码；
- lossless 比较 bit pattern 和 signed interpretation；
- near-lossless 在 signed domain 比较，覆盖跨零和 min/max。

### 10.4 PALETTE COLOR 与 mapping table

DICOM PALETTE COLOR 的 Pixel Data 是 palette index，LUT 位于 Dataset。JPEG-LS mapping table 是独立的
码流机制。默认压缩 index 并保留 Dataset LUT，不自动将 DICOM LUT 转成 LSE mapping table。

## 11. ImageIO writer 参数与注册

```java
JpegLsImageWriteParam {
    int allowedError;             // .80=0；.81 默认 2
    JpegLsInterleaveMode interleaveMode;
    int restartInterval;
    List<JpegLsMappingTable> mappingTables;
    Map<Integer, Integer> componentMappingTableSelectors;
}
```

- 普通 DICOM encode 默认无 mapping table、无 restart、无 HP transform；
- 显式 mapping encode 的 source sample 是 table index，不能自动反推 table；
- selector 引用的 table 必须存在且完整；
- `.80` 不生成非零 allowedError；
- `.81` 按 effective MAXVAL 校验 allowedError；
- `nearLossless=2` property 必须能设置专用 param。

注册资源：

```text
dcm4che-imageio-codecs-jpegls/src/main/resources/
├── META-INF/services/javax.imageio.spi.ImageReaderSpi
├── META-INF/services/javax.imageio.spi.ImageWriterSpi
├── io/github/cocosip/dcm4che/imageio/codecs/jpegls/readers.properties
└── io/github/cocosip/dcm4che/imageio/codecs/jpegls/writers.properties
```

- 只覆盖 `.80/.81`；
- `patchJPEGLS` 为空；
- `.81` properties 为 `nearLossless=2`；
- SPI 可由 `ImageIO.scanForPlugins()` 发现；
- 不覆盖其他 Transfer Syntax。

## 12. Reader 行为

- 从 descriptor-backed stream 取得 `ImageDescriptor`；
- 校验 dimensions、precision、components 和 DICOM profile；
- 支持 source region、subsampling、destination offset、source/destination bands 和 caller destination；
- 支持 multi-scan 和 per-scan effective LSE/NEAR/DRI；
- 支持 mapping table output；如果 mapped output width/layout 无法放入目标 DICOM Raster，返回适配错误，
  不能丢弃 mapping table；
- APPn/COM/SPIFF 安全跳过，`mrfx` 按兼容规则处理；
- malformed codestream 包装为含具体原因的 `IIOException`。

## 13. 测试矩阵

### 13.1 Primitive/algorithm

- bit stuffing、Golomb、error map/unmap、traits/defaults；
- predictor、regular/run、run interruption；
- Near-Lossless 边界；restart reset；
- signed mask/sign extension；
- mapping lookup 和 segment split/join；
- HP inverse transform。

### 13.2 Codestream

- 2..16-bit；mono/RGB；None/Line/Sample；
- single scan 和每 component 一个 SOS；
- scan 间 LSE/DRI 更新；
- LSE `0x01/0x02/0x03/0x04` 和 DNL；
- palletised mapping fixed vector 和多 continuation table；
- DRI 2/3/4 byte、RST7->RST0 rollover；
- APPn/COM/SPIFF skip；`mrfx` 0/1/2/3；
- entropy/EOI/padding 边界。

### 13.3 Invalid stream

- marker 缺失/重复/乱序/截断；
- 非法 precision/dimension/component/sampling/selector；
- 非法 NEAR/ILV/scan set/point transform；
- mapping 缺失、未完成、TID/Wt 不一致、entry 不整除/不足/过量、continuation 不连续；
- LSE/DNL 非法覆盖 dimension；
- restart 提前/缺失/错误/多余；
- `mrfx` 冲突/非法；descriptor 不匹配；size overflow/resource limit。

### 13.4 DICOM

| 维度 | 必测值 |
| --- | --- |
| BitsAllocated/BitsStored | 8/(2..8)，16/(9..16) |
| PixelRepresentation | unsigned；signed mono |
| Standard Photometric | MONOCHROME1/2、PALETTE COLOR、RGB、YBR_FULL |
| Compatibility input | interleaved odd/even width YBR_FULL_422 |
| Frames | 单 frame、多 frame |
| Syntax | `.80`、`.81`，包括 `.81 + NEAR=0` |
| Interleave | mono None；color Line/Sample；multi-scan None |
| Container | byte order、unused high bits、16-bit min/max |

验证 lossless 精确值、near-lossless error、最终 Dataset metadata、NumberOfFrames、fragment/frame 边界、
Pixel Data 偶数长度、PALETTE LUT 保留和标准/兼容路径错误边界。

### 13.5 外部互操作

对 `fo-dicom.PureCodecs` 和 `fo-dicom.Codecs`/CharLS 分别验证共同有效范围：

1. 外部 encode -> Java decode -> 原始 pixels；
2. Java encode -> 外部 decode -> 原始 pixels；
3. lossless/near-lossless、8/16-bit、mono/RGB、Line/Sample、多 scan、restart decode；
4. YBR/planar/history compatibility 按实际 DICOM adapter 路径验证；
5. PM5644 JPEG-LS Lossless/Near-Lossless fixtures。

mapping table/DNL 若参考版本不能验证，使用标准固定向量、更新 CharLS 可用版本和 malformed vectors，
不能降级为拒绝。PureCodecs 的未接线参数不作为互操作验收依据。

## 14. 开发阶段

1. marker state machine、SOF/SOS、metadata skip；
2. LSE `0x01/0x02/0x03/0x04`、DNL、mapping vectors；
3. traits、bit/Golomb、regular mode；
4. run mode、Near-Lossless；
5. interleave、多 scan、per-scan state；
6. restart reader/writer；
7. DICOM Raster/container、signed、Photometric、PALETTE；
8. APP8 HP compatibility decode 和历史 YBR_FULL_422 path；
9. ImageIO params、reader/writer、SPI、dcm4che registration；
10. Compressor/Decompressor integration 和双参考互操作；
11. malformed/resource-limit/concurrent reuse 验证。

每阶段先写失败测试再实现。不能用自编自解替代外部互操作，不能通过放宽断言、屏蔽异常或私改
Dataset 掩盖不兼容。

## 15. 完成标准

- `.80/.81` encode/decode 完整；
- DICOM frame 使用的 Part 1 marker、LSE `0x01/0x02/0x03/0x04`、DNL、DRI/RST 完成；
- mapping table specification/continuation/selector 完整；
- regular/run、Near-Lossless、三种 ILV、多 scan/per-scan state 完成；
- signed mono 和完整 precision/container 矩阵完成；
- 标准 DICOM Photometric profile 和明确分离的历史兼容路径完成；
- 两个参考 codec 的有效共同能力双向互操作；
- mapping/DNL 由标准或更新参考向量验证；
- malformed input 明确失败，无越界、死循环或无界分配；
- 无 Native runtime dependency；注册只覆盖 `.80/.81`，`patchJPEGLS == null`；
- 文档、实现、测试和注册参数一致。
