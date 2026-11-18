package dev.kizuna.meridian.inventory;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.ItemStackWithSlot;

/** 服务器出生配置；资源重载仅改变之后新生的骷髅。 */
final class BirthItems {
    static final Identifier RESOURCE = Identifier.fromNamespaceAndPath(
            "meridian_mob_inventory", "birth_items/skeleton.json");
    private List<ItemStackWithSlot> items;

    void start(MinecraftServer server) {
        // 服务器注册表已就绪，物品组件可使用真实动态注册表解析。
        reload(server.registryAccess(), server.getResourceManager());
    }

    void reload(HolderLookup.Provider registries, ResourceManager resources) {
        // 必装数据资源必须存在，不能把缺文件当作空配装。
        try {
            load(registries, resources.getResourceOrThrow(RESOURCE));
        } catch (IOException failure) {
            throw new IllegalArgumentException("无法读取出生配装 " + RESOURCE, failure);
        }
    }

    void load(HolderLookup.Provider registries, Resource resource) {
        // 缺少必装资源或任何条目损坏都拒绝配置，不能悄悄按空配装发放。
        try (var reader = resource.openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            List<ItemStackWithSlot> parsed = InventoryStorage.SLOT_CODEC.listOf().parse(ops, json)
                    .getOrThrow(IllegalArgumentException::new);
            MobInventory validated = InventoryStorage.fromEntries(parsed);
            items = InventoryStorage.entries(validated);
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalArgumentException("无法加载出生配装 " + RESOURCE, failure);
        }
    }

    List<ItemStackWithSlot> snapshot() {
        // 未初始化属于接线错误；物品必须复制，发给一只实体不能消费配置模板。
        if (items == null) {
            throw new IllegalStateException("出生配装尚未初始化");
        }
        return items.stream().map(entry -> new ItemStackWithSlot(entry.slot(), entry.stack().copy())).toList();
    }
}
