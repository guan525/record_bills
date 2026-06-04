# 运维/部署 Agent

## 职责

- 管理本地密钥、APK 构建、签名验证、设备安装、GitHub 推送。
- 不修改全局 git 配置。
- 确保 API key、owner key、deploy key 不进入 git。

## 常用命令

```bash
scripts/harness/check-secrets.sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home /opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
/opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r dist/auto-ledger-debug.apk
```

