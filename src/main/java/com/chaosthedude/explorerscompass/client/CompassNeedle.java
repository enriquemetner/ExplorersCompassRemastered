package com.chaosthedude.explorerscompass.client;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import com.chaosthedude.explorerscompass.util.CompassSearchData;
import com.chaosthedude.explorerscompass.util.CompassState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ClampedModelPredicateProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

/** Needle state belongs to a stack and a render context, never to the shared item type. */
@Environment(EnvType.CLIENT)
public final class CompassNeedle implements ClampedModelPredicateProvider {
    private final Map<ItemStack, Map<Object, NeedleAnimation>> animations = new WeakHashMap<>();
    private ClientWorld lastWorld;
    public void clear() { animations.clear(); lastWorld = null; }
    @Override public float unclampedCall(ItemStack stack, ClientWorld world, LivingEntity living, int seed) {
        var context = CompassRenderContexts.resolve(stack, world, living, seed);
        if (context != null) world = context.world();
        if (world == null) world = MinecraftClient.getInstance().world;
        if (lastWorld != world) { clear(); lastWorld = world; }
        if (world == null) return 0;
        Object key = context == null ? "unlocated:" + seed : context.animationKey();
        Map<Object, NeedleAnimation> perContext = animations.computeIfAbsent(stack, ignored -> new HashMap<>());
        if (!perContext.containsKey(key) && perContext.size() >= 32) perContext.clear();
        NeedleAnimation animation = perContext.computeIfAbsent(key, ignored -> new NeedleAnimation());
        NbtCompound data = CompassSearchData.pointingData(stack.getNbt());
        Identifier dimension = data == null ? null : Identifier.tryParse(data.getString("FoundDimension"));
        if (dimension == null && data != null && com.chaosthedude.explorerscompass.ExplorersCompass.allowedStructureIDsToDimensionIDs != null) {
            Identifier structure = Identifier.tryParse(data.getString("StructureID"));
            if (structure != null) {
                var dimensions = com.chaosthedude.explorerscompass.ExplorersCompass.allowedStructureIDsToDimensionIDs.get(structure);
                if (dimensions.stream().distinct().count() == 1) dimension = dimensions.get(0);
            }
        }
        boolean pointing = context != null && data != null && data.getInt("State") == CompassState.FOUND.getID()
                && dimension != null && dimension.equals(world.getRegistryKey().getValue());
        double angle;
        if (pointing) {
            double targetX = data.getInt("FoundX") + 0.5;
            double targetZ = data.getInt("FoundZ") + 0.5;
            double target = Math.atan2(targetZ - context.position().z, targetX - context.position().x);
            angle = (Math.PI - ((context.yaw() - 90) * Math.PI / 180 - target)) / (Math.PI * 2);
        } else {
            angle = 0; // Random target and wobble are maintained separately for each stack/context.
        }
        return animation.update(world.getTime(), angle, !pointing, context == null || context.smooth());
    }
}
