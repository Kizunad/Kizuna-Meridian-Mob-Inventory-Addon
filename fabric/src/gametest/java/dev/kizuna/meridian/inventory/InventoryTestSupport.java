package dev.kizuna.meridian.inventory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

final class InventoryTestSupport {
    static final int TIMEOUT = 100;
    static final BlockPos SPAWN = new BlockPos(1, 1, 1);
    static final double DROP_RADIUS = 2.0;

    private InventoryTestSupport() {
        // 所有回归用例共用真实实体、NBT 文件与断言入口。
    }

    static Skeleton spawn(GameTestHelper context) {
        // 走自然出生原因及真正入世界事件；关闭 AI，避免日晒和拾取干扰即时断言。
        Skeleton skeleton = context.spawn(EntityTypes.SKELETON, SPAWN, EntitySpawnReason.NATURAL);
        skeleton.setNoAi(true /* noAi */);
        skeleton.setCustomName(Component.literal("inventory-test-" + skeleton.getUUID()));
        clearEquipment(skeleton);
        return skeleton;
    }

    static void clearEquipment(Skeleton skeleton) {
        // 排除出生随机装备，专门的装备掉落用例再配置保证掉落的测试物。
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            skeleton.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    static CompoundTag save(Entity entity) {
        // 与 /data get entity 相同，经过实体全部附加存档钩子。
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    static void load(Entity entity, CompoundTag data) {
        // 调原版实体 load，不能以直接调用库存解码器替代接线验收。
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, entity.registryAccess(), data));
    }

    static CompoundTag diskRoundTrip(CompoundTag data) {
        // 真正经过压缩 NBT 文件，保留文件在各次 GameTest 运行目录内供复核。
        try {
            Path directory = Path.of("inventory-saves");
            Files.createDirectories(directory);
            Path file = directory.resolve(UUID.randomUUID() + ".nbt");
            NbtIo.writeCompressed(data, file);
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException failure) {
            throw new IllegalStateException("实体 NBT 文件往返失败", failure);
        }
    }

    static Skeleton reload(GameTestHelper context, Skeleton original) {
        // 卸载不调用死亡；同 UUID 的新实体从盘上读回再加入真实服务器。
        CompoundTag data = diskRoundTrip(save(original));
        ServerLevel level = context.getLevel();
        int before = drops(original).size();
        original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        context.assertTrue(drops(original).size() == before, "卸载生成了掉落物");
        var loaded = new Skeleton(EntityTypes.SKELETON, level);
        load(loaded, data);
        context.assertTrue(level.addFreshEntity(loaded), "重载实体未能重新加入服务器");
        return loaded;
    }

    static List<ItemEntity> drops(Entity entity) {
        // 只观察本测试实体附近的真实 ItemEntity，避免并发场景的其他掉落。
        return entity.level().getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(DROP_RADIUS));
    }

    static void assertContents(GameTestHelper context, List<ItemStack> expected, MobInventory inventory) {
        // ItemStack.matches 同时比较物品、数量与全部组件，逐槽校验才能捕捉压槽合堆。
        context.assertTrue(expected.size() == MobInventory.SIZE, "预期库存不是 18 格");
        for (int slot = 0; slot < MobInventory.SIZE; slot++) {
            ItemStack actual = inventory.peek(slot);
            context.assertTrue(ItemStack.matches(expected.get(slot), actual),
                    "槽位 " + slot + " 不等：预期 " + expected.get(slot) + "，实际 " + actual);
        }
    }

    static void assertEmpty(GameTestHelper context, MobInventory inventory) {
        // 用于死亡和管理删除，必须所有固定槽都已清空。
        context.assertTrue(inventory.snapshot().stream().allMatch(ItemStack::isEmpty), "背囊仍残留实物");
    }
}
