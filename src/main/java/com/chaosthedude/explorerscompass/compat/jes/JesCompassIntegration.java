package com.chaosthedude.explorerscompass.compat.jes;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen;
import com.chaosthedude.explorerscompass.util.ItemUtils;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import java.lang.reflect.Proxy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

/** Registers the compass provider exposed by the optional JES client integration. */
public final class JesCompassIntegration {
    private static final String COMPASS_LINK = "com.finndog.justenoughstructures.client.CompassLink";

    private JesCompassIntegration() {}

    public static boolean register() {
        try {
            // Resolve the optional interface at runtime so JES is never required to load this mod.
            Class<?> link = Class.forName(COMPASS_LINK);
            Object provider = Proxy.newProxyInstance(link.getClassLoader(), new Class<?>[] {link},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "icon" -> ExplorersCompass.STILL_COMPASS_ICON;
                        case "holding" -> !usableCompass((PlayerEntity) args[0]).isEmpty();
                        case "open" -> open((PlayerEntity) args[0], (Identifier) args[1]);
                        case "status" -> status((PlayerEntity) args[0], (Identifier) args[1]);
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "Explorer's Compass JES integration";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            link.getMethod("set", link).invoke(null, provider);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            ExplorersCompass.LOGGER.warn("Could not register the Explorer's Compass provider for Just Enough Structures", e);
            return false;
        }
    }

    private static ItemStack usableCompass(PlayerEntity player) {
        if (player == null || player.isSneaking()) return ItemStack.EMPTY;
        var compass = ExplorersCompass.EXPLORERS_COMPASS_ITEM;
        return ItemUtils.getHeldItem(player, compass);
    }

    private static boolean open(PlayerEntity player, Identifier structure) {
        MinecraftClient client = MinecraftClient.getInstance();
        ItemStack stack = usableCompass(player);
        if (stack.isEmpty() || client.player != player || client.interactionManager == null || structure == null) return false;

        Hand hand = player.getMainHandStack() == stack ? Hand.MAIN_HAND : Hand.OFF_HAND;
        client.interactionManager.interactItem(player, hand);
        if (client.currentScreen instanceof ExplorersCompassScreen screen) {
            screen.selectStructureAfterSync(structure);
            return true;
        }
        return false;
    }

    private static Text status(PlayerEntity player, Identifier structure) {
        ItemStack stack = usableCompass(player);
        if (stack.isEmpty() || structure == null || !stack.hasNbt()
                || !structure.equals(Identifier.tryParse(stack.getNbt().getString("StructureID")))) return null;
        var compass = ExplorersCompass.EXPLORERS_COMPASS_ITEM;
        var state = compass.getState(stack);
        if (state == null) return null;
        return switch (state) {
            case INACTIVE -> null;
            case SEARCHING -> Text.translatable("screen.justenoughstructures.compass_searching");
            case NOT_FOUND -> Text.translatable("screen.justenoughstructures.compass_not_found");
            case FOUND -> compass.shouldDisplayDistance(stack) && compass.isInFoundDimension(stack, player.getWorld())
                    ? Text.translatable("screen.justenoughstructures.compass_found",
                            StructureUtils.getHorizontalDistanceToLocation(player, compass.getFoundStructureX(stack), compass.getFoundStructureZ(stack)))
                    : Text.literal("Structure located");
        };
    }
}
