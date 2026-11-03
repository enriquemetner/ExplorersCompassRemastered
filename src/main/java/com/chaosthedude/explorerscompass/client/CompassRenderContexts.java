package com.chaosthedude.explorerscompass.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/** Client integration point for displays that render items without an entity holder.
 * Wrap the display's complete item render (including model lookup) in a push scope.
 * Yaw is in degrees, following Minecraft entity yaw; animationKey must be stable per display.
 */
@Environment(EnvType.CLIENT)
public final class CompassRenderContexts {
    public record Context(ClientWorld world, Vec3d position, double yaw, Object animationKey, boolean smooth) {
        public Context { Objects.requireNonNull(world); Objects.requireNonNull(position); Objects.requireNonNull(animationKey); }
    }
    @FunctionalInterface public interface Provider {
        Context resolve(ItemStack stack, ClientWorld world, LivingEntity holder, int seed);
    }
    public interface Scope extends AutoCloseable { @Override void close(); }
    private static final ThreadLocal<Deque<Context>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final List<Provider> PROVIDERS = new ArrayList<>();
    private CompassRenderContexts() {}
    public static Scope push(Context context) {
        SCOPES.get().push(context);
        return () -> SCOPES.get().pop();
    }
    /** Register on the client thread. Return null from a provider to leave a render unhandled. */
    public static void register(Provider provider) { PROVIDERS.add(Objects.requireNonNull(provider)); }
    public static Context entity(Entity entity, double yaw, boolean smooth) {
        return entity != null && entity.getWorld() instanceof ClientWorld world
                ? new Context(world, entity.getPos(), yaw, entity.getUuid(), smooth) : null;
    }
    static Context resolve(ItemStack stack, ClientWorld world, LivingEntity living, int seed) {
        if (!SCOPES.get().isEmpty()) return SCOPES.get().peek();
        for (Provider provider : PROVIDERS) {
            Context context = provider.resolve(stack, world, living, seed);
            if (context != null) return context;
        }
        Entity holder = stack.getHolder();
        if (holder instanceof ItemFrameEntity frame) {
            return entity(frame, frame.getBodyYaw(), false);
        }
        if (holder != null) return entity(holder, holder.getBodyYaw(), true);
        return living == null ? null : entity(living, living.getYaw(), true);
    }
}
