package io.github.enriquemetner.explorerscompass.network.search;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.util.ItemUtils;
import java.util.function.Consumer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

final class SearchDispatch {
    private SearchDispatch() {}

    static void withHeldCompass(MinecraftServer server, ServerPlayerEntity player, int requestId,
            Consumer<ItemStack> start) {
        server.execute(() -> {
            var stack = ItemUtils.getHeldItem(player, ExplorersCompass.EXPLORERS_COMPASS_ITEM);
            if (stack.isEmpty()) {
                SearchResultPacket.send(player, requestId, SearchResultPacket.REJECTED, 0);
                return;
            }
            start.accept(stack);
        });
    }
}
