package dev.kizuna.meridian.inventory;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.kizuna.meridian.fabric.MeridianCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

final class InventoryCommands {
    private static final int ADMIN_PERMISSION = 2;
    private static final String PREFIX = "command.meridian_mob_inventory.";
    private static final DynamicCommandExceptionType UNSUPPORTED = new DynamicCommandExceptionType(
            name -> Component.translatable(PREFIX + "unsupported", name));
    private static final DynamicCommandExceptionType DAMAGED = new DynamicCommandExceptionType(
            reason -> Component.translatable(PREFIX + "damaged", reason));

    private InventoryCommands() {
        // 子树统一挂在 Framework 的管理分支。
    }

    static void register() {
        // 原版单实体选择器提供类型检查与 Tab 补全，子命令保持字面量节点。
        MeridianCommands.register(MeridianCommands.Branch.DEBUG, "inventory", ADMIN_PERMISSION, (node, context) -> {
            RequiredArgumentBuilder<CommandSourceStack, EntitySelector> target =
                    Commands.argument("entity", EntityArgument.entity());
            target.then(Commands.literal("list").executes(InventoryCommands::list));
            target.then(Commands.literal("clear").executes(InventoryCommands::clear));
            node.then(target);
        });
    }

    private static Mob target(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        // 未登记实体明确拒绝，不能通过清空命令创建一份额外库存。
        Entity entity = EntityArgument.getEntity(context, "entity");
        if (!(entity instanceof Mob mob) || !MobInventories.supports(mob)) {
            throw UNSUPPORTED.create(entity.getDisplayName());
        }
        InventoryState state = MobInventories.state(mob);
        Optional<String> diagnostic = state.diagnostic();
        if (diagnostic.isPresent()) {
            throw DAMAGED.create(diagnostic.get());
        }
        return mob;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        // 只列有东西的格子；空背囊单独说一句，不刷十八行空格子。
        Mob mob = target(context);
        MobInventory inventory = MobInventories.inventory(mob);
        List<Component> lines = new ArrayList<>();
        for (int slot = 0; slot < MobInventory.SIZE; slot++) {
            ItemStack stack = inventory.peek(slot);
            if (!stack.isEmpty()) {
                lines.add(slotLine(slot, stack));
            }
        }
        String heading = lines.isEmpty() ? "empty" : "list";
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.translatable(PREFIX + heading, mob.getDisplayName()),
                false /* broadcastToAdmins */);
        lines.forEach(line -> source.sendSuccess(() -> line, false /* broadcastToAdmins */));
        return lines.size();
    }

    private static Component slotLine(int slot, ItemStack stack) {
        // 按手边、口袋两行报位置，格子从 1 数起；另带存档槽号，方便对照 /data。
        String row = slot < MobInventory.ROW_SIZE ? "row.hand" : "row.pocket";
        int place = slot % MobInventory.ROW_SIZE + 1;
        return Component.translatable(PREFIX + "slot", Component.translatable(PREFIX + row), place, slot,
                stack.getHoverName(), stack.getCount());
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        // 清空是明确的管理删除，仍经移出接口执行，不触碰装备或出生标记。
        Mob mob = target(context);
        MobInventory inventory = MobInventories.inventory(mob);
        int removed = 0;
        for (int slot = 0; slot < MobInventory.SIZE; slot++) {
            removed += inventory.extract(slot, Integer.MAX_VALUE).getCount();
        }
        int removedCount = removed;
        context.getSource().sendSuccess(
                () -> Component.translatable(PREFIX + "clear", mob.getDisplayName(), removedCount),
                true /* broadcastToAdmins */);
        return removed;
    }
}
