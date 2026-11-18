# 库存、存档与管理

`MobInventories.inventory(Mob)` 仅供服务器线程调用。库存有 18 格，0–8 为手边、9–17 为口袋；
原版装备是独立实物。`peek`、`snapshot`、`previewInsertion` 均返回防御性副本。
`insert` 与 `insertAt` 按物品组件和原版最大堆叠数插入，并同步扣减传入的来源栈；返回插入数量。
`extract` 返回移出的实物；`swap` 交换内部两个槽；`exchange` 消耗整个来源栈并返回旧槽物品。
预览不能当成提交结果，也不能与插入后剩余来源重复消费。

实体字段 `meridian_mob_inventory:inventory` 保存 `version=1`、`phase` 和 `items`。
每个物品条目使用原版 `ItemStackWithSlot` 编码，保留 `Slot`、物品及组件。
空槽不写条目，非空槽总是带原槽号；不会压空槽或合并相同物品。
生命周期 `NEW`、`READY`、`DEAD` 分别表示待发出生物品、已发、已结算死亡。
已有旧实体缺少整个字段时按空背囊 `READY` 接入，不补发出生配装。

未知版本、重复或越界槽号、条目解码失败会停用该背囊；日志包含实体 UUID、字段和原因。
保存时保留原始字段。管理命令拒绝损坏背囊，管理员可用原版 `/data get entity` 检查并修复数据；
不能静默把坏物品变成空槽。

数据包 `data/meridian_mob_inventory/birth_items/skeleton.json` 是带槽号的出生物品列表，随包为空数组。
使用原版 `ItemStackWithSlot` 格式。配置整份校验通过才生效，重载只影响之后新生的实体。
实体初次加入服务器时发一次；读档、区块卸载和重新加入不会再次发放。

死亡先记录已结算，再移出全部背囊实物。关闭 `minecraft:mob_drops` 时不生成物品实体；
带原版阻止装备掉落效果的消失诅咒物品销毁。装备继续由原版掉率处理。
重复死亡回调不会再次掉落；普通卸载不触发掉落。

管理权限等级 2 的 `/meridian debug inventory <实体> list|clear` 使用原版单实体选择器及补全。
`list` 只列有东西的格子，按手边、口袋两行报位置并带存档槽号，空背囊单独说一句；
`clear` 删除背囊物品，保留装备与生命周期标记。
