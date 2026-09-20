# JPEG 完整编解码功能开发方案

## 1. 目标和边界

当前提交 `e85d890` 已完成 DICOM Phase 1 的四个 JPEG 传输语法，并提供了部分通用 Progressive Huffman ImageIO 能力：

- JPEG Baseline Process 1: `.50`
- JPEG Extended Sequential Process 2/4: `.51`
- JPEG Lossless Process 14: `.57`
- JPEG Lossless Process 14 SV1: `.70`
- 通用 Progressive Huffman 的基础读写路径

本方案的目标是继续完成标准 ISO/IEC 10918 JPEG 的剩余功能：

- Progressive DCT Huffman 的完整 scan script 和 restart 支持
- Arithmetic Sequential/Progressive/Lossless
- Differential Sequential/Progressive/Lossless
- CMYK/YCCK 四组件 JPEG
- 所有新增能力的 ImageIO 参数、SPI 和标准互操作测试

`.50/.51/.57/.70` 继续作为 DICOM 正式传输语法；Progressive、Arithmetic、Differential、CMYK/YCCK 作为通用 JPEG ImageIO 能力实现，不虚构不存在的 DICOM Transfer Syntax。

当前 `ArithmeticJpegCodec` 和 `DifferentialJpegCodec` 只能视为内部闭环原型，不能作为标准互操作实现继续扩展。

## 2. 总体原则

1. 所有 codec 必须遵循 ISO JPEG marker、entropy coding 和 frame process 定义。
2. 不使用额外 helper 或外部转换路径掩盖内部 codec 的互操作问题。
3. 每个新功能先写失败测试，再实现最小代码，再运行专项和完整回归。
4. 自编码/自解码只能证明闭环；标准完成必须同时有外部编码输入和外部解码输出证据。
5. 现有 DICOM 四个传输语法的行为和 API 不能回归。
6. 不修改或提交 `docs/superpowers/**` 临时文件。
7. 每个阶段独立提交，提交前执行完整 Maven 测试。

## 3. 阶段一：公共 JPEG 模型和标准测试基础

### 目标

建立 Progressive、Arithmetic、Differential 和 CMYK/YCCK 共用的标准 frame/scan/entropy 基础，避免每个 codec 各自解析 marker 和 bitstream。

### 主要文件

- Modify: `dcm4che-imageio-codecs-jpeg/src/main/java/io/github/cocosip/dcm4che/imageio/codecs/jpeg/internal/JpegFrame.java`
- Modify: `.../JpegMarkerReader.java`
- Modify: `.../JpegMarkerWriter.java`
- Modify: `.../BitReader.java`
- Modify: `.../BitWriter.java`
- Create: `.../JpegScanScript.java`
- Create: `.../JpegProcess.java`
- Test: `.../JpegMarkerReaderTest.java`
- Test: `.../BitStreamTest.java`
- Create: `dcm4che-imageio-codecs-jpeg/src/test/resources/interop/`

### 具体内容

- 统一 SOF0/SOF1/SOF2/SOF3/SOF5/SOF6/SOF7/SOF9/SOF10/SOF11 的 frame metadata。
- 统一 DQT、DHT、DAC、DRI、SOS、APPn、COM、RST 解析。
- `JpegScanScript` 明确定义组件列表、`Ss`、`Se`、`Ah`、`Al`、DC/AC 模式。
- 统一 MCU、sampling factor、component plane 和边界块计算。
- 统一 entropy byte stuffing、marker 边界和 restart segment 分割。
- 增加非法 marker、非法长度、非法 scan 参数、截断数据测试。

### 验收

- 现有 Core 14/14、JPEG 55/55 保持通过。
- 新的公共解析器不改变 `.50/.51/.57/.70` 已有输出。
- 所有新 codec 都复用同一套 marker 和边界校验。

## 4. 阶段二：完整 Progressive DCT Huffman

### 目标

把当前固定两段 scan 的 Progressive 实现扩展为支持合法 JPEG scan script，并支持 Progressive restart interval。

### 主要文件

- Modify: `.../internal/ProgressiveJpegCodec.java`
- Modify: `.../ProgressiveJpegImageReader.java`
- Modify: `.../ProgressiveJpegImageWriter.java`
- Modify: `.../JpegImageWriteParam.java`
- Create: `.../internal/ProgressiveScanState.java`
- Test: `.../internal/ProgressiveJpegCodecTest.java`
- Test: `.../JpegImageIoTest.java`

### 必须支持

- DC first scan
- DC refinement scan
- AC first scan
- AC refinement scan
- `Ss/Se/Ah/Al` successive approximation
- 多组件 scan
- EOB run
- scan 之间的 coefficient state 保留
- restart interval 下 DC predictor、EOB run、successive state 重置
- 非法 scan 顺序和重复 coefficient band 拒绝

### 测试

- 当前自编码/自解码测试继续通过。
- JDK Progressive JPEG 输入解码。
- libjpeg-turbo Progressive 输入解码。
- 多 scan、AC refinement、DC refinement、restart fixture。
- 本项目编码结果由外部 decoder 解码后比较像素误差。

## 5. 阶段三：标准 Arithmetic JPEG QM coder

### 目标

用 ISO JPEG arithmetic process 的 QM coder 替换当前自定义 binary/range round-trip 原型。

### 主要文件

- Create: `.../internal/JpegQmCoder.java`
- Create: `.../internal/JpegArithmeticContexts.java`
- Replace/refactor: `.../internal/ArithmeticJpegCodec.java`
- Modify: `.../internal/JpegMarkerReader.java`
- Modify: `.../internal/JpegMarkerWriter.java`
- Test: `.../internal/ArithmeticJpegCodecTest.java`
- Create: `.../test/resources/interop/arithmetic/`

### QM coder 要求

- 完整 113-state `Qe/NMPS/NLPS/Switch_MPS` 表。
- `A/C/CT` 寄存器和标准 renormalization。
- MPS/LPS 状态转移和状态切换。
- `0xFF` stuffing、byte-out 和 restart 处理。
- DAC 条件参数解析和校验。
- DC、AC、lossless prediction 使用各自标准上下文模型。
- Arithmetic restart 时正确重置上下文和编码寄存器。

### 覆盖范围

- SOF9: Arithmetic Sequential DCT
- SOF10: Arithmetic Progressive DCT
- SOF11: Arithmetic Lossless

### 验收

- 标准 arithmetic fixture 可由 Java decoder 解码。
- Java encoder 输出可由 libjpeg-turbo 或等价标准 decoder 解码。
- 缺少 DAC、非法 DAC、截断 arithmetic data、错误 restart marker 都有失败测试。
- 禁止只保留 Java 自编码/自解码作为完成依据。

## 6. 阶段四：Differential JPEG 参考帧语义

### 目标

实现真实 Differential frame process，不再通过普通 codec 修改 SOF marker 来模拟。

### 主要文件

- Create: `.../internal/DifferentialProcess.java`
- Create: `.../internal/JpegReferenceFrame.java`
- Replace/refactor: `.../internal/DifferentialJpegCodec.java`
- Modify: `.../internal/ProgressiveJpegCodec.java`
- Modify: `.../internal/LosslessJpegCodec.java`
- Test: `.../internal/DifferentialJpegCodecTest.java`
- Create: `.../test/resources/interop/differential/`

### API 约束

差分编码和解码必须显式接收 reference frame，不能使用隐藏全局状态：

```java
byte[] encode(JpegFrame frame, JpegFrame reference, DifferentialProcess process)
JpegFrame decode(byte[] data, JpegFrame reference)
```

### 必须校验

- reference frame 存在性。
- 尺寸、组件数、精度、sampling compatibility。
- 差分样本/差分系数范围。
- SOF5/SOF6/SOF7 与对应 SOS 语义匹配。
- differential scan 的 DRI/RST 状态。
- 第一帧不能伪造 reference。

### 验收

