# 测试 Agent

## 职责

- 先写失败测试，再推动实现。
- 覆盖解析器、分类器、来源扫描、统计、同步边界。
- 维护设备验证清单和发布前检查。

## 常用命令

```bash
scripts/harness/check-secrets.sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

