package dev.kizuna.meridian.inventory;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InventoryDamageGameTest {
    private static final int UNKNOWN_VERSION = InventoryStorage.VERSION + 1;
    private static final int AMOUNT = 3;

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void duplicateSlotIsQuarantinedWithoutLosingRawData(GameTestHelper context) {
        // 两条同号不是合法合堆；原样保留供管理员恢复。
        assertDamaged(context, data -> {
            ListTag items = data.getListOrEmpty("items");
            items.add(items.get(0).copy());
        }, "重复背囊槽号");
    }

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void outOfRangeSlotIsQuarantinedWithoutLosingRawData(GameTestHelper context) {
        // 容量边界之外的槽位不能截断丢弃。
        assertDamaged(context, data -> data.getListOrEmpty("items").getCompoundOrEmpty(0)
                .putInt("Slot", MobInventory.SIZE), "背囊槽号越界");
    }

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void unknownVersionIsQuarantinedWithoutLosingRawData(GameTestHelper context) {
        // 旧代码不能重写尚不认识的新版本。
        assertDamaged(context, data -> data.putInt("version", UNKNOWN_VERSION), "未知背囊存档版本");
    }

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void undecodableItemIsQuarantinedWithoutLosingRawData(GameTestHelper context) {
        // 无效注册表 ID 必须留下解码诊断，不能被过滤为空栈。
        assertDamaged(context, data -> data.getListOrEmpty("items").getCompoundOrEmpty(0)
                .putString("id", "meridian_mob_inventory:missing_item"), "missing_item");
    }

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void missingSlotIsQuarantinedWithoutDefaultingToZero(GameTestHelper context) {
        // 原版槽位 codec 有缺省零；本格式必须显式给槽号。
        assertDamaged(context, data -> data.getListOrEmpty("items").getCompoundOrEmpty(0).remove("Slot"), "Slot");
    }

    private static void assertDamaged(GameTestHelper context, Consumer<CompoundTag> damage, String expectedReason) {
        // 先保存真实实体，再篡改盘上数据；真实 load 后不得开放一个空背囊。
        Skeleton original = InventoryTestSupport.spawn(context);
        MobInventories.inventory(original).insertAt(1, new ItemStack(Items.EMERALD, AMOUNT));
        CompoundTag entityData = InventoryTestSupport.save(original);
        CompoundTag inventoryData = entityData.getCompoundOrEmpty(InventoryStorage.KEY);
        damage.accept(inventoryData);
        CompoundTag diskData = InventoryTestSupport.diskRoundTrip(entityData);
        var loaded = new Skeleton(EntityTypes.SKELETON, context.getLevel());
        InventoryTestSupport.load(loaded, diskData);
        InventoryState state = MobInventories.state(loaded);
        context.assertTrue(state.diagnostic().orElse("").contains(expectedReason),
                "缺少对应损坏诊断：" + state.diagnostic());
        assertUnavailable(context, loaded);
        CompoundTag savedAgain = InventoryTestSupport.diskRoundTrip(InventoryTestSupport.save(loaded));
        context.assertTrue(inventoryData.equals(savedAgain.get(InventoryStorage.KEY)), "坏数据被保存为空囊或部分数据");
        context.succeed();
    }

    private static void assertUnavailable(GameTestHelper context, Skeleton entity) {
        // 损坏必须显式拒绝访问，不能让其他附属把它当空库存继续插入。
        try {
            MobInventories.inventory(entity);
        } catch (IllegalStateException expected) {
            return;
        }
        context.fail("损坏背囊仍可被当作普通库存访问");
    }
}
