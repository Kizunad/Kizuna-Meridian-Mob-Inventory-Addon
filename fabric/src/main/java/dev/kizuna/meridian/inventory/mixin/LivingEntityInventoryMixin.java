package dev.kizuna.meridian.inventory.mixin;

import dev.kizuna.meridian.inventory.MobInventories;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityInventoryMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("TAIL"))
    private void meridian$dropInventory(ServerLevel level, DamageSource source, CallbackInfo callback) {
        // 原版已处理装备；这里只结算额外背囊，区块卸载不经过死亡入口。
        if ((Object) this instanceof Mob mob && MobInventories.supports(mob)) {
            MobInventories.state(mob).dropOnDeath(mob, level);
        }
    }
}
