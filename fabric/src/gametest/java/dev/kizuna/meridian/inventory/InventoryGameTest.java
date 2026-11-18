package dev.kizuna.meridian.inventory;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class InventoryGameTest {
    private static final int FIRST_POCKET = MobInventory.ROW_SIZE;
    private static final int LAST_SLOT = MobInventory.SIZE - 1;
    private static final int STACK_LIMIT = 64;
    private static final int PARTIAL_COUNT = 5;
    private static final int INITIAL_COUNT = STACK_LIMIT - PARTIAL_COUNT;
    private static final int DIFFERENT_LIMIT = 16;
    private static final int TEST_DAMAGE = 7;

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void allEighteenSlotsAndComponentsSurviveRealSave(GameTestHelper context) {
        // 每格独立组件，另有受损装备，完整物品事实必须经过实体存档与磁盘往返。
        Skeleton original = InventoryTestSupport.spawn(context);
        MobInventory inventory = MobInventories.inventory(original);
        for (int slot = 0; slot < MobInventory.SIZE; slot++) {
            var stack = new ItemStack(Items.BONE, slot + 1);
            var custom = new CompoundTag();
            custom.putInt("original_slot", slot);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("slot-" + slot));
            inventory.insertAt(slot, stack);
            context.assertTrue(stack.isEmpty(), "插入未扣减来源");
        }
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(TEST_DAMAGE);
        inventory.exchange(LAST_SLOT, sword);
        List<ItemStack> expected = inventory.snapshot();
        Skeleton loaded = InventoryTestSupport.reload(context, original);
        InventoryTestSupport.assertContents(context, expected, MobInventories.inventory(loaded));
        context.succeed();
    }

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void sparseSlotsAndSameItemInBothRowsStaySeparate(GameTestHelper context) {
        // 同物分占两行、中间空槽、末槽同时存在，压槽或合堆都会改变逐槽结果。
        Skeleton original = InventoryTestSupport.spawn(context);
        MobInventory inventory = MobInventories.inventory(original);
        inventory.insertAt(1, new ItemStack(Items.ARROW, PARTIAL_COUNT));
        inventory.insertAt(FIRST_POCKET, new ItemStack(Items.ARROW, PARTIAL_COUNT));
        inventory.insertAt(LAST_SLOT, new ItemStack(Items.SNOWBALL, PARTIAL_COUNT));
        List<ItemStack> expected = inventory.snapshot();
        Skeleton loaded = InventoryTestSupport.reload(context, original);
        InventoryTestSupport.assertContents(context, expected, MobInventories.inventory(loaded));
        context.assertTrue(InventoryTestSupport.drops(loaded).isEmpty(), "卸载重载不应掉落");
        context.succeed();
    }

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void previewAndPartialInsertRespectComponentsAndCapacity(GameTestHelper context) {
        // 满囊只留同组件栈的少量容量；预览、提交及来源余量必须一致。
        MobInventory inventory = MobInventories.inventory(InventoryTestSupport.spawn(context));
        for (int slot = 0; slot < MobInventory.SIZE; slot++) {
            inventory.insertAt(slot, new ItemStack(Items.COBBLESTONE, STACK_LIMIT));
        }
        inventory.extract(0, PARTIAL_COUNT);
        var source = new ItemStack(Items.COBBLESTONE, DIFFERENT_LIMIT);
        MobInventory.Insertion preview = inventory.previewInsertion(source);
        context.assertTrue(source.getCount() == DIFFERENT_LIMIT, "预览消费了来源");
        context.assertTrue(inventory.peek(0).getCount() == INITIAL_COUNT, "预览修改了库存");
        int accepted = inventory.insert(source);
        context.assertTrue(accepted == PARTIAL_COUNT && source.getCount() == DIFFERENT_LIMIT - PARTIAL_COUNT,
                "部分插入没有守恒");
        InventoryTestSupport.assertContents(context, preview.slots(), inventory);
        context.assertTrue(ItemStack.matches(preview.remainder(), source), "预览余量与实际不同");
        inventory.extract(0, PARTIAL_COUNT);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("different-component"));
        context.assertTrue(inventory.insert(source) == 0, "不同组件不应合堆");
        context.succeed();
    }

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void snapshotsExtractAndExchangeDoNotExposeInternalStacks(GameTestHelper context) {
        // 读出的副本不能改写内部；交换同时移走来源并返还原物。
        MobInventory inventory = MobInventories.inventory(InventoryTestSupport.spawn(context));
        inventory.insertAt(FIRST_POCKET, new ItemStack(Items.BONE, PARTIAL_COUNT));
        inventory.peek(FIRST_POCKET).setCount(0);
        inventory.snapshot().get(FIRST_POCKET).setCount(0);
        context.assertTrue(inventory.peek(FIRST_POCKET).getCount() == PARTIAL_COUNT, "查询暴露了内部栈");
        inventory.swap(0, FIRST_POCKET);
        ItemStack removed = inventory.extract(0, 1);
        context.assertTrue(removed.getCount() == 1 && inventory.peek(0).getCount() == PARTIAL_COUNT - 1,
                "移出数量不守恒");
        var replacement = new ItemStack(Items.DIAMOND);
        ItemStack old = inventory.exchange(0, replacement);
        context.assertTrue(replacement.isEmpty() && old.getCount() == PARTIAL_COUNT - 1,
                "换入未消费来源或换出旧物丢失");
        old.setCount(0);
        context.assertTrue(inventory.peek(0).is(Items.DIAMOND), "换出物仍能改写库存");
        context.assertTrue(inventory.peek(FIRST_POCKET).isEmpty(), "交换留下第二份物品");
        context.succeed();
    }

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void vanillaAndComponentStackLimitsAreRespected(GameTestHelper context) {
        // 雪球上限为 16；组件可进一步修改最大堆叠数，不能硬编码为 64。
        MobInventory inventory = MobInventories.inventory(InventoryTestSupport.spawn(context));
        var snowballs = new ItemStack(Items.SNOWBALL, DIFFERENT_LIMIT + 1);
        context.assertTrue(inventory.insertAt(0, snowballs) == DIFFERENT_LIMIT && snowballs.getCount() == 1,
                "没有遵守原版雪球堆叠上限");
        var bones = new ItemStack(Items.BONE, PARTIAL_COUNT + 1);
        bones.set(DataComponents.MAX_STACK_SIZE, PARTIAL_COUNT);
        context.assertTrue(inventory.insertAt(1, bones) == PARTIAL_COUNT && bones.getCount() == 1,
                "没有遵守组件中的最大堆叠数");
        context.succeed();
    }
}
