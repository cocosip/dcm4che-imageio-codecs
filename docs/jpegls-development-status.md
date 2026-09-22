# JPEG-LS 编解码器开发状态

本文档记录 `dcm4che-imageio-codecs-jpegls` 在 **2026-09-23** 的实现状态，供后续继续开发时使用。

- 完整功能范围仍以 [`jpegls-development-plan.md`](jpegls-development-plan.md) 为准；
- 本文档是当前工作区快照，不替代标准、设计基线或最终验收；
- 当前实现尚未提交，JPEG-LS 的大部分 Java 源码、测试和 properties 资源仍是未跟踪文件；
- 按当前开发要求，暂不执行 `fo-dicom.PureCodecs`、`fo-dicom.Codecs`/CharLS 等外部类库互操作测试。

## 1. 当前结论

JPEG-LS 核心算法、基本码流、三种交织模式、基础多 scan、restart、ImageIO 读写器和
dcm4che 注册主链路已经实现。当前代码可以完成常见的单色/RGB、8/16-bit、Lossless 和
Near-Lossless 自编自解。

但开发计划中的完整范围尚未完成，主要缺口是：

1. 多 scan 的 per-scan LSE/NEAR/DRI/mapping 状态；
2. mapping table 的实际映射输出和 writer API；
3. 完整 `ImageReadParam` 行为；
4. DICOM Pixel Data profile、signed/BitsStored/Photometric/多帧集成；
5. historical YBR_FULL_422 compatibility path；
6. malformed/resource-limit/concurrent reuse 完整矩阵；
7. 真正的零编译 warning 验收。

因此当前代码是可运行的中间实现，不能按 `jpegls-development-plan.md` 第 15 节认定为全部完成。

## 2. 当前验证基线

2026-09-23 执行：

```powershell
.\mvnw.cmd -pl dcm4che-imageio-codecs-jpegls -am test
```

结果：

| 模块 | 测试数 | Failure | Error | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `dcm4che-imageio-codecs-core` | 14 | 0 | 0 | 0 |
| `dcm4che-imageio-codecs-jpegls` | 60 | 0 | 0 | 0 |

Reactor 结果为 `BUILD SUCCESS`。

### 2.1 Warning 状态

当前还不能宣称“编译不存在警告”：

- 根 `pom.xml` 给 `maven-compiler-plugin 3.15.0` 配置了 `<showWarnings>false</showWarnings>`；
- 当前使用 JDK `21.0.12.1`，项目以 Java 8 release 编译；
- 该配置隐藏了 javac warning，不能证明 warning 已从根因上消除；
- 测试输出中仍有 SLF4J “No providers were found” 运行时 warning。

后续验收应移除 warning 屏蔽，在 clean compile/test 下确认编译器输出无 warning，并决定是为测试增加
合适的 SLF4J test provider，还是通过已有测试日志配置消除运行时 warning。不能把关闭 warning 输出当成
质量门通过。

## 3. 已完成并有测试覆盖的功能

### 3.1 Transfer Syntax、ImageIO 和注册

- JPEG-LS Lossless：`1.2.840.10008.1.2.4.80`；
- JPEG-LS Near-Lossless：`1.2.840.10008.1.2.4.81`；
- Lossless/Near-Lossless ImageReader、ImageWriter 和四个 SPI；
- ImageIO service 文件；
- dcm4che `readers.properties`、`writers.properties` 注册；
- `JpegLsCodec.register()`；
- `.81` 默认 `allowedError=2`；
- 基础单色和 RGB ImageIO round-trip；
- dcm4che `ImageReaderFactory`/`ImageWriterFactory` 注册验证。

### 3.2 Frame 和 marker 解析

