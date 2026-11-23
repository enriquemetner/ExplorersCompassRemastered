package io.github.enriquemetner.explorerscompass.mixins.storage;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

@Mixin(ThreadedAnvilChunkStorage.class)
public interface StructureChunkStorageAccessor {
    @Accessor("currentChunkHolders")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> explorerscompass$currentChunkHolders();

    @Accessor("chunksToUnload")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> explorerscompass$chunksToUnload();

    @Invoker("getCurrentChunkHolder")
    ChunkHolder explorerscompass$getCurrentChunkHolder(long chunk);

    @Invoker("updateChunkNbt")
    NbtCompound explorerscompass$updateChunkNbt(NbtCompound nbt);
}
