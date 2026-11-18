package dev.kizuna.meridian.inventory;

import dev.kizuna.bs3d.weights.protocol.pb.WeightsProtocol.Status;
import dev.kizuna.bs3d.weights.server.Burden;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class BurdenGameTest {
    private static final int FULL = PickupTestSupport.STACK;
    private static final int POCKET = MobInventory.ROW_SIZE;
    private static final int STONES_BELOW_LIMIT = 9;
    private static final int BONE_SPACE = 10;
    private static final int OFFER = 16;
    private static final long STONE_GRAMS = 1_000;
    private static final long DEFAULT_GRAMS = 100;
    private static final int MAIN_FACTOR = 2;
    private static final double REFERENCE_GRAMS = 40_000;
    private static final double EPSILON = 0.00001;
    private static final int WALK_TICKS = 12;
    private static final int PATH_LENGTH = 6;

    @GameTest
    public void predictedWeightAcceptsExactlyToLine(GameTestHelper context) {
        var mob = PickupTestSupport.skeleton(context);
        var bag = MobInventories.inventory(mob);
        for (int slot = 0; slot < POCKET; slot++) {
            bag.insertAt(slot, new ItemStack(Items.STICK));
        }
        bag.insertAt(POCKET, new ItemStack(Items.STONE, STONES_BELOW_LIMIT));
        var item = PickupTestSupport.ground(mob, new ItemStack(Items.BONE, OFFER));
        PickupTestSupport.scan(context, mob);
        context.assertTrue(item.isAlive() && item.getItem().getCount() == OFFER - BONE_SPACE,
                "拟插入整囊后应部分拾取到 20 kg 并留下精确余量");
        bag.extract(0, 1);
        var bow = PickupTestSupport.ground(mob, new ItemStack(Items.BOW));
        PickupTestSupport.scan(context, mob);
        context.assertTrue(bow.isAlive() && bag.peek(0).isEmpty(), "已在线上仍向免费手边拾取");
        context.succeed();
    }

    @GameTest
    public void invalidWeightSlowsAndRejectsUntilRepaired(GameTestHelper context) {
        var mob = PickupTestSupport.skeleton(context);
        var invalid = new ItemStack(Items.STONE, FULL + 1);
        mob.setItemSlot(EquipmentSlot.OFFHAND, invalid);
        var speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        var ground = PickupTestSupport.ground(mob, new ItemStack(Items.BONE));
        PickupTestSupport.scan(context, mob);
        context.assertTrue(ground.isAlive() && speed.getValue() < speed.getBaseValue(), "无效重量被当作空载");
        mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        PickupTestSupport.scan(context, mob);
        context.assertTrue(!ground.isAlive() && speed.getValue() == speed.getBaseValue(), "修复重量后没有恢复");
        context.succeed();
    }

    @GameTest
    public void speedAfterLoadMatchesFlatGroundTravel(GameTestHelper context) {
        var mob = PickupTestSupport.skeleton(context);
        var bag = MobInventories.inventory(mob);
        int rows = MobInventory.ROW_SIZE;
        for (int slot = 0; slot < rows; slot++) {
            bag.insertAt(slot, new ItemStack(Items.COBBLESTONE, FULL));
        }
        PickupTestSupport.scan(context, mob);
        var speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        double base = speed.getBaseValue();
        context.assertTrue(speed.getValue() == base, "手边行产生有效负重");
        double light = walk(context, mob);
        for (int slot = 0; slot < rows; slot++) {
            bag.swap(slot, POCKET + slot);
        }
        var loaded = InventoryTestSupport.reload(context, mob);
        loaded.getGoalSelector().removeAllGoals(goal -> true);
        var restored = loaded.getAttribute(Attributes.MOVEMENT_SPEED);
        double grams = rows * FULL * DEFAULT_GRAMS * MAIN_FACTOR;
        double expected = base / (1 + grams / REFERENCE_GRAMS);
        PickupTestSupport.scan(context, loaded);
        context.assertTrue(Math.abs(restored.getValue() - expected) < EPSILON, "读档后移速公式或修正数量错误");
        double heavy = walk(context, loaded);
        context.assertTrue(heavy > 0 && heavy < light / 2, "同一平路的实测位移未减少：" + light + " / " + heavy);
        MobInventory afterLoad = MobInventories.inventory(loaded);
        for (int slot = POCKET; slot < MobInventory.SIZE; slot++) {
            afterLoad.extract(slot, FULL);
        }
        PickupTestSupport.scan(context, loaded);
        context.assertTrue(restored.getValue() == base && restored.getBaseValue() == base, "清空后未恢复原基础移速");
        context.succeed();
    }

    private static double walk(GameTestHelper context, Skeleton mob) {
        var start = context.absolutePos(InventoryTestSupport.SPAWN);
        for (int offset = 0; offset <= PATH_LENGTH; offset++) {
            context.getLevel().setBlockAndUpdate(start.east(offset).below(), Blocks.STONE.defaultBlockState());
        }
        mob.getNavigation().stop();
        mob.snapTo(Vec3.atBottomCenterOf(start));
        mob.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        mob.setYRot(net.minecraft.core.Direction.EAST.toYRot());
        var end = Vec3.atBottomCenterOf(start.east(PATH_LENGTH));
        context.assertTrue(mob.getNavigation().moveTo(end.x, end.y, end.z, 1), "未建立平路导航");
        double x = mob.getX();
        PickupTestSupport.tick(context, mob, WALK_TICKS);
        return mob.getX() - x;
    }

    @GameTest
    public void playerAndMobAgreeIncludingNestedAndRoundedArmor(GameTestHelper context) {
        var mob = InventoryTestSupport.spawn(context);
        var player = (ServerPlayer) context.makeMockServerPlayer(GameType.SURVIVAL);
        var box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE))));
        var bag = MobInventories.inventory(mob);
        bag.insertAt(POCKET, box.copy());
        player.getInventory().setItem(POCKET, box);
        for (var slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.BODY,
                EquipmentSlot.SADDLE)) {
            var stack = new ItemStack(Items.STICK);
            mob.setItemSlot(slot, stack.copy());
            player.setItemSlot(slot, stack);
        }
        var measured = MobBurden.measure(context.getLevel(), mob, bag.snapshot());
        long total = Burden.totalGrams(player).orElseThrow();
        long effective = Burden.effectiveGrams(player).orElseThrow();
        context.assertTrue(measured.getStatus() == Status.OK && measured.getTotalGrams() == total
                && measured.getEffectiveGrams() == effective, "生物与玩家相同根清单的两项重量不符");
        long boxGrams = STONE_GRAMS + DEFAULT_GRAMS;
        long armorRoundedTogether = 1;
        int equipmentCount = 4;
        context.assertTrue(total == boxGrams + equipmentCount * DEFAULT_GRAMS
                && effective == MAIN_FACTOR * boxGrams + DEFAULT_GRAMS + armorRoundedTogether,
                "玩家或生物漏算嵌套内容、重复主手或逐栈舍入装备");
        context.succeed();
    }
}