- SOI、SOF55、SOS、EOI；
- APP0-APP15 和 COM 的安全跳过；
- SOF55 precision、dimension、component、sampling、selector 等基础校验；
- SOS component、ILV、NEAR、point transform 基础校验；
- 重复 SOI/SOF、非法 marker 顺序和 trailing data 的部分拒绝路径；
- JPEG-LS `FF xx` bit-stuffing 边界：`xx` 最高位为 0 时按 entropy 处理；
- DRI 2、3、4-byte unsigned big-endian 解析；
- DNL 2、3、4-byte dimension 基础解析；
- LSE `0x04` oversize dimensions 基础解析；
- APP8 `mrfx` 声明解析和冲突校验。

### 3.3 LSE preset 和 mapping marker 结构

- LSE `0x01` preset coding parameters；
- `MAXVAL/T1/T2/T3/RESET` 解析、默认值解析和参数校验；
- effective threshold 进入 coding context；
- LSE `0x02` mapping table specification 解析；
- LSE `0x03` continuation 解析；
- 大表自动分段和 continuation 重组；
- TID、Wt、entry alignment、entry count、continuation 顺序和匹配校验；
- mapping table entry 的按 index 读取能力。

注意：这里只完成了 mapping table 的结构层，尚未完成实际 scan 映射语义，见第 4.2 节。

### 3.4 Coding core

- MED predictor；
- gradient quantization；
- `MAXVAL`、`RANGE`、`QBPP`、`LIMIT`、threshold、RESET traits；
- regular mode context；
- run mode 和 run interruption；
- prediction error map/unmap；
- Golomb/Rice quotient、remainder 和 limited escape；
- Near-Lossless quantization/reconstruction；
- JPEG-LS bit writer/reader 和 bit stuffing；
- 自定义 T1/T2/T3/RESET 进入 context；
- run context 使用 effective RESET，不再固定为 64。

### 3.5 Frame、scan 和 interleave

- 8-bit 和 16-bit frame round-trip；
- unsigned sample code round-trip；
- signed 16-bit container 的低位 bit pattern 基础 round-trip；
- 单色 Lossless/Near-Lossless；
- RGB Lossless；
- ILV None；
- ILV Line；
- ILV Sample；
- RGB 三个独立 SOS 的基础 multi-scan；
- scan component 重复/遗漏的基础校验。

### 3.6 Restart

- writer 输出 DRI 和 RST marker；
- decoder 消费 DRI 和 RST marker；
- restart interval 按 image line 处理；
- RST0 到 RST7 再回到 RST0；
- 错误 RST 顺序拒绝；
- restart 后通过独立 interval codec 重建 regular/run context、run index 和相邻行状态；
- restart marker 数量与 DRI 的基础一致性校验。

### 3.7 DNL 和 HP transform

- SOF height 为零时，在 entropy 后、EOI 前通过 DNL 补全高度的基础解码；
- `mrfx` transform 0/1/2/3 识别；
- HP1/HP2/HP3 inverse transform；
- transform 4/5 返回 recognized-but-unsupported；
- 其他 transform 值作为 invalid data；
- 重复相同 transform 允许，冲突声明拒绝。

### 3.8 Writer 参数

`JpegLsImageWriteParam` 已有：

- `allowedError`；
- `interleaveMode`；
- `restartInterval`。

Lossless writer 会强制 `allowedError=0`，Near-Lossless writer 支持显式覆盖默认值。

## 4. 部分完成的功能

### 4.1 Multi-scan 和 per-scan state

已完成：

- 一个 interleaved scan；
- RGB 每分量一个 ILV=None scan；
- scan 集合的重复和遗漏基础校验。

未完成：

- scan 之间解析和应用 LSE `0x01/0x02/0x03`；
- scan 之间解析和应用 DRI；
- scan 之间跳过 APPn/COM，并继续识别 APP8 `mrfx`；
- 每个 SOS 独立快照 effective preset、NEAR、DRI 和 mapping selector；
- 每个 scan 使用自己的 `JpegLsTraits`；
- multi-scan 不同 NEAR 的返回模型。

当前 `JpegLsFrameCodec.decode()` 在读到第一个 SOS 后，scan 数据之间主要只识别下一个 SOS、DNL、RST
和 EOI。traits 由第一个 scan 的 header 创建，并被所有 scan 共用。因此“基础 multi-scan 可用”，但开发
计划要求的 per-scan state 尚未实现。

