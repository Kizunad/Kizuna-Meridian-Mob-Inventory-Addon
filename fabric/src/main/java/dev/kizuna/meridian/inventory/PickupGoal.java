package dev.kizuna.meridian.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.pathfinder.Path;

public final class PickupGoal extends Goal {
    public static final int PRIORITY = 4;
    private static final int SCAN_TICKS = 20;
    private static final int SEARCH_RADIUS = 8;
    private static final int CANDIDATES = 16;
    private static final int CHASE_TICKS = 80;
    private static final double REACH = 1;
    private final ServerLevel level;
    private final Mob mob;
    private ItemEntity target;
    private Path path;
    private long nextScan;
    private long stopAt;

    public PickupGoal(ServerLevel level, Mob mob) {
        this.level = level;
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
        nextScan = Math.floorMod(mob.getId(), SCAN_TICKS);
    }

    @Override
    public boolean canUse() {
        long now = mob.tickCount;
        if (now < nextScan || !GroundPickup.allowed(level, mob)) {
            return false;
        }
        nextScan = now + SCAN_TICKS;
        var nearby = new ArrayList<ItemEntity>();
        var area = mob.getBoundingBox().inflate(SEARCH_RADIUS);
        var slots = MobInventories.inventory(mob).snapshot();
        level.getEntities(EntityTypes.ITEM, area, item -> GroundPickup.available(item)
                && MobInventoryFabric.WANTED.allowance(mob, slots, item.getItem()) > 0, nearby, CANDIDATES);
        nearby.sort(Comparator.comparingDouble(mob::distanceToSqr));
        for (ItemEntity item : nearby) {
            if (GroundPickup.quantity(level, mob, item) > 0) {
                if (inReach(item) || findPath(item)) {
                    target = item;
                    stopAt = now + CHASE_TICKS;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean inReach(ItemEntity item) {
        return mob.getBoundingBox().inflate(REACH, 0, REACH).intersects(item.getBoundingBox());
    }

    private boolean findPath(ItemEntity item) {
        path = mob.getNavigation().createPath(item, 0);
        return path != null && path.canReach();
    }

    @Override
    public void start() {
        if (path != null) {
            mob.getNavigation().moveTo(path, 1);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && GroundPickup.allowed(level, mob) && GroundPickup.available(target)
                && mob.tickCount < stopAt && (inReach(target) || !mob.getNavigation().isDone());
    }

    @Override
    public void tick() {
        if (target != null && inReach(target)) {
            GroundPickup.collect(level, mob, target);
            target = null;
        }
    }

    @Override
    public void stop() {
        target = null;
        path = null;
        mob.getNavigation().stop();
    }
}
