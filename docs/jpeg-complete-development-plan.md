# JPEG 编解码开发计划

## 1. 当前范围

本项目只实现 fo-dicom.Codecs 当前公开且仍在使用的四个 DICOM JPEG Transfer Syntax：

| Transfer Syntax | UID 后缀 | 编码范围 | 解码范围 |
| --- | --- | --- | --- |
| JPEG Baseline Process 1 | `.50` | 8-bit，Sequential DCT，Huffman | 8-bit，Monochrome/RGB |
| JPEG Extended Process 2/4 | `.51` | 8/12-bit，Sequential DCT，Huffman | 8/12-bit，Monochrome/RGB |
| JPEG Lossless Process 14 | `.57` | 8/12/16-bit，Predictive Huffman | 8/12/16-bit，Monochrome/RGB |
| JPEG Lossless Process 14 SV1 | `.70` | Predictor 1 | Predictor 1 |

这四个 UID 是本模块唯一允许进入 dcm4che reader/writer properties 的 JPEG 注册项。
实现不扩展 dcm4che 的公开 DICOM 颜色模型或 Transfer Syntax 语义。

明确不在范围内：

- CMYK/YCCK：应用范围有限，且 dcm4che 没有对应的公开 Photometric Interpretation、
  Pixel Data 和渲染契约；不实现四组件颜色模型、转换器或 SPI。
- Progressive、Arithmetic、Differential/Hierarchical：不进入 DICOM 公开注册，
  也不作为当前标准互操作目标。

## 2. 已完成能力

### 2.1 JPEG 核心

- JPEG marker、长度和截断输入校验。
- MSB-first bit reader/writer、byte stuffing 和 marker 边界处理。
- Canonical Huffman table、DQT/DHT/SOS/EOI 处理。
- 8x8 DCT、量化、zig-zag、DC differential 和 AC run-length coding。
- Predictive lossless coding、predictor 1-7 解码、predictor 1 编码。
- DRI/RST restart interval 的编码、解码和顺序校验。

### 2.2 ImageIO 和 DICOM 适配

- Baseline、Extended、Lossless、Lossless SV1 各自的 reader/writer 和 SPI。
- Descriptor-backed 读写，支持 Monochrome/RGB 的覆盖范围。
- Baseline/Extended 的质量和量化参数。
- Lossless/SV1 的 point transform、predictor 和 restart 参数校验。
- ImageReadParam 的区域、采样、目标偏移和 band 选择路径。
- DICOM properties 只注册 `.50/.51/.57/.70`，不覆盖其他 dcm4che 默认 UID 映射。

## 3. 代码与注册边界

正式 DICOM 注册位于：

```text
dcm4che-imageio-codecs-jpeg/src/main/resources/
  io/github/cocosip/dcm4che/imageio/codecs/jpeg/readers.properties
  io/github/cocosip/dcm4che/imageio/codecs/jpeg/writers.properties
```

注册表必须只包含：

```text
1.2.840.10008.1.2.4.50
1.2.840.10008.1.2.4.51
1.2.840.10008.1.2.4.57
1.2.840.10008.1.2.4.70
```

仓库中可能保留用于算法研究的内部 Progressive、Arithmetic 或 Differential 类，
但这些类不能被 properties、公开 DICOM API 或默认 SPI 暴露。内部自编码/自解码通过
只能证明闭环，不能据此宣称标准互操作完成。

## 4. 待完成事项

当前剩余工作集中在四个正式 Transfer Syntax 的互操作和边界验证：

1. 为 `.51` 的 8/12-bit Sequential Huffman 增加外部编码器输入和外部解码器输出验证。
2. 为 `.57`、`.70` 的 8/12/16-bit Lossless 增加外部 fixture，覆盖 predictor、point transform
   和 restart interval。
3. 补充 malformed marker、缺失 table、错误 RST 顺序、截断 entropy 数据的回归样例。
4. 对 Monochrome1、RGB、位深和 signed/unsigned descriptor 组合进行矩阵化验证。
5. 记录每个 fixture 的编码器、解码器、像素比较方式和允许误差。

Progressive、Arithmetic、Differential/Hierarchical 和 CMYK/YCCK 不列入上述待完成事项。

## 5. 验收标准

每个正式语法都需要同时满足：

1. Java encoder 输出可以被至少一个外部标准 decoder 解码。
2. 外部标准 encoder 输出可以被 Java decoder 解码。
3. Lossy `.50/.51` 使用明确的像素误差阈值；Lossless `.57/.70` 使用逐样本相等比较。
4. 非法 marker、缺失 table、精度不匹配、采样不匹配和错误参数被明确拒绝。
5. 注册边界测试确认只有四个 UID，且不覆盖已有 dcm4che 默认映射。

## 6. 验证命令

```powershell
.\mvnw.cmd -q -pl dcm4che-imageio-codecs-jpeg -am -DforkCount=0 clean test
.\mvnw.cmd -q -DforkCount=0 test
git diff --check
```

Maven Wrapper 版本固定为 Apache Maven 3.9.16，配置位于
`.mvn/wrapper/maven-wrapper.properties`。
