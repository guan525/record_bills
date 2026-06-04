# AGENTS.md

## Remote Long-Running Tasks

When working on the SSH development machine `lizhangguan@10.37.97.71`, run long-running or disconnect-sensitive commands inside tmux with `agent-tmux`.

Use this for builds, full test suites, batch jobs, downloads, Docker jobs, or any command that should continue if the local Mac sleeps, shuts down, or disconnects:

```bash
agent-tmux run <session-name> 'cd /path/to/workspace && <command>'
```

Short read-only inspection commands can still run normally.

## Local Project Rules

- Do not change global git configuration. This repo uses a repo-scoped GitHub deploy key.
- Keep API keys, Supabase URLs, owner keys, deploy keys, signing keys, and local credentials in ignored local files.
- Run `scripts/harness/check-secrets.sh` before every commit.
- Use `CHECK_GIT_HISTORY=1 scripts/harness/check-secrets.sh` for approved history audits. Do not rewrite Git history without explicit approval.
- Use Chinese Conventional Commit messages, for example `feat: 增加预算提醒` and `fix: 修复来源扫描`.
- For feature and behavior work, write or update tests before implementation.
- Preserve local-first Room/SQLite as the source of truth.

## Multi-Agent Roles

Follow `docs/agents/harness.md` and role files under `docs/agents/roles/`.
