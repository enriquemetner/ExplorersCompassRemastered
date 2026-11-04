package com.chaosthedude.explorerscompass.workers;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import java.nio.file.Path;
import java.util.*;
import java.time.Instant;
import static net.minecraft.server.command.CommandManager.*;

/** Benchmark branches only. No player inventory, saved visits, or search sessions are modified. */
public final class BenchmarkAutomation {
    private static final Map<MinecraftServer, Run> RUNS = new IdentityHashMap<>();
    private static final Map<String, Run> SEARCHES = new HashMap<>();
    private record Target(ServerWorld world, Identifier structure) {}
    private static final List<String> SUITE = List.of("minecraft:buried_treasure", "minecraft:mineshaft_mesa",
            "minecraft:mansion", "graveyard:lich_prison", "repurposed_structures:ancient_city_nether",
            "repurposed_structures:monument_nether", "repurposed_structures:mansion_desert",
            "repurposed_structures:mineshaft_ocean", "dungeons_arise:infested_temple",
            "dungeons_arise:heavenly_conqueror", "betterstrongholds:stronghold");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("compassbench").requires(s -> s.hasPermissionLevel(2))
                .then(argument("repeats", IntegerArgumentType.integer(1, 100))
                    .then(argument("radius", IntegerArgumentType.integer(100, 30000)).executes(c -> start(c.getSource(),
                            IntegerArgumentType.getInteger(c,"repeats"), IntegerArgumentType.getInteger(c,"radius")))))
                .then(literal("status").executes(c -> {
                    Run run=RUNS.get(c.getSource().getServer());
                    c.getSource().sendFeedback(() -> Text.literal(run==null ? "No benchmark running." : run.status()),false); return 1;
                }))
                .then(literal("stop").executes(c -> { Run run=RUNS.get(c.getSource().getServer());
                    if(run!=null) run.stop("stopped"); return 1; })));
    }
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> { Run run=RUNS.get(server); if(run!=null) run.tick(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { Run run=RUNS.get(server); if(run!=null) run.stop("server_stopping"); });
    }
    private static int start(ServerCommandSource source,int repeats,int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player=source.getPlayerOrThrow();
        if(RUNS.containsKey(source.getServer())) {source.sendError(Text.literal("A benchmark is already running; use /compassbench stop."));return 0;}
        if(!player.isCreative()) {source.sendError(Text.literal("Use a creative test world."));return 0;}
        List<Target> targets=new ArrayList<>();
        for(String name:SUITE) {
            Identifier id=new Identifier(name);
            for(ServerWorld world:source.getServer().getWorlds()) {
                Structure structure=world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(id);
                if(structure!=null && !world.getChunkManager().getStructurePlacementCalculator()
                        .getPlacements(StructureUtils.getEntryForStructure(world,structure)).isEmpty()) {
                    targets.add(new Target(world,id));break;
                }
            }
        }
        if(targets.isEmpty()) {source.sendError(Text.literal("No suite structures have placements in this world."));return 0;}
        Run run=new Run(player,targets,repeats,radius);
        RUNS.put(source.getServer(),run);
        source.sendFeedback(() -> Text.literal("Benchmark started: "+targets.size()+" structures, "+repeats+" searches each. Keep the game unpaused. Results: "+run.file),false);
        return 1;
    }
    static int sampleLimit(String id,int normal) {return SEARCHES.containsKey(id) ? Integer.MAX_VALUE : normal;}
    static Boolean visited(String id,Identifier structure,ChunkPos chunk) {
        return SEARCHES.containsKey(id) ? false : null;
    }
    static boolean success(String id,BlockPos result) {
        Run run=SEARCHES.get(id);if(run==null)return false;
        SearchBenchmark.finish(id,"found",result);
        run.complete();return true;
    }
    static boolean failure(String id) {
        Run run=SEARCHES.get(id);if(run==null)return false;
        run.manager.pop();
        if(!run.manager.start()) run.complete();
        return true;
    }
    static Path output(String id,Path normal) {Run run=SEARCHES.get(id);return run==null ? normal : run.file;}
    static Object[] metadata(String id) {
        Run run=SEARCHES.get(id);return run==null ? null : new Object[]{"origins",run.iteration+1,
                run.target().world.getRegistryKey().getValue(),run.target().world.getSeed(),run.radius,120};
    }
    private static final class Run {
        final ServerPlayerEntity player;final List<Target> targets;final int repeats,radius;
        final Path file;
        SearchWorkerManager manager;String id;long started;int index,iteration;boolean waiting;
        Run(ServerPlayerEntity p,List<Target> targets,int repeats,int radius) {
            player=p;this.targets=targets;this.repeats=repeats;this.radius=radius;
            file=FabricLoader.getInstance().getGameDir().resolve("logs/explorerscompass/automated/")
                    .resolve("suite-"+Instant.now().toString().replace(':','-')+".csv");
        }
        Target target(){return targets.get(index);}
        String status(){if(index>=targets.size())return "Finishing benchmark.";return "Structure "+(index+1)+"/"+targets.size()+", search "+(iteration+1)+"/"+repeats+": "+target().structure;}
        void tick() {
            if(player.isDisconnected()) {stop("disconnected");return;}
            if(waiting && System.nanoTime()-started>120_000_000_000L) {stopAttempt("timeout");return;}
            if(waiting)return;
            if(index>=targets.size()) {stop("complete");return;}
            Target target=target();
            // Origins are repeatable, independent of player spawn, movement and the structure found previously.
            BlockPos origin=BenchmarkOrigins.at(iteration);
            manager=new SearchWorkerManager();id=manager.benchmarkId();SEARCHES.put(id,this);
            waiting=true;started=System.nanoTime();
            Structure structure=StructureUtils.getStructureForID(target.world,target.structure);
            manager.createWorkers(target.world,player,new ItemStack(ExplorersCompass.EXPLORERS_COMPASS_ITEM),
                    List.of(structure),target.structure,true,true,origin);
            manager.setRadiusLimit(radius);
            player.sendMessage(Text.literal(status()),false);
            if(!manager.start()) complete();
        }
        void complete() {
            if(!waiting)return;
            SEARCHES.remove(id);manager.stop();waiting=false;
            iteration++;
            if(iteration>=repeats){iteration=0;index++;}
        }
        void stopAttempt(String reason) {SearchBenchmark.finish(id,reason,null);complete();}
        void stop(String reason) {
            if(waiting){SearchBenchmark.finish(id,reason,null);SEARCHES.remove(id);manager.stop();}
            RUNS.remove(player.getServer());
            player.sendMessage(Text.literal("Benchmark "+reason+". Results: "+file.toAbsolutePath()),false);
        }
    }
}
