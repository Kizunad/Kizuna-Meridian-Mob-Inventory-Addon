package dev.kizuna.meridian.inventory.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.kizuna.meridian.inventory.GroundItemAccess;
import dev.kizuna.meridian.inventory.MobInventories;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityInventoryMixin {
    @Unique private boolean meridian$droppingDeathLoot;

    @WrapMethod(method = "dropAllDeathLoot")
    private void meridian$trackDeathDrops(ServerLevel level, DamageSource source, Operation<Void> original) {
        if (!((Object) this instanceof ServerPlayer)) {
            original.call(level, source);
            return;
        }
        boolean previous = meridian$droppingDeathLoot;
        meridian$droppingDeathLoot = true;
        try {
            original.call(level, source);
        } finally {
            meridian$droppingDeathLoot = previous;
        }
    }

    @Inject(method = "createItemStackToDrop", at = @At("RETURN"))
    private void meridian$markPlayerDrop(ItemStack stack, boolean randomly, boolean thrownFromHand,
                                         CallbackInfoReturnable<ItemEntity> callback) {
        // 在物品第一次加入世界之前记录来源。活着时的系统溢出掉物不属于主动丢弃。
        var item = callback.getReturnValue();
        if (item != null && (Object) this instanceof ServerPlayer && (thrownFromHand || meridian$droppingDeathLoot)) {
            ((GroundItemAccess) item).meridian$markPlayerDrop();
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("TAIL"))
    private void meridian$dropInventory(ServerLevel level, DamageSource source, CallbackInfo callback) {
        // 原版已处理装备；这里只结算额外背囊，区块卸载不经过死亡入口。
        if ((Object) this instanceof Mob mob && MobInventories.supports(mob)) {
            MobInventories.state(mob).dropOnDeath(mob, level);
        }
    }
}
