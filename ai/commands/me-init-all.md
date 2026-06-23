---
name: me-init-all
description: 为总工程目录初始化根 CLAUDE.md + docs/ 知识库，并递归为所有子目录初始化子工程知识库
---

请读取并执行 ~/.claude/skills/omc-learned/me-init-all/SKILL.md 中的完整流程。

- 以当前目录名作为总工程名。
- 扫描当前目录下所有一级子目录（排除隐藏/依赖目录）。
- 对每个没有 `CLAUDE.md` 的子目录，执行 `me-init` 流程（以子目录名作为项目名）初始化其知识库；已有 `CLAUDE.md` 的子目录仅记录，不覆盖。
- 创建根目录的 `CLAUDE.md` + `docs/` 知识库，并生成 `docs/projects.md` 索引所有子工程。
- 完成后列出初始化的子工程列表、创建的文件路径，并提示用户补全 `docs/` 中的 TODO 占位内容。
