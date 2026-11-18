# 仓库协作约定

- 简体中文沟通、文档和函数内注释；遵守 `~/.codex/AGENTS.md` 的代码形状规则。
- `fabric/` 保存服务器权威库存与 Minecraft 接线，`fabric/src/gametest/` 保存真实实体回归用例。
- Framework 和 bs3d-Weights 是硬依赖；普通原版骷髅没有身体和真元是受支持状态。
- 库存只暴露守恒操作及防御性快照；不要公开内部 ItemStack 或数组。
- 文档唯一维护来源为 `docs/wiki/`；README 只保留入口。
- 构建与测试一律走 `~/Code/Kizuna-remote-session/scripts/kz`，不直接执行 Gradle。
- 细则以 `~/Code/Kizuna-remote-session/handoff/codex-worker-rules.md` 为准。
- 未经授权不提交、不推送、不创建远端；新仓首次提交由主会话执行。