- reference + differential frame 成对编码解码后像素一致。
- reference 错误、尺寸不一致、精度不一致均明确失败。
- 标准 differential fixture 可以跨 codec 解码。

## 7. 阶段五：CMYK/YCCK 四组件 JPEG

### 目标

支持通用 JPEG 的 4-component CMYK 和 YCCK，不把它们强行映射为 DICOM 的 1/3 component 路径。

### 主要文件

- Modify: `dcm4che-imageio-codecs-core/src/main/java/.../DicomImageTypes.java`
- Create: `.../internal/JpegColorConverter.java`
- Modify: `.../JpegRasterFrames.java`
- Modify: `.../JpegImageReader.java`
- Modify: `.../JpegImageWriter.java`
- Modify: `.../internal/BaselineJpegCodec.java`
- Modify: `.../internal/ProgressiveJpegCodec.java`
- Modify: `.../internal/ExtendedJpegCodec.java`
- Test: `.../JpegImageIoTest.java`
- Create: `.../test/resources/interop/cmyk/`

### 必须支持

- 4-component frame metadata。
- Adobe APP14 marker。
- Transform 0: CMYK。
- Transform 2: YCCK。
- CMYK ↔ RGB。
- YCCK ↔ CMYK ↔ RGB。
- 原始四通道输出和 RGB 转换输出分开定义。
- 黑色通道、全零、全满和边界颜色。

Java 没有内置标准 CMYK `ColorSpace`，因此需要明确的四通道 sample model 和内部精确整数颜色转换，不能依赖浮点 `java.awt.color.ColorSpace` 处理 12/16-bit 数据。

## 8. 阶段六：SPI、互操作和文档收口

### SPI

- 为通用 Progressive、Arithmetic、Differential、CMYK/YCCK 增加明确 format name。
- reader/writer SPI 只注册已经通过标准 fixture 的能力。
- 未完成标准互操作的 codec 不进入生产 SPI 注册表。
- DICOM `.50/.51/.57/.70` 继续使用现有 properties 映射。

### 互操作矩阵

每个新格式必须验证两条方向：

1. Java encode → 外部 decoder → 原始像素语义。
2. 外部 encoder → Java decode → 原始像素语义。

PureCodecs 只用于 `.50/.51/.57/.70` 的参考验证；它不覆盖 Arithmetic、Differential、CMYK/YCCK，不能把 PureCodecs 的通过结果扩展解释为这些格式已完成。

### 文档

- 更新 `docs/design.md` 的 JPEG status matrix。
- 记录每个 format 的支持 marker、精度、组件数、sampling 和互操作工具。
- 明确区分“内部闭环通过”和“标准互操作通过”。

## 9. 测试和提交门槛

每个阶段执行：

```powershell
mvn -pl dcm4che-imageio-codecs-jpeg -am -DforkCount=0 test
```

最终执行：

```powershell
mvn -DforkCount=0 test
git diff --check
```

每个阶段单独提交，建议提交顺序：

1. `test(jpeg): add standard scan and interoperability fixtures`
2. `feat(jpeg): complete progressive scan and restart support`
3. `feat(jpeg): implement ISO arithmetic QM coding`
4. `feat(jpeg): implement differential JPEG reference frames`
5. `feat(jpeg): support CMYK and YCCK JPEG`
6. `docs(jpeg): document complete format coverage and interoperability`

只有在某阶段的专项测试、外部互操作和完整 Maven 回归全部通过后，才能把该阶段标记为完成。

## 10. 开发顺序

```text
P0 公共模型、marker、scan script 和标准 fixture
  ↓
P1 Progressive 完整 scan/restart
  ↓
P2 Arithmetic QM coder
  ↓
P3 Differential reference-frame
  ↓
P4 CMYK/YCCK
  ↓
P5 SPI、互操作矩阵、文档收口
```

建议从 P0 开始。P0 完成后再进入 P1，因为后续三个 codec 都依赖统一的 scan、marker、entropy segment 和 frame model。
