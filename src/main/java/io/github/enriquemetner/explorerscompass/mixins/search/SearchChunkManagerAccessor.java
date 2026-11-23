package io.github.enriquemetner.explorerscompass.mixins.search;

import com.mojang.datafixers.util.Either;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkManager.class)
public interface SearchChunkManagerAccessor {
    @Invoker("getChunkFuture")
    CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> explorerscompass$requestChunk(
            int x, int z, ChunkStatus status, boolean create);
}
