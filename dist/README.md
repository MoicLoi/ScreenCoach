# 产物 / Build Artifacts

本目录存放 ScreenCoach 1.0.0 的可分发产物，与仓库根目录的源码一一对应。

| 文件 | 说明 |
|---|---|
| `ScreenCoach-1.0.0-debug.apk` | 已签名的安装包（120,347 字节）。`release` 与 `debug` 变体字节一致。 |
| `ScreenCoach-1.0.0-debug.apk.idsig` | APK 的 v4 签名文件，`adb install --incremental` 可选使用。 |
| `ScreenCoach-1.0.0-github.zip` | 对外发布包：安装包 + 源码 zip + README + 校验值。 |
| `screencoach-source-1.0.0.tar.gz` | 完整源码归档（与本仓库根目录内容相同）。 |
| `SHA256SUMS.txt` | 上列三个产物的 SHA-256。 |
| `GITHUB_BUNDLE.sha256` | `ScreenCoach-1.0.0-github.zip` 的 SHA-256。 |

## 校验

```bash
sha256sum -c SHA256SUMS.txt   # 在 dist/ 的上一级（仓库根目录）执行
```

## SHA-256

```
47f17bc701b6e81ace32ca1a9899d8ab207cab557b821452d3c21872efcecb9d  ScreenCoach-1.0.0-debug.apk
2f676c8c6011ccc54b5d96fe464ea5fc7293ca28b65e967fc9c92a3b845cb1cd  screencoach-source-1.0.0.tar.gz
acf2365ccb0ab06f9cb2f7aee79acd80969dde8735f9f55cc2e6da6be16ecf0e  ScreenCoach-1.0.0-github.zip
```

## 说明

- 未提交任何签名私钥（`*.keystore` / `*.jks` 由 `.gitignore` 排除）。
- 产物内不含 DeepSeek API Key；Key 仅由用户在本机填写，并使用 Android Keystore 加密保存。
