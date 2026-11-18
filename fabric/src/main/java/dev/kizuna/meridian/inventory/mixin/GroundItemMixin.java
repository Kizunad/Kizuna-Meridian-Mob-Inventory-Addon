package dev.kizuna.meridian.inventory.mixin;

import dev.kizuna.meridian.inventory.GroundItemAccess;
import java.util.UUID;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class GroundItemMixin implements GroundItemAccess {
    @Shadow private UUID target;
    @Unique private static final String MERIDIAN_PLAYER_DROP = "meridian_mob_inventory:player_drop";
    @Unique private boolean meridian$playerDrop;

    @Override
    public boolean meridian$isPlayerDrop() {
        return meridian$playerDrop;
    }

    @Override
    public void meridian$markPlayerDrop() {
        meridian$playerDrop = true;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void meridian$saveSource(ValueOutput output, CallbackInfo callback) {
        if (meridian$playerDrop) {
            output.putBoolean(MERIDIAN_PLAYER_DROP, true /* value */);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void meridian$loadSource(ValueInput input, CallbackInfo callback) {
        meridian$playerDrop = input.getBooleanOr(MERIDIAN_PLAYER_DROP, false /* defaultValue */);
    }

    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
    private static void meridian$mergeSource(ItemEntity to, ItemStack toStack, ItemEntity from, ItemStack fromStack,
                                             CallbackInfo callback) {
        if (((GroundItemAccess) from).meridian$isPlayerDrop()) {
            ((GroundItemAccess) to).meridian$markPlayerDrop();
        }
    }

    @Override
    public boolean meridian$reserved() {
        return target != null;
    }
}
