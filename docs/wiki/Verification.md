# 构建与验证

构建结构参照 `Kizuna-Meridian-Vanilla-Golem-Threatening`，文档链接检查沿用 Humanoid Taunts 附属。
仅有 `fabric/` 子工程；当前没有脱离 Minecraft 的独立策略，因此不创建空的 server/client/shared 工程。
Gradle Wrapper 与 Java 版本沿用工作区，开发运行使用 Framework 完整装配。

通过 `~/Code/Kizuna-remote-session/scripts/kz test <本仓路径> --with <Framework 工作树>`
执行构建、文档检查与真实服务器 GameTest。新仓登记尚未合入主线时，命令前设置本次进程的
`KZ_MANIFEST` 指向该 Framework 工作树的 `scripts/assembly.json`，仅在本机运行。

GameTest 源码和测试 Mixin 位于 `fabric/src/gametest/`，不进入发布包。
验证库存守恒、NBT 文件往返、出生与卸载、死亡实物、损坏数据隔离，以及真实命令权限、选择器和补全。
每局由 kz 提供独立运行目录，保留世界及日志；不接触玩家实例。
