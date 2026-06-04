# Agent Task Brief

## Agent

填写角色：产品 / UI/UX / 前端 / 后端 / 数据库 / 测试 / 运维 / 文档。

## Goal

本次任务要完成的一个明确目标。

## Write Scope

允许修改的文件或目录：

- `path/to/file`

禁止修改：

- 其他 Agent 正在处理的文件
- 全局 git 配置
- 本地密钥文件

## Context

必要上下文，不依赖对话历史。

## Acceptance

- 可验收条件 1
- 可验收条件 2

## Verification

```bash
scripts/harness/check-secrets.sh
./gradlew :app:testDebugUnitTest
```

## Output

- 改了哪些文件
- 验证结果
- 剩余风险

