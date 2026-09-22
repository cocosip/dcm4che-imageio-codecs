# JPEG 编解码设计与开发计划

本文档合并 JPEG Baseline 设计、实施计划和后续 DICOM JPEG 开发范围，作为
`dcm4che-imageio-codecs-jpeg` 的唯一专题文档。实现状态以本文档和
`docs/design.md` 的 JPEG status matrix 为准。

## 1. 范围与约束

本项目只实现 fo-dicom.Codecs 当前公开且仍在使用的四个 DICOM JPEG Transfer Syntax：

| Transfer Syntax | UID 后缀 | 编码范围 | 解码范围 |
| --- | --- | --- | --- |
| JPEG Baseline Process 1 | `.50` | 8-bit，Sequential DCT，Huffman | 8-bit，Monochrome/RGB |
| JPEG Extended Process 2/4 | `.51` | 8/12-bit，Sequential DCT，Huffman | 8/12-bit，Monochrome/RGB |
| JPEG Lossless Process 14 | `.57` | 8/12/16-bit，Predictive Huffman | 8/12/16-bit，Monochrome/RGB |
| JPEG Lossless Process 14 SV1 | `.70` | Predictor 1 | Predictor 1 |

全局约束：

- 纯 Java 实现，不依赖 JNI、JDK 内部 JPEG 实现或外部编码库。
- dcm4che 负责 descriptor、fragment、transfer syntax 映射和多帧编排；本模块每次
  ImageIO reader/writer 调用处理一个逻辑压缩帧。
- 只支持代码和 descriptor 已明确覆盖的 Monochrome/RGB 语义；不新增 DICOM 颜色模型。
- 任何 malformed marker、缺失 table、精度或采样不匹配、截断 entropy 数据和错误参数
  都必须明确失败，不能静默补齐或改变像素语义。

明确不在范围内：

- CMYK/YCCK：应用范围有限，且 dcm4che 没有对应的公开 Photometric Interpretation、
  Pixel Data 和渲染契约；不实现四组件颜色模型、转换器或 SPI。
- Progressive、Arithmetic、Differential/Hierarchical：不进入 DICOM 公开注册，
  也不作为当前标准互操作目标。仓库中若保留内部研究类，不得通过 properties、公开
  DICOM API 或默认 SPI 暴露。

## 2. 架构与数据流

JPEG 模块分为 codec-neutral 的 marker/bit/entropy 层、DCT 或 predictive codec 层，
以及 ImageIO/DICOM 适配层：

```text
JpegImageReader / JpegImageWriter
        |
  JpegRasterFrames             descriptor 与 RenderedImage 转换
        |
  Baseline / Extended / Lossless codec
        |
  marker + bit + Huffman + quantization + DCT/predictive primitives
```

读取流程：

1. `AbstractDicomImageReader` 获取 `ImageDescriptor`，并把当前压缩帧交给 JPEG reader。
2. reader 校验 SOI、SOF、DQT/DHT、SOS、DRI/RST 和 EOI，解码 component sample buffer。
3. `JpegRasterFrames` 将 component buffer 交错写入 descriptor 对应的目标图像；
   `MONOCHROME1` 在图像边界执行反转。
4. dcm4che 调用方继续负责 encapsulation、偶数字节填充和多帧持久化。

写入流程：

1. writer 从 descriptor-compatible `RenderedImage` 提取 Monochrome/RGB component buffer。
2. 对 Baseline/Extended 执行 level shift、DCT、量化、zig-zag、Huffman entropy coding。
3. 对 Lossless/SV1 执行 predictor、point transform、Huffman entropy coding。
4. 将一帧完整 JPEG 写入已连接的 `ImageOutputStream`。

## 3. 已完成实现

### 3.1 公共 JPEG 基础

- JPEG marker、长度、SOI/EOI 和截断输入校验。
- MSB-first bit reader/writer、byte stuffing 和 marker 边界处理。
- Canonical Huffman table、DQT/DHT/SOS 解析与生成；SOF/SOS 的 quantization、DC、AC
  table selector 0-3 按 component 生效，引用缺失 table 时明确失败。
- 解码支持 8-bit/16-bit DQT；12-bit Extended 编码使用覆盖更大 DC/AC category 的
  独立 Huffman 定义，不改变 Baseline 标准表。
- 8x8 DCT、量化、zig-zag、DC differential 和 AC run-length coding。
- Predictive lossless coding；predictor 1-7 解码、predictor 1 编码。
- DRI/RST restart interval 的编码、解码和 marker 顺序校验。

### 3.2 DICOM JPEG 语法

