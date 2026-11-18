package dev.kizuna.meridian.inventory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 每只已登记实体的一份状态；坏存档原样保留，禁止继续写入和补发。 */
public final class InventoryState {
    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryState.class);
    private MobInventory inventory = new MobInventory();
    private InventoryStorage.Phase phase = InventoryStorage.Phase.NEW;
    private Dynamic<?> damagedData;
    private String diagnostic;
    private boolean invalidWeightReported;

    boolean available() {
        return damagedData == null && phase == InventoryStorage.Phase.READY;
    }

    void reportInvalidWeight(Mob mob) {
        if (!invalidWeightReported) {
            LOGGER.warn("背囊重量无效，实体 {} 暂停拾取并减速", mob.getUUID());
            invalidWeightReported = true;
        }
    }

    void clearWeightDiagnostic() {
        invalidWeightReported = false;
    }

    void spillOnConversion(Mob mob, ServerLevel level) {
        if (available()) {
            for (int slot = 0; slot < MobInventory.SIZE; slot++) {
                ItemStack stack = inventory.extract(slot, Integer.MAX_VALUE);
                if (!stack.isEmpty()) {
                    mob.spawnAtLocation(level, stack);
                }
            }
        }
    }

    public MobInventory inventory() {
        // 损坏不能伪装成一个空背囊交给调用者继续写。
        if (damagedData != null) {
            throw new IllegalStateException(diagnostic);
        }
        return inventory;
    }

    public Optional<String> diagnostic() {
        // 管理命令可报告原因，原始数据仍由原版 /data 读取。
        return Optional.ofNullable(diagnostic);
    }

    public void load(Mob mob, ValueInput input) {
        // PASSTHROUGH 保留错误类型和未知字段，避免普通可选解码把坏条目吞掉。
        Optional<Dynamic<?>> raw = input.read(InventoryStorage.KEY, Codec.PASSTHROUGH);
        if (raw.isEmpty()) {
            inventory = new MobInventory();
            phase = InventoryStorage.Phase.READY;
            damagedData = null;
            diagnostic = null;
            return;
        }
        try {
            InventoryStorage.Saved saved = InventoryStorage.CODEC.parse(raw.get())
                    .getOrThrow(IllegalArgumentException::new);
            MobInventory restored = InventoryStorage.restore(saved);
            inventory = restored;
            phase = saved.phase();
            damagedData = null;
            diagnostic = null;
        } catch (IllegalArgumentException failure) {
            damagedData = raw.get();
            diagnostic = failure.getMessage();
            LOGGER.error("背囊存档停用，实体 {}，类型 {}，字段 {}：{}；原始数据保留",
                    mob.getUUID(), mob.getType(), InventoryStorage.KEY, diagnostic);
        }
    }

    public void save(ValueOutput output) {
        // 无法解释的数据不能被空列表覆盖；等待管理员修复原始字段。
        if (damagedData != null) {
            output.store(InventoryStorage.KEY, Codec.PASSTHROUGH, damagedData);
            return;
        }
        var saved = new InventoryStorage.Saved(InventoryStorage.VERSION, phase, InventoryStorage.entries(inventory));
        output.store(InventoryStorage.KEY, InventoryStorage.CODEC, saved);
    }

    void grantBirthItems(List<ItemStackWithSlot> entries) {
        // 只在新实体第一次加入服务器时发放；读档和重载事件不会补发。
        if (damagedData != null || phase != InventoryStorage.Phase.NEW) {
            return;
        }
        MobInventory configured = InventoryStorage.fromEntries(entries);
        for (int slot = 0; slot < MobInventory.SIZE; slot++) {
            if (!inventory.peek(slot).isEmpty()) {
                throw new IllegalStateException("新实体背囊已有物品，不能覆盖出生配装");
            }
        }
        inventory = configured;
        phase = InventoryStorage.Phase.READY;
    }

    public void dropOnDeath(Mob mob, ServerLevel level) {
        // 死亡结算先标记，再逐槽清空；卸载路径不调用这里。
        if (damagedData != null || phase == InventoryStorage.Phase.DEAD) {
            return;
        }
        phase = InventoryStorage.Phase.DEAD;
        for (int slot = 0; slot < MobInventory.SIZE; slot++) {
            ItemStack stack = inventory.extract(slot, Integer.MAX_VALUE);
            if (InventoryDrops.shouldDrop(level, stack)) {
                mob.spawnAtLocation(level, stack);
            }
        }
    }
}
