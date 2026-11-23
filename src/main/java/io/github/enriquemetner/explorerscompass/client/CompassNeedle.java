package io.github.enriquemetner.explorerscompass.client;

import io.github.enriquemetner.explorerscompass.network.ClientPacketHandlers;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.enriquemetner.explorerscompass.items.CompassSearchData;
import io.github.enriquemetner.explorerscompass.items.CompassState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ClampedModelPredicateProvider;
import net.minecraft.client.item.CompassAnglePredicateProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;

/** Needle state belongs to a stack and a render context, never to the shared item type. */
@Environment(EnvType.CLIENT)
public final class CompassNeedle implements ClampedModelPredicateProvider {
    private final Map<ItemStack, Map<Object, RenderAnimation>> animations = new WeakHashMap<>();
    private ClientWorld lastWorld;
    public void clear() { animations.clear(); lastWorld = null; }
    @Override public float unclampedCall(ItemStack stack, ClientWorld world, LivingEntity living, int seed) {
        var context = CompassRenderContexts.resolve(stack, world, living, seed);
        if (context != null) world = context.world();
        if (world == null) world = MinecraftClient.getInstance().world;
        if (lastWorld != world) { clear(); lastWorld = world; }
        if (world == null) return 0;
        Object key = context == null ? "unlocated:" + seed : context.animationKey();
        Map<Object, RenderAnimation> perContext = animations.computeIfAbsent(stack, ignored -> new HashMap<>());
        if (!perContext.containsKey(key) && perContext.size() >= 32) perContext.clear();
        RenderAnimation animation = perContext.computeIfAbsent(key, ignored -> new RenderAnimation());
        NbtCompound data = CompassSearchData.pointingData(stack.getNbt());
        Identifier dimension = data == null ? null : Identifier.tryParse(data.getString("FoundDimension"));
        if (dimension == null && data != null && io.github.enriquemetner.explorerscompass.ExplorersCompass.allowedStructureIDsToDimensionIDs != null) {
            Identifier structure = Identifier.tryParse(data.getString("StructureID"));
            if (structure != null) {
                var dimensions = io.github.enriquemetner.explorerscompass.ExplorersCompass.allowedStructureIDsToDimensionIDs.get(structure);
                if (dimensions.stream().distinct().count() == 1) dimension = dimensions.get(0);
            }
        }
        var currentDimension = world.getRegistryKey().getValue();
        CompassProjection projection = ClientPacketHandlers.showNetherEquivalent && data != null
                ? CompassProjection.between(dimension, currentDimension, data.getInt("FoundX"), data.getInt("FoundZ"))
                : null;
        boolean pointing = context != null && data != null && data.getInt("State") == CompassState.FOUND.getID()
                && (currentDimension.equals(dimension) || projection != null);
        var viewer = MinecraftClient.getInstance().player;
        if (projection == null && context != null && viewer != null && context.world() == viewer.getWorld()
                && context.smooth() && context.animationKey().equals(viewer.getUuid())
                && context.position().equals(viewer.getPos()) && context.yaw() == viewer.getYaw()) {
            animation.target = pointing
                    ? GlobalPos.create(world.getRegistryKey(), new BlockPos(data.getInt("FoundX"), 0, data.getInt("FoundZ")))
                    : null;
            return animation.vanilla.unclampedCall(stack, world, viewer, seed);
        }

        if (!pointing) return animation.custom.aimless(world.getTime());
        double targetX = projection == null ? data.getInt("FoundX") + 0.5 : projection.x();
        double targetZ = projection == null ? data.getInt("FoundZ") + 0.5 : projection.z();
        double bearing = Math.atan2(targetZ - context.position().z, targetX - context.position().x) / (2 * Math.PI);
        return animation.custom.aimed(world.getTime(), 0.75 - context.yaw() / 360, bearing, context.smooth());
    }

    private static final class RenderAnimation {
        private GlobalPos target;
        private final CompassAnglePredicateProvider vanilla = new CompassAnglePredicateProvider((world, stack, entity) -> target);
        private final NeedleAnimation custom = new NeedleAnimation();
    }
}
