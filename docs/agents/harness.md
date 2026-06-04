# 自动账本 Multi-Agent Harness

## 目标

这个 harness 把后续研发固定成“总控 Agent 编排 + 专项 Agent 并行 + 质量门禁”的流程。所有功能迭代都必须先经过任务拆分、密钥检查、测试/构建验证，再提交到 GitHub。

## 运行原则

- 总控 Agent 负责拆分任务、限定文件写入范围、合并结果、执行最终验证。
- 专项 Agent 只处理自己的职责范围，不回滚其他人的改动。
- 涉及功能和行为变化时先写测试，再改实现。
- Supabase URL、publishable key、owner key、签名文件、SSH key 只放本地忽略文件。
- Git 提交信息使用 Conventional Commit 风格中文描述，例如 `feat: 增加预算提醒`、`fix: 修复来源扫描`、`chore: 更新harness脚本`。
- 每个阶段完成前运行 `scripts/harness/run-checks.sh`。
- Git 历史重写、Supabase key 轮换、Release 上传属于高影响操作，必须单独确认。

## 推荐执行顺序

1. 总控 Agent 创建本轮目标和写入边界。
2. 产品 Agent 更新需求、验收标准和路线图。
3. UI/UX Agent 产出页面结构和交互说明。
4. 前端 Agent 实现 Compose UI。
5. 后端 Agent 实现捕获、同步、导入导出等业务逻辑。
6. 数据库 Agent 更新 Room/Supabase schema 和迁移说明。
7. 测试 Agent 增加单元测试、解析样例和设备验证清单。
8. 运维/部署 Agent 验证密钥、构建、APK、GitHub 推送路径。
9. 文档 Agent 更新 README、release notes、用户操作说明。
10. 总控 Agent 运行全量 harness 检查、提交、推送。

## Agent 任务模板

- 分派任务使用 `docs/agents/templates/task-brief.md`。
- 审查结果使用 `docs/agents/templates/review-report.md`。
- 运行记录使用 `scripts/harness/create-run-record.sh <slug> "<goal>"` 生成到 `docs/agents/runs/`。
- 每个 Agent 的输出必须包含改动文件、验证命令和剩余风险。

## 质量门禁

- `scripts/harness/check-secrets.sh`
- `scripts/harness/check-git-hygiene.sh`
- `scripts/harness/check-room-schema.sh`
- `scripts/harness/check-supabase-schema.sh`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
- `scripts/harness/apk-verify.sh app/build/outputs/apk/debug/app-debug.apk`
- 外部连通性需要单独执行 `scripts/harness/supabase-smoke.sh`。
- 手机安装/启动验证需要单独执行 `scripts/harness/device-smoke.sh`。

可选安全审计：

```bash
CHECK_GIT_HISTORY=1 scripts/harness/check-secrets.sh
CHECK_APK_SECRETS=1 scripts/harness/check-secrets.sh
```

APK 会包含 Android 客户端运行所需的 publishable key；它只能通过“不提交/不上传 APK”降低扩散，不能作为真正的服务端秘密保存。

## 本地配置

复制 `local.properties.example` 为 `local.properties`，填入本机 Android SDK 和 Supabase 连接信息。`local.properties` 已被 `.gitignore` 忽略。