### 4.2 Mapping table

已完成 marker 解析、分段、重组和完整性校验，但还缺少：

- decode 时根据 SOS 中的 `Tm` 对 reconstructed sample `Rx` 查表；
- 输出每个 entry 的 `Wt` bytes；
- mapped output 的数据模型；
- mapped width/layout 无法放入目标 DICOM Raster 时的明确 adapter error；
- writer 输出 mapping table marker；
- writer 为 component 写入 mapping selector；
- public `JpegLsMappingTable` API；
- `JpegLsImageWriteParam.mappingTables`；
- `componentMappingTableSelectors`；
- palette-style 固定码流端到端测试；
- 强制多 continuation 的 frame 端到端测试。

当前 `JpegLsMappingTable` 仍是 internal package-private 类，不能视为计划中公开 writer 参数已经实现。

### 4.3 DNL 和 oversize dimensions

已完成基础 header/frame 解析，但仍缺少：

- DNL 重复、冲突、覆盖非零高度等非法场景；
- DNL 出现位置的完整状态机校验；
- 真正流式输入边界测试；
- oversize dimension 与 DNL 的组合规则；
- 超过 DICOM Rows/Columns 表达范围时的 adapter error；
- descriptor dimension mismatch 的完整验证。

### 4.4 Restart

主路径已实现，但测试矩阵仍不完整：

- RST 提前、缺失、截断和尾部多余；
- 无 DRI 的 RST；
- 行边界和最后一个 interval 的更多尺寸组合；
- RGB Line/Sample interleave 与 restart；
- multi-scan 每个 scan 独立 DRI 更新；
- 3/4-byte 大 restart interval 的实际 frame 测试；
- `JpegLsImageWriteParam.restartInterval` 是 Java `int`，而 decoder 支持 unsigned 32-bit DRI，公开 API
  可表达范围仍需明确。

### 4.5 DICOM Raster/container

已有基础 Raster 转 sample 和 ImageIO round-trip，但完整 DICOM profile 尚未完成验证：

- `BitsAllocated=8/16` 与全部 `BitsStored` 组合；
- unused high bits 和 embedded overlay 排除；
- signed MONOCHROME1/2 的完整 mask/sign-extension；
- signed Near-Lossless 在 signed domain 的误差，包括跨零和 min/max；
- MONOCHROME1、MONOCHROME2；
- PALETTE COLOR 及 LUT 保留；
- RGB planar/interleaved；
- YBR_FULL；
- 对 YBR_FULL_422、YBR_PARTIAL_422、YBR_PARTIAL_420、YBR_RCT、YBR_ICT 的标准路径明确拒绝；
- descriptor 与码流 dimensions、precision、components 的完整一致性验证。

### 4.6 APP8 HP compatibility

reader 的 inverse transform 已实现，但还需要：

- 用完整外部风格 frame 验证 transform 1/2/3，而不仅是 transform 函数和 header 组合测试；
- APP8/SPIFF/普通 APP8 的更完整区分；
- transform 与 precision/component profile 的 malformed 矩阵；
- 明确验证 writer 永远不主动输出 HP transform。

## 5. 尚未完成的功能

### 5.1 完整 ImageReadParam

`JpegLsImageReader` 当前尚未实现计划要求的：

- source region；
- X/Y subsampling；
- subsampling offset；
- destination offset；
- source bands；
- destination bands；
- caller-provided destination image 的完整校验和写入。

这些行为应在完整 frame 解码后的 adapter 层实现，可参考现有 JPEG 模块中的
`JpegRasterFrames.readRegion`、`destinationImage`、bands 和 destination validation。

### 5.2 Historical YBR_FULL_422 compatibility path

尚未实现独立的历史兼容 decode/transcode 路径：

