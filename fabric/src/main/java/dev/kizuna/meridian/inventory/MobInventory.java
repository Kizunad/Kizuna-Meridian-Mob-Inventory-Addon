package dev.kizuna.meridian.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/** 固定两行库存；插入消耗来源，移出返回实物，查询只返回副本。仅在服务器线程使用。 */
public final class MobInventory {
    public static final int ROW_SIZE = 9;
    public static final int SIZE = ROW_SIZE * 2;
    private final List<ItemStack> slots = new ArrayList<>(SIZE);

    public MobInventory() {
        // 空槽也占据固定索引，不随内容压缩。
        for (int slot = 0; slot < SIZE; slot++) {
            slots.add(ItemStack.EMPTY);
        }
    }

    public ItemStack peek(int slot) {
        // 不把可变栈引用交给调用方。
        return slots.get(checkedSlot(slot)).copy();
    }

    public List<ItemStack> snapshot() {
        // 列表和栈都与库存分离，修改栈组件不会反写库存。
        return slots.stream().map(ItemStack::copy).toList();
    }

    public Insertion previewInsertion(ItemStack offered) {
        // 在副本上走同一插入算法；预览不消费调用方实物。
        var preview = new MobInventory();
        for (int slot = 0; slot < SIZE; slot++) {
            preview.slots.set(slot, slots.get(slot).copy());
        }
        ItemStack remainder = offered.copy();
        preview.insert(remainder);
        return new Insertion(preview.snapshot(), remainder);
    }

    public int insert(ItemStack source) {
        // 先补同组件的现有栈，再占空槽；每次成功插入同时从来源扣减。
        Objects.requireNonNull(source, "插入来源不能为空");
        int originalCount = source.getCount();
        for (int slot = 0; slot < SIZE && !source.isEmpty(); slot++) {
            if (!slots.get(slot).isEmpty()) {
                insertAt(slot, source);
            }
        }
        for (int slot = 0; slot < SIZE && !source.isEmpty(); slot++) {
            if (slots.get(slot).isEmpty()) {
                insertAt(slot, source);
            }
        }
        return originalCount - source.getCount();
    }

    public int insertAt(int slot, ItemStack source) {
        // 原版按物品、组件与最大堆叠数决定能否合堆；溢出仍留在来源。
        ItemStack stored = slots.get(checkedSlot(slot));
        Objects.requireNonNull(source, "插入来源不能为空");
        if (source.isEmpty() || !stored.isEmpty() && !ItemStack.isSameItemSameComponents(stored, source)) {
            return 0;
        }
        int capacity = source.getMaxStackSize() - stored.getCount();
        int accepted = Math.min(capacity, source.getCount());
        if (accepted > 0) {
            slots.set(slot, source.copyWithCount(stored.getCount() + accepted));
            source.shrink(accepted);
        }
        return accepted;
    }

    public ItemStack extract(int slot, int count) {
        // 返回被移出的唯一实物；负数不是有效操作。
        ItemStack stored = slots.get(checkedSlot(slot));
        if (count < 0) {
            throw new IllegalArgumentException("移出数量不能小于零");
        }
        return stored.split(count);
    }

    public void swap(int first, int second) {
        // 仅交换两个固定槽位，不经过合堆算法。
        ItemStack firstStack = slots.get(checkedSlot(first));
        ItemStack secondStack = slots.get(checkedSlot(second));
        slots.set(first, secondStack);
        slots.set(second, firstStack);
    }

    public ItemStack exchange(int slot, ItemStack source) {
        // 整栈换入时清空来源并返还旧物；超堆栈必须先分拆。
        int index = checkedSlot(slot);
        Objects.requireNonNull(source, "交换来源不能为空");
        if (!source.isEmpty() && source.getCount() > source.getMaxStackSize()) {
            throw new IllegalArgumentException("交换来源超过该物品最大堆叠数");
        }
        ItemStack previous = slots.set(index, source.copyAndClear());
        return previous;
    }

    static int checkedSlot(int slot) {
        // 持久化与运行时操作使用相同的槽位边界。
        if (slot < 0 || slot >= SIZE) {
            throw new IllegalArgumentException("背囊槽号越界：" + slot);
        }
        return slot;
    }

    public static final class Insertion {
        private final List<ItemStack> slots;
        private final ItemStack remainder;

        private Insertion(List<ItemStack> slots, ItemStack remainder) {
            // 结果可被外部保留，但不能把可变引用带回预览或库存。
            this.slots = slots.stream().map(ItemStack::copy).toList();
            this.remainder = remainder.copy();
        }

        public List<ItemStack> slots() {
            // 每次读取都返回独立栈快照。
            return slots.stream().map(ItemStack::copy).toList();
        }

        public ItemStack remainder() {
            // 余量只是预览，不可直接当作已完成的库存移出。
            return remainder.copy();
        }
    }
}