| 语法 | 当前状态 | 说明 |
| --- | --- | --- |
| `.50` Baseline | 已完成 | SOF0，8-bit，Sequential DCT，Huffman，Monochrome/RGB。 |
| `.51` Extended | 已完成 | SOF1，仅 unsigned 8/12-bit；12-bit 限定 SF444，使用 Descriptor-backed 16-bit 容器。 |
| `.57` Lossless | 已完成 | SOF3，unsigned 8/12/16-bit，predictor 1-7 解码，predictor 1 编码。 |
| `.70` Lossless SV1 | 已完成 | 固定 predictor 1，独立 reader/writer 适配器和参数约束。 |

### 3.3 ImageIO 与 DICOM 适配

- 四个语法各自的 reader/writer 和 ImageIO SPI。
- Descriptor-backed 输入输出；有损编码把 sRGB 输入转换为 YBR，解码把三通道 YBR
  转回 sRGB `BufferedImage`，Lossless 保持样本值不变。
- 解码帧在写 raster 前校验 width、height、component count 和 precision 与 descriptor
  完全一致。
- Baseline/Extended 的 compression quality 与 quantization 控制。
- Lossless/SV1 的 point transform、predictor 和 restart 参数校验。
- ImageReadParam 的 source region、subsampling factor/offset、destination offset 和成对
  source/destination band selection；未指定 source bands 时保留全部源 band。
- Descriptor 精度固定为 Baseline unsigned 8-bit、Extended unsigned 8/12-bit、Lossless
  unsigned 8/12/16-bit；Extended 12-bit `YBR_FULL_422` 在公开适配层拒绝。
- DICOM properties 只注册 `.50/.51/.57/.70`，不覆盖其他 dcm4che 默认 UID 映射。

## 4. 注册边界

正式 DICOM 注册文件：

```text
dcm4che-imageio-codecs-jpeg/src/main/resources/
  io/github/cocosip/dcm4che/imageio/codecs/jpeg/readers.properties
  io/github/cocosip/dcm4che/imageio/codecs/jpeg/writers.properties
```

两个文件都必须且只能包含以下 UID：

```text
1.2.840.10008.1.2.4.50
1.2.840.10008.1.2.4.51
1.2.840.10008.1.2.4.57
1.2.840.10008.1.2.4.70
```

Progressive、Arithmetic、Differential/Hierarchical 和 CMYK/YCCK 不得新增 DICOM
Transfer Syntax、Photometric Interpretation、四组件 Pixel Data 或公开 SPI 注册。

## 5. 剩余开发与验证任务

剩余工作只针对四个正式 Transfer Syntax 的标准互操作和边界覆盖：

1. `.51`：增加外部编码器输入和外部解码器输出，覆盖 8-bit 与 12-bit。
2. `.57/.70`：增加外部 Lossless fixture，覆盖 predictor、point transform 和 restart interval。
3. 继续补充 malformed marker、错误 RST 顺序和截断 entropy 数据的外部回归样例。
4. 使用外部 fixture 对 Monochrome1、Monochrome2、RGB/YBR 和已声明位深组合做矩阵化验证。
5. 记录每个 fixture 的编码器、解码器、像素比较方式和允许误差；内部闭环不得替代该证据。

Progressive、Arithmetic、Differential/Hierarchical 和 CMYK/YCCK 不属于上述待办事项。

## 6. 测试分层与验收标准

测试分为四层：

1. **Primitive tests**：marker、bit boundary、byte stuffing、Huffman、quantization、
   zig-zag 和 DCT/predictive 基础。
2. **Codec tests**：Monochrome/RGB component buffer round-trip、精度、predictor、
   restart 和非法输入拒绝。
3. **ImageIO tests**：SPI discovery、descriptor-backed reader/writer、ImageReadParam、
   `MONOCHROME1` 反转和 writer 输出读取。
4. **Interoperability tests**：外部 encoder -> Java decoder，以及 Java encoder -> 外部 decoder。

验收要求：

- Lossy `.50/.51` 使用明确的像素误差阈值，不比较压缩字节相等。
- Lossless `.57/.70` 使用逐样本相等比较。
- 注册边界测试确认只有四个 UID，且不覆盖已有 dcm4che 默认映射。
- 内部自编码/自解码通过只能证明闭环，不能替代外部互操作证据。

## 7. 验证命令

```powershell
.\mvnw.cmd -q -pl dcm4che-imageio-codecs-jpeg -am -DforkCount=0 clean test
.\mvnw.cmd -q -DforkCount=0 test
git diff --check
```

Maven Wrapper 版本固定为 Apache Maven 3.9.16，配置位于
`.mvn/wrapper/maven-wrapper.properties`。
