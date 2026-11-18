package dev.kizuna.meridian.inventory.mixin;

import dev.kizuna.meridian.inventory.InventoryHolder;
import dev.kizuna.meridian.inventory.InventoryState;
import dev.kizuna.meridian.inventory.MobInventories;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobInventoryMixin implements InventoryHolder {
    @Unique
    private InventoryState meridian$inventory;

    @Override
    public InventoryState meridianInventoryState() {
        // 未登记生物不创建 18 格库存，已登记生物按需创建唯一状态。
        if (meridian$inventory == null) {
            meridian$inventory = new InventoryState();
        }
        return meridian$inventory;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void meridian$saveInventory(ValueOutput output, CallbackInfo callback) {
        // /data 与区块存档都使用实体的同一保存入口。
        Mob mob = (Mob) (Object) this;
        if (MobInventories.supports(mob)) {
            meridianInventoryState().save(output);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void meridian$loadInventory(ValueInput input, CallbackInfo callback) {
        // 先读回生命周期，再进入 ENTITY_LOAD，防止卸载重载触发出生补发。
        Mob mob = (Mob) (Object) this;
        if (MobInventories.supports(mob)) {
            meridianInventoryState().load(mob, input);
        }
    }
}