- interleaved YBR_FULL_422 到 RGB；
- odd-width frame length；
- planar YBR_FULL_422 明确失败；
- 输出 metadata 更新为 RGB、PlanarConfiguration 0；
- 标准 profile 和兼容路径的分离测试。

### 5.3 DICOM Compressor/Decompressor 集成

尚未添加类似 `RleDicomIntegrationTest` 的 JPEG-LS DICOM 集成测试，缺少：

- `.80`、`.81`；
- `.81 + NEAR=0`；
- 单色、RGB；
- unsigned/signed 8/16-bit；
- BitsStored matrix；
- 单 frame、多 frame；
- fragment/frame 边界；
- Pixel Data 偶数长度；
- NumberOfFrames 和最终 Dataset metadata；
- interleave/restart writer 参数的 DICOM 调用链验证。

### 5.4 SPI 自动发现

service 文件和显式注册已有测试，但还没有验证 `ImageIO.scanForPlugins()` 能在干净注册表环境中自动发现
全部 `.80/.81` reader/writer SPI，且不会覆盖其他 Transfer Syntax。

### 5.5 Malformed、资源限制和并发

现有测试覆盖部分非法 marker/header/mapping/restart，但尚缺完整矩阵：

- marker 缺失、重复、乱序、截断的组合；
- entropy 截断和非法填充边界；
- dimension/sample count 乘法溢出；
- 最大允许 frame 分配和明确资源上限；
- mapping table 无界分配防护；
- 不应泄漏 `ArrayIndexOutOfBoundsException`、`NegativeArraySizeException` 等内部异常；
- reader/writer 实例重复使用；
- 并发使用或并发创建实例；
- malformed 输入不得死循环。

### 5.6 文档和最终一致性审计

尚未完成：

- public API Javadoc；
- 实现、properties、默认参数和开发计划的最终逐项核对；
- `patchJPEGLS == null` 的明确测试；
- 全仓 clean test；
- 打包产物中 SPI/properties 的检查；
- 无 Native runtime dependency 的产物级检查。

## 6. 按要求暂缓的项目

以下项目属于开发计划最终范围，但本阶段按要求暂缓，不应删除对应验收项：

- `fo-dicom.PureCodecs` encode/decode 双向互操作；
- `fo-dicom.Codecs`/CharLS encode/decode 双向互操作；
- PM5644 JPEG-LS Lossless/Near-Lossless fixtures；
- 外部 codec 对 Line/Sample/multi-scan/restart 的共同能力验证。

mapping table 和 DNL 即使外部参考 codec 无法验证，后续仍必须通过标准固定向量、malformed vectors 或
支持相应能力的更新参考实现验收，不能降级为直接拒绝。

## 7. 当前测试清单

| 测试类 | 当前测试数 | 主要覆盖 |
| --- | ---: | --- |
| `JpegLsCodingPrimitiveTest` | 10 | predictor、traits、bit/Golomb、context 等 primitive |
| `JpegLsColorTransformTest` | 2 | HP inverse transform 和 transform 校验 |
| `JpegLsDimensionsTest` | 4 | oversize/DNL dimension 解析 |
| `JpegLsFrameCodecTest` | 8 | frame round-trip、interleave、multi-scan、restart、DNL |
| `JpegLsHeaderReaderTest` | 15 | marker、SOF/SOS、APP/COM、DRI、mrfx 等 header 行为 |
| `JpegLsMappingTableTest` | 7 | mapping segment parse/split/join/invalid cases |
| `JpegLsPresetCodingParametersTest` | 5 | LSE preset/default/validation |
| `JpegLsScanCodecTest` | 4 | mono/RGB scan、Near-Lossless 和 signed bit pattern |
| `JpegLsImageIoTest` | 5 | ImageIO mono/RGB round-trip 和 dcm4che registration |

当前测试总数为 60。该数字仅说明现有测试通过，不表示计划中缺失的测试矩阵已经完成。

## 8. 关键源码位置

### 8.1 Core

