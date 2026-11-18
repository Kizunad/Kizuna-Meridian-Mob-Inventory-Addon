package dev.kizuna.meridian.inventory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InventoryCommandsGameTest {
    private static final String ROOT = "meridian debug inventory ";
    private static final String UNIQUE_TAG = "inventory_command_fixture";
    private static final int AMOUNT = 5;

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void permissionsSelectorsAndClearUseRealCommandTree(GameTestHelper context) {
        // 权限不足无法读写，管理员通过原版选择器只清空背囊。
        Skeleton skeleton = InventoryTestSupport.spawn(context);
        skeleton.addTag(UNIQUE_TAG);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        MobInventory inventory = MobInventories.inventory(skeleton);
        inventory.insertAt(MobInventory.ROW_SIZE, new ItemStack(Items.EMERALD, AMOUNT));
        CommandSourceStack operator = context.getLevel().getServer().createCommandSourceStack();
        CommandSourceStack player = operator.withPermission(LevelBasedPermissionSet.ALL);
        String selection = "@e[type=minecraft:skeleton,tag=" + UNIQUE_TAG + ",limit=1]";
        context.assertTrue(run(player, ROOT + selection + " list") == 0, "普通玩家能读取管理背囊");
        context.assertTrue(run(player, ROOT + selection + " clear") == 0, "普通玩家能清空背囊");
        context.assertTrue(inventory.peek(MobInventory.ROW_SIZE).getCount() == AMOUNT, "权限拒绝后库存被修改");
        context.assertTrue(run(operator, ROOT + selection + " list") == 1, "list 应只列出有东西的那一格");
        context.assertTrue(run(operator, ROOT + selection + " clear") == AMOUNT, "选择器 clear 返回数量错误");
        InventoryTestSupport.assertEmpty(context, inventory);
        context.assertTrue(skeleton.getMainHandItem().is(Items.IRON_SWORD), "clear 修改了原版装备");
        context.assertTrue(run(operator, "data get entity " + skeleton.getStringUUID()
                + " \"" + InventoryStorage.KEY + "\".version") == InventoryStorage.VERSION,
                "原版 /data 无法读取命名空间存档");
        context.succeed();
    }

    @GameTest(maxTicks = InventoryTestSupport.TIMEOUT)
    public void selectorsAndActionsCompleteAndUnregisteredEntitiesAreRejected(GameTestHelper context) {
        // Tab 走 Brigadier 原版路径；未登记生物与骷髅变种都必须拒绝。
        Skeleton skeleton = InventoryTestSupport.spawn(context);
        CommandSourceStack source = context.getLevel().getServer().createCommandSourceStack();
        context.assertTrue(completions(source, "meridian debug ").contains("inventory"), "缺少 inventory 补全");
        context.assertTrue(completions(source, ROOT + "@e[type=minecraft:ske").stream()
                .anyMatch(value -> value.contains("skeleton")), "实体选择器类型没有补全");
        context.assertTrue(completions(source, ROOT + skeleton.getStringUUID() + " ")
                .containsAll(List.of("list", "clear")), "动作未补全");
        Entity pig = context.spawn(EntityTypes.PIG, InventoryTestSupport.SPAWN);
        Entity stray = context.spawn(EntityTypes.STRAY, InventoryTestSupport.SPAWN);
        for (Entity entity : List.of(pig, stray)) {
            context.assertTrue(run(source, ROOT + entity.getStringUUID() + " list") == 0,
                    "未登记实体 list 没有拒绝：" + entity.getType());
            context.assertTrue(run(source, ROOT + entity.getStringUUID() + " clear") == 0,
                    "未登记实体 clear 没有拒绝：" + entity.getType());
        }
        var node = source.getServer().getCommands().getDispatcher().getRoot().getChild("meridian")
                .getChild("debug").getChild("inventory");
        context.assertTrue(!node.canUse(source.withPermission(LevelBasedPermissionSet.ALL)),
                "下发给普通玩家的命令树未过滤管理节点");
        context.succeed();
    }

    private static List<String> completions(CommandSourceStack source, String input) {
        // 使用聊天栏 Tab 的分发器建议入口。
        CommandDispatcher<CommandSourceStack> dispatcher = source.getServer().getCommands().getDispatcher();
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).join().getList().stream()
                .map(Suggestion::getText)
                .toList();
    }

    static int run(CommandSourceStack source, String command) {
        // Minecraft 的执行方法返回 void，通过官方回调读取成功结果。
        int[] result = {0};
        CommandSourceStack observed = source.withCallback((success, value) -> {
            if (success) {
                result[0] = value;
            }
        });
        source.getServer().getCommands().performPrefixedCommand(observed, command);
        return result[0];
    }
}
