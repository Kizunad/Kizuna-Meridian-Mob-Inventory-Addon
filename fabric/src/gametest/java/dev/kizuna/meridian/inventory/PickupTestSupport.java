package dev.kizuna.meridian.inventory;

import net.minecraft.core.BlockPos;
import dev.kizuna.meridian.inventory.test.TargetGoalsAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class PickupTestSupport {
    static final int SCAN_WINDOW = 45;
    static final int STACK = 64;

    private PickupTestSupport() {
    }

    static Skeleton skeleton(GameTestHelper context) {
        BlockPos spawn = InventoryTestSupport.SPAWN;
        context.setBlock(spawn.below(), Blocks.STONE);
        var mob = context.spawnEntity(EntityTypes.SKELETON, spawn)
                .requirePersistence(false /* requirePersistence */)
                .spawnReason(EntitySpawnReason.NATURAL)
                .spawn();
        mob.setNoAi(false /* noAi */);
        InventoryTestSupport.clearEquipment(mob);
        mob.getGoalSelector().removeAllGoals(goal -> !(goal instanceof PickupGoal));
        ((TargetGoalsAccess) mob).meridian$targets().removeAllGoals(goal -> true);
        return mob;
    }

    static ItemEntity ground(Skeleton mob, ItemStack stack) {
        var item = new ItemEntity(mob.level(), mob.getX(), mob.getY(), mob.getZ(), stack);
        item.setDeltaMovement(Vec3.ZERO);
        item.setNoPickUpDelay();
        mob.level().addFreshEntity(item);
        return item;
    }

    static void tick(GameTestHelper context, Skeleton mob, int ticks) {
        ServerLevel level = context.getLevel();
        for (int tick = 0; tick < ticks && mob.isAlive(); tick++) {
            level.tickNonPassenger(mob);
        }
    }

    static void scan(GameTestHelper context, Skeleton mob) {
        tick(context, mob, SCAN_WINDOW);
    }
}