- `internal/JpegLsFrameCodec.java`：frame encode/decode、multi-scan、restart；
- `internal/JpegLsScanCodec.java`：regular/run scan coding；
- `internal/JpegLsHeaderReader.java`：首个 SOS 前的 marker state；
- `internal/JpegLsHeader.java`：当前首 scan effective header；
- `internal/JpegLsFrameHeader.java`：SOF55、oversize、DNL dimensions；
- `internal/JpegLsScanHeader.java`：SOS 参数；
- `internal/JpegLsPresetCodingParameters.java`：LSE preset；
- `internal/JpegLsMappingTable*.java`：mapping table 结构、parser 和分段；
- `internal/JpegLsTraits.java`：JPEG-LS traits；
- `internal/JpegLsContextModel.java`、`JpegLsRegularContextState.java`、
  `JpegLsRunModeContext.java`：context state；
- `internal/JpegLsColorTransform.java`：HP inverse transform。

### 8.2 ImageIO 和 dcm4che adapter

- `JpegLsImageReader.java`；
- `JpegLsImageWriter.java`；
- `JpegLsRasterFrames.java`；
- `JpegLsImageWriteParam.java`；
- `JpegLsCodec.java`；
- Lossless/Near-Lossless reader、writer 和 SPI 类；
- `src/main/resources/.../readers.properties`；
- `src/main/resources/.../writers.properties`；
- `META-INF/services/javax.imageio.spi.ImageReaderSpi`；
- `META-INF/services/javax.imageio.spi.ImageWriterSpi`。

## 9. 推荐的后续开发顺序

1. **补齐 per-scan 状态机**
   - 先写失败测试：三个 component scan 分别使用不同 NEAR、LSE 和 DRI；
   - 在 scan 间支持 LSE、DRI、APPn、COM；
   - 给每个 scan 保存 preset、traits、restart interval 和 mapping selector 快照。

2. **完成 mapping table 端到端语义**
   - 先做 decode lookup 和 `Wt` bytes output；
   - 定义不能放入普通 Raster 时的 core/adapter 边界；
   - 再公开 mapping table API 和 writer selector；
   - 增加固定向量和 continuation frame 测试。

3. **完成 `ImageReadParam`**
   - region、subsampling、offset、bands、caller destination；
   - 复用现有 JPEG adapter 的边界检查模式。

4. **完成标准 DICOM Pixel Data profile**
   - BitsStored/BitsAllocated、signed mono、Photometric、planar RGB；
   - descriptor mismatch 和非法 subsampled Photometric；
   - near-lossless signed-domain error。

5. **实现历史 YBR_FULL_422 兼容路径**
   - 与标准 adapter 路径分开；
   - 验证 odd width 和 metadata 更新。

6. **增加 Compressor/Decompressor 单帧和多帧集成测试**。

7. **补齐 malformed/resource-limit/concurrency 和 SPI discovery**。

8. **处理 warning**
   - 去掉 `<showWarnings>false</showWarnings>`；
   - clean compile/test；
   - 修复实际编译 warning 和测试日志 warning。

9. **最终验收**

   ```powershell
   .\mvnw.cmd clean test
   ```

   对照 `jpegls-development-plan.md` 第 13、14、15 节逐项核验。除明确暂缓的外部互操作外，不能用
   自编自解或较窄的定向测试替代完整范围验收。

## 10. 继续开发时的注意事项

- 不要 reset、checkout 或覆盖当前未提交工作；
- 新功能继续使用测试先行，先观察目标行为失败，再实现；
- 不要通过放宽断言、屏蔽异常或关闭 warning 来通过质量门；
- 不要把 JPEG-LS ILV 与 DICOM Planar Configuration 混为一谈；
- 不要把 DICOM PALETTE COLOR LUT 自动等同于 JPEG-LS mapping table；
- core 处理 sample code，signed container 转换属于 adapter；
- writer 每次只输出一个完整 JPEG-LS frame，不输出 DICOM padding；
- multi-scan、mapping、restart 和 DNL 的状态更新必须按 marker 出现位置生效。
