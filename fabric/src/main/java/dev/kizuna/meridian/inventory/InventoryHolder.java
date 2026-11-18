package dev.kizuna.meridian.inventory;

/** Mixin 保存实体私有状态的接线；其他附属通过 MobInventories 访问库存。 */
public interface InventoryHolder {
    InventoryState meridianInventoryState();
}
