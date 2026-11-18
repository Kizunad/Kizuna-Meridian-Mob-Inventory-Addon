package dev.kizuna.meridian.inventory;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** 数据包中的类别按声明次序匹配，库存和双手使用同一分类。 */
final class WantedItems {
    private static final Identifier RESOURCE = Identifier.fromNamespaceAndPath(
            "meridian_mob_inventory", "wanted_items/skeleton.json");
    private List<Category> categories = List.of();

    void reload(MinecraftServer server, ResourceProvider resources) {
        try (var reader = resources.getResourceOrThrow(RESOURCE).openAsReader()) {
            var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            categories = Category.CODEC.listOf().parse(ops, JsonParser.parseReader(reader))
                    .getOrThrow(IllegalArgumentException::new);
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalArgumentException("无法加载背囊想要表 " + RESOURCE, failure);
        }
    }

    private Category category(ItemStack stack) {
        for (Category category : categories) {
            if (category.matches(stack)) {
                return category;
            }
        }
        return null;
    }

    int allowance(Mob mob, List<ItemStack> slots, ItemStack offered) {
        Category wanted = category(offered);
        if (wanted == null) {
            return 0;
        }
        int remaining = wanted.limit();
        for (ItemStack hand : List.of(mob.getMainHandItem(), mob.getOffhandItem())) {
            if (category(hand) == wanted) {
                remaining -= hand.getCount();
            }
        }
        for (ItemStack stack : slots) {
            if (category(stack) == wanted) {
                remaining -= stack.getCount();
            }
        }
        return Math.clamp(remaining, 0, offered.getCount());
    }

    private record Category(List<Ingredient> items, int limit) {
        private static final Codec<Category> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ingredient.CODEC.listOf(1, Integer.MAX_VALUE).fieldOf("items").forGetter(Category::items),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("limit").forGetter(Category::limit)
        ).apply(instance, Category::new));

        boolean matches(ItemStack stack) {
            return !stack.isEmpty() && items.stream().anyMatch(ingredient -> ingredient.test(stack));
        }
    }
}
