package dev.kizuna.meridian.inventory.test;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface DeathLootInvoker {
    @Invoker("dropAllDeathLoot")
    void inventoryTest$repeatDeathLoot(ServerLevel level, DamageSource source);
}
