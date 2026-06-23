---
name: me-init
description: 为新工程初始化 CLAUDE.md + docs/ 知识库体系，以 CLAUDE.md 为入口索引到 docs 目录的规范文档
---

请读取并执行 ~/.claude/skills/omc-learned/me-init/SKILL.md 中的完整流程。

- 若用户提供了项目名参数（$ARGUMENTS），使用该名称；否则使用当前目录名。
- 按 SKILL.md 的检测规则判断技术栈。
- 若 CLAUDE.md 或 docs/ 已存在，遵循 SKILL.md 的「不覆盖已有文件」原则，仅创建缺失文件，除非用户明确要求覆盖。
- 完成后列出创建的文件路径，并提示用户补全 docs/ 中的 TODO 占位内容。