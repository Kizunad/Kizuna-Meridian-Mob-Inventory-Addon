package dev.kizuna.meridian.inventory;

import dev.kizuna.bs3d.weights.protocol.pb.WeightsProtocol.Status;
import dev.kizuna.bs3d.weights.protocol.pb.WeightsProtocol.WeightState;
import dev.kizuna.bs3d.weights.server.Burden;
import dev.kizuna.bs3d.weights.server.BurdenSource;
import dev.kizuna.bs3d.weights.server.CarriedStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

public final class MobBurden {
    static final long PICKUP_GRAMS = 20_000;
    private static final double REFERENCE_GRAMS = 40_000;
    private static final double INVALID_SPEED_FACTOR = 0.25;
    private static final int UPDATE_TICKS = 20;
    private static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath("meridian_mob_inventory", "burden");

    private MobBurden() {
    }

    static WeightState measure(ServerLevel level, Mob mob, List<ItemStack> slots) {
        var roots = new ArrayList<CarriedStack>();
        for (int slot = 0; slot < slots.size(); slot++) {
            BurdenSource source = slot < MobInventory.ROW_SIZE ? BurdenSource.HOTBAR : BurdenSource.MAIN;
            addRoot(roots, slots.get(slot), source);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            BurdenSource source = switch (slot) {
                case MAINHAND -> BurdenSource.HOTBAR;
                case OFFHAND -> BurdenSource.OFFHAND;
                default -> BurdenSource.ARMOR;
            };
            addRoot(roots, mob.getItemBySlot(slot), source);
        }
        return Burden.measure(level, roots);
    }

    private static void addRoot(List<CarriedStack> roots, ItemStack stack, BurdenSource source) {
        if (!stack.isEmpty()) {
            roots.add(new CarriedStack(stack, source));
        }
    }

    public static void tick(ServerLevel level, Mob mob) {
        if ((mob.tickCount + mob.getId()) % UPDATE_TICKS == 0) {
            update(level, mob);
        }
    }

    static void update(ServerLevel level, Mob mob) {
        InventoryState state = MobInventories.state(mob);
        double factor = INVALID_SPEED_FACTOR;
        if (state.available()) {
            WeightState weight = measure(level, mob, state.inventory().snapshot());
            if (weight.getStatus() == Status.OK) {
                factor = 1 / (1 + weight.getEffectiveGrams() / REFERENCE_GRAMS);
                state.clearWeightDiagnostic();
            } else {
                state.reportInvalidWeight(mob);
            }
        }
        var speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (factor == 1) {
            speed.removeModifier(SPEED_ID);
        } else {
            var operation = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            var modifier = new AttributeModifier(SPEED_ID, factor - 1, operation);
            speed.addOrUpdateTransientModifier(modifier);
        }
    }
}
