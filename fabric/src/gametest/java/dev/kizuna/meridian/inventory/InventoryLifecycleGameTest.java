package dev.kizuna.meridian.inventory;

import dev.kizuna.meridian.inventory.test.DeathLootInvoker;
import java.io.IOException;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.gamerules.GameRules;

public final class InventoryLifecycleGameTest {
    private static final int BIRTH_COUNT = 3;
    private static final int DROP_COUNT = 7;
    private static final int POCKET_DROP_COUNT = 5;
    private static final float LETHAL_DAMAGE = 1000.0F;
    private static final Identifier BIRTH_FIXTURE = Identifier.fromNamespaceAndPath(
            "meridian_mob_inventory_test", "birth_items/skeleton.json");

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void naturalSkeletonStartsEmptyAndUnmarkedForPersistence(GameTestHelper context) {
        // 不命名的自然出生骷髅默认空囊，不能因初始化变成常驻生物。
        Skeleton skeleton = context.spawnEntity(EntityTypes.SKELETON, InventoryTestSupport.SPAWN)
                .requirePersistence(false /* requirePersistence */)
                .spawnReason(EntitySpawnReason.NATURAL)
                .spawn();
        InventoryTestSupport.assertEmpty(context, MobInventories.inventory(skeleton));
        context.assertTrue(!skeleton.isPersistenceRequired(), "空背囊把自然骷髅标成持久化");
        CompoundTag stored = InventoryTestSupport.save(skeleton).getCompoundOrEmpty(InventoryStorage.KEY);
        context.assertTrue(stored.getStringOr("phase", "").equals("READY"), "空配装也必须记录已发放");
        context.succeed();
    }

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void configuredBirthItemsAreIssuedOnceAcrossUnloadAndReload(GameTestHelper context) {
        // 真资源经生产配置解析器载入；用例在同一服务器回调内完成并恢复模板，不影响其他场景。
        ServerLevel level = context.getLevel();
        try {
            loadBirthFixture(level);
            Skeleton skeleton = InventoryTestSupport.spawn(context);
            MobInventory inventory = MobInventories.inventory(skeleton);
            ItemStack born = inventory.extract(MobInventory.ROW_SIZE, 1);
            context.assertTrue(born.is(Items.DIAMOND) && born.getCount() == 1, "出生物品没有发到口袋");
            context.assertTrue(born.getCustomName() != null, "出生物品组件丢失");
            loadBirthFixture(level);
            ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(skeleton, level);
            context.assertTrue(inventory.peek(MobInventory.ROW_SIZE).getCount() == BIRTH_COUNT - 1,
                    "重复加载事件补发了出生物品");
            List<ItemStack> expected = inventory.snapshot();
            Skeleton loaded = InventoryTestSupport.reload(context, skeleton);
            InventoryTestSupport.assertContents(context, expected, MobInventories.inventory(loaded));
            InventoryCommandsGameTest.run(level.getServer().createCommandSourceStack(),
                    "meridian debug inventory " + loaded.getStringUUID() + " clear");
            Skeleton emptyReload = InventoryTestSupport.reload(context, loaded);
            InventoryTestSupport.assertEmpty(context, MobInventories.inventory(emptyReload));
            context.assertTrue(InventoryTestSupport.drops(emptyReload).isEmpty(), "卸载或重载生成了掉落物");
        } finally {
            MobInventoryFabric.BIRTH_ITEMS.start(level.getServer());
        }
        context.succeed();
    }

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void existingEntityWithoutInventoryDataDoesNotReceiveBirthItems(GameTestHelper context) {
        // 旧存档没有本字段时按已出生处理；新增模组或重载配置不能给旧生物补发。
        ServerLevel level = context.getLevel();
        Skeleton original = InventoryTestSupport.spawn(context);
        CompoundTag data = InventoryTestSupport.save(original);
        data.remove(InventoryStorage.KEY);
        try {
            loadBirthFixture(level);
            var loaded = new Skeleton(EntityTypes.SKELETON, level);
            InventoryTestSupport.load(loaded, InventoryTestSupport.diskRoundTrip(data));
            ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(loaded, level);
            InventoryTestSupport.assertEmpty(context, MobInventories.inventory(loaded));
        } finally {
            MobInventoryFabric.BIRTH_ITEMS.start(level.getServer());
        }
        context.succeed();
    }

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void realDeathDropsInventoryOnceAndLeavesEquipmentToVanilla(GameTestHelper context) {
        // 真致死伤害产生物品实体；保证掉落的装备由原版处理，不应复制。
        Skeleton skeleton = InventoryTestSupport.spawn(context);
        var first = new ItemStack(Items.DIAMOND, DROP_COUNT);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("dropped-components"));
        var second = new ItemStack(Items.EMERALD, POCKET_DROP_COUNT);
        ItemStack expectedFirst = first.copy();
        ItemStack expectedSecond = second.copy();
        MobInventory inventory = MobInventories.inventory(skeleton);
        inventory.insertAt(0, first);
        inventory.insertAt(MobInventory.ROW_SIZE, second);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        skeleton.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        ServerLevel level = context.getLevel();
        skeleton.hurtServer(level, skeleton.damageSources().genericKill(), LETHAL_DAMAGE);
        context.assertTrue(!skeleton.isAlive(), "致死伤害没有杀死骷髅");
        InventoryTestSupport.assertEmpty(context, inventory);
        assertDroppedOnce(context, skeleton, expectedFirst, expectedSecond);
        ((DeathLootInvoker) skeleton).inventoryTest$repeatDeathLoot(level, skeleton.damageSources().genericKill());
        assertDroppedOnce(context, skeleton, expectedFirst, expectedSecond);
        context.assertTrue(skeleton.getMainHandItem().isEmpty(), "原版装备掉落后没有清空");
        Skeleton loaded = InventoryTestSupport.reload(context, skeleton);
        ((DeathLootInvoker) loaded).inventoryTest$repeatDeathLoot(level, loaded.damageSources().genericKill());
        assertDroppedOnce(context, loaded, expectedFirst, expectedSecond);
        context.succeed();
    }

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void disabledMobDropsStillClearsAndSettlesInventory(GameTestHelper context) {
        // 关闭规则时销毁背囊实物；之后开回规则、重复死亡也不能补掉。
        ServerLevel level = context.getLevel();
        boolean originalRule = level.getGameRules().get(GameRules.MOB_DROPS);
        Skeleton skeleton = InventoryTestSupport.spawn(context);
        MobInventory inventory = MobInventories.inventory(skeleton);
        inventory.insertAt(0, new ItemStack(Items.DIAMOND, DROP_COUNT));
        try {
            level.getGameRules().set(GameRules.MOB_DROPS, false /* value */, level.getServer());
            skeleton.hurtServer(level, skeleton.damageSources().genericKill(), LETHAL_DAMAGE);
            InventoryTestSupport.assertEmpty(context, inventory);
            level.getGameRules().set(GameRules.MOB_DROPS, true /* value */, level.getServer());
            ((DeathLootInvoker) skeleton).inventoryTest$repeatDeathLoot(level, skeleton.damageSources().genericKill());
            context.assertTrue(countDrops(skeleton, Items.DIAMOND) == 0, "规则关闭后仍掉出背囊物品");
        } finally {
            level.getGameRules().set(GameRules.MOB_DROPS, originalRule, level.getServer());
        }
        context.succeed();
    }

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void vanishingItemDisappearsAndOrdinaryItemDrops(GameTestHelper context) {
        // 背囊里的消失诅咒同样生效，但不能影响其他普通物品。
        Skeleton skeleton = InventoryTestSupport.spawn(context);
        ServerLevel level = context.getLevel();
        var cursed = new ItemStack(Items.DIAMOND_SWORD);
        cursed.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.VANISHING_CURSE), 1);
        MobInventory inventory = MobInventories.inventory(skeleton);
        inventory.insertAt(0, cursed);
        inventory.insertAt(MobInventory.ROW_SIZE, new ItemStack(Items.DIAMOND, DROP_COUNT));
        skeleton.hurtServer(level, skeleton.damageSources().genericKill(), LETHAL_DAMAGE);
        InventoryTestSupport.assertEmpty(context, inventory);
        context.assertTrue(countDrops(skeleton, Items.DIAMOND_SWORD) == 0, "消失诅咒物品没有消失");
        context.assertTrue(countDrops(skeleton, Items.DIAMOND) == DROP_COUNT, "普通背囊物品未完整掉落");
        context.succeed();
    }

    private static void loadBirthFixture(ServerLevel level) {
        // 与真实数据包相同的 Resource 和注册表解析路径，仅换成测试命名空间的资源。
        try {
            var resource = level.getServer().getResourceManager().getResourceOrThrow(BIRTH_FIXTURE);
            MobInventoryFabric.BIRTH_ITEMS.load(level.registryAccess(), resource);
        } catch (IOException failure) {
            throw new IllegalStateException("出生配置测试资源缺失", failure);
        }
    }

    private static void assertDroppedOnce(GameTestHelper context, Skeleton skeleton,
                                          ItemStack first, ItemStack second) {
        // 栈数量和组件都必须一致；重复回调可能生成原版战利品，单独观察本用例的背囊物品与装备。
        List<ItemEntity> drops = InventoryTestSupport.drops(skeleton);
        context.assertTrue(drops.stream().filter(item -> ItemStack.matches(item.getItem(), first)).count() == 1,
                "第一格没有恰好掉一份完整组件物品");
        context.assertTrue(drops.stream().filter(item -> ItemStack.matches(item.getItem(), second)).count() == 1,
                "口袋没有恰好掉一份物品");
        context.assertTrue(countDrops(skeleton, Items.DIAMOND) == DROP_COUNT, "背囊掉落重复或数量错误");
        context.assertTrue(countDrops(skeleton, Items.EMERALD) == POCKET_DROP_COUNT, "口袋掉落重复或数量错误");
        context.assertTrue(countDrops(skeleton, Items.IRON_SWORD) == 1, "装备掉落重复或丢失");
    }

    private static int countDrops(Skeleton skeleton, net.minecraft.world.item.Item item) {
        // 统计实物总数，不依赖原版物品实体是否已合堆。
        return InventoryTestSupport.drops(skeleton).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }
}
