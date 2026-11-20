package dev.kizuna.meridian.inventory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;

/** 存档和出生配置都以显式槽号描述物品，整份校验成功后才发布。 */
final class InventoryStorage {
    static final int VERSION = 1;
    static final String KEY = "meridian_mob_inventory:inventory";
    static final Codec<ItemStackWithSlot> SLOT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("Slot").forGetter(ItemStackWithSlot::slot),
            ItemStack.MAP_CODEC.forGetter(ItemStackWithSlot::stack)
    ).apply(instance, ItemStackWithSlot::new));
    static final Codec<Phase> PHASE_CODEC = Codec.STRING.comapFlatMap(InventoryStorage::parsePhase, Phase::name);
    static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("version").forGetter(Saved::version),
            PHASE_CODEC.fieldOf("phase").forGetter(Saved::phase),
            SLOT_CODEC.listOf().fieldOf("items").forGetter(Saved::items),
            Codec.INT.optionalFieldOf("birth_weapon_slot").forGetter(Saved::birthWeapon)
    ).apply(instance, Saved::new));

    enum Phase { NEW, READY, DEAD }

    record Saved(int version, Phase phase, List<ItemStackWithSlot> items, Optional<Integer> birthWeapon) { }

    private InventoryStorage() {
        // 编解码没有实例状态。
    }

    private static DataResult<Phase> parsePhase(String value) {
        // 未知生命周期与未知版本一样不能猜测成空库存。
        try {
            return DataResult.success(Phase.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            return DataResult.error(() -> "未知背囊生命周期：" + value);
        }
    }

    static MobInventory restore(Saved saved) {
        // 版本先于槽位校验；将来的格式不能被旧代码部分读取再覆盖。
        if (saved.version() != VERSION) {
            throw new IllegalArgumentException("未知背囊存档版本：" + saved.version());
        }
        MobInventory inventory = fromEntries(saved.items());
        saved.birthWeapon().ifPresent(inventory::restoreBirthWeapon);
        if (saved.phase() != Phase.READY && !saved.items().isEmpty()) {
            throw new IllegalArgumentException("待出生或已死亡的背囊仍有物品");
        }
        return inventory;
    }

    static MobInventory fromEntries(List<ItemStackWithSlot> entries) {
        // 不接受重复、越界、空条目或超堆；不使用会合堆压槽的 SimpleContainer。
        var inventory = new MobInventory();
        Set<Integer> used = new HashSet<>();
        for (ItemStackWithSlot entry : entries) {
            int slot = MobInventory.checkedSlot(entry.slot());
            if (!used.add(slot)) {
                throw new IllegalArgumentException("重复背囊槽号：" + slot);
            }
            ItemStack stack = entry.stack();
            if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()) {
                throw new IllegalArgumentException("无效背囊物品数量，槽号：" + slot);
            }
            // 校验组件与物品数量的原版约束；损坏时抛 IllegalArgumentException，整份拒绝。
            ItemStack.validateStrict(stack).getOrThrow(IllegalArgumentException::new);
            inventory.insertAt(slot, stack.copy());
        }
        return inventory;
    }

    static List<ItemStackWithSlot> entries(MobInventory inventory) {
        // 空槽不写条目，但保留其他条目的原槽号；同物两行不会合并。
        List<ItemStackWithSlot> entries = new ArrayList<>();
        for (int slot = 0; slot < MobInventory.SIZE; slot++) {
            ItemStack stack = inventory.peek(slot);
            if (!stack.isEmpty()) {
                entries.add(new ItemStackWithSlot(slot, stack));
            }
        }
        return List.copyOf(entries);
    }
}
