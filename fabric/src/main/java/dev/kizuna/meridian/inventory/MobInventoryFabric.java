package dev.kizuna.meridian.inventory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.server.level.ServerLevel;

public final class MobInventoryFabric implements ModInitializer {
    static final BirthItems BIRTH_ITEMS = new BirthItems();
    static final WantedItems WANTED = new WantedItems();

    @Override
    public void onInitialize() {
        // 配装在进入世界前校验；重载只更新模板，不触碰已有实体。
        ServerLifecycleEvents.SERVER_STARTING.register(BIRTH_ITEMS::start);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> WANTED.reload(server, server.getResourceManager()));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) {
                BIRTH_ITEMS.reload(server.registryAccess(), resources);
                WANTED.reload(server, resources);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Mob mob && MobInventories.supports(mob)) {
                MobInventories.state(mob).grantBirthItems(BIRTH_ITEMS.snapshot());
                MobBurden.update(level, mob);
            }
        });
        InventoryCommands.register();
        ServerLivingEntityEvents.MOB_CONVERSION.register((previous, next, params) -> {
            if (MobInventories.supports(previous) && next.getType() == EntityTypes.STRAY
                    && previous.level() instanceof ServerLevel level) {
                MobInventories.state(previous).spillOnConversion(previous, level);
            }
        });
    }
}
