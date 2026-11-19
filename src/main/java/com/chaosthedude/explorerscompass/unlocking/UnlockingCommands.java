package com.chaosthedude.explorerscompass.unlocking;

import java.io.Reader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.util.CompassTeleport;
import com.chaosthedude.explorerscompass.util.StructureDestination;
import com.chaosthedude.explorerscompass.util.VisitedStructures;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class UnlockingCommands {
    private static final java.util.regex.Pattern REPORT_DETAILS = java.util.regex.Pattern.compile(
            "(?i)(?<=\\bline )\\d+|(?<=\\blines )\\d+(?:, \\d+)*|(?<=integer >= )\\d+|(?<=\\bcolumn )\\d+"
            + "|(?<=\\bpath )\\$[^\\r\\n]*|\\b(?:unlocking_rules|search_costs)\\.json\\b"
            + "|\"(?:\\\\.|[^\"\\\\])*\""
            + "|(?<=Unknown \"default\": )[^\\r\\n]+|(?<=condition type )[^\\r\\n]+"
            + "|(?<=match selector )[^\\r\\n]+|(?<=ID or pattern )[^\\r\\n]+"
            + "|(?<=Invalid identifier )[^\\r\\n]+|(?<=Unknown advancement )[^\\s]+"
            + "|(?<=Unknown structure )[^\\s]+|(?<=structure set )[^\\s]+(?=$)"
            + "|(?<=: )[^\\s]+(?= is listed more than once)|(?<=Both a structure and structure set use )[^;]+");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var command = dispatcher.register(literal("explorerscompass").requires(source -> source.hasPermissionLevel(2))
                .then(literal("report").executes(c -> report(c.getSource(), null))
                        .then(literal("error").executes(c -> report(c.getSource(), UnlockingRules.Severity.ERROR)))
                        .then(literal("warning").executes(c -> report(c.getSource(), UnlockingRules.Severity.WARNING))))
                .then(literal("lock").then(structureTargets(false)))
                .then(literal("unlock").then(structureTargets(true)))
                .then(literal("tp").executes(c -> {
                    CompassTeleport.toHeldTarget(c.getSource().getPlayerOrThrow());
                    return 1;
                }).then(structureArgument().then(argument("x", IntegerArgumentType.integer(-30_000_000, 29_999_999))
                        .then(argument("z", IntegerArgumentType.integer(-30_000_000, 29_999_999)).executes(c -> {
                            int x = IntegerArgumentType.getInteger(c, "x");
                            int z = IntegerArgumentType.getInteger(c, "z");
                            CompassTeleport.toCoordinates(c.getSource().getPlayerOrThrow(),
                                    IdentifierArgumentType.getIdentifier(c, "id"), new BlockPos(x, 0, z));
                            return 1;
                        })))))
                .then(literal("visits")
                    .then(literal("list").then(playerArgument()
                        .then(literal("structure").then(visitArgument(false).executes(c -> listVisits(c, false))))
                        .then(literal("structureSet").then(visitArgument(true).executes(c -> listVisits(c, true))))))
                    .then(literal("clear").then(playerArgument()
                        .then(literal("everything").executes(c -> reportCleared(c, VisitedStructures.get(c.getSource().getServer()).clear(player(c), null))))
                        .then(literal("structure").then(visitRanges(false)))
                        .then(literal("structureSet").then(visitRanges(true)))))));
        dispatcher.register(literal("ecomp").requires(command::canUse).redirect(command));
    }

    static java.util.List<UnlockingRules.Diagnostic> combinedReport(UnlockingRules.Severity severity) {
        return java.util.stream.Stream.concat(UnlockingRules.report(severity).stream(),
                com.chaosthedude.explorerscompass.config.SearchCosts.report(severity).stream()).toList();
    }

    private static int report(ServerCommandSource source, UnlockingRules.Severity severity) {
        var diagnostics = combinedReport(severity);
        if (diagnostics.isEmpty()) {
            String kind = severity == null ? "errors or warnings"
                    : severity == UnlockingRules.Severity.ERROR ? "errors" : "warnings";
            source.sendFeedback(() -> Text.literal("No " + kind + " in unlocking rules or search costs."), false);
        }
        for (var diagnostic : diagnostics) {
            source.sendFeedback(() -> formatDiagnostic(diagnostic), false);
        }
        return diagnostics.size();
    }

    static Text formatDiagnostic(UnlockingRules.Diagnostic diagnostic) {
        boolean error = diagnostic.severity() == UnlockingRules.Severity.ERROR;
        var text = Text.literal("").formatted(Formatting.WHITE);
        text.append(Text.literal(error ? "[ERROR]" : "[WARNING]").formatted(error ? Formatting.RED : Formatting.GOLD));
        text.append(" ");
        String message = diagnostic.message();
        var details = REPORT_DETAILS.matcher(message);
        int offset = 0;
        while (details.find()) {
            text.append(message.substring(offset, details.start()));
            String detail = details.group();
            if (detail.startsWith("\"")) {
                text.append("\"").append(Text.literal(detail.substring(1, detail.length() - 1)).formatted(Formatting.YELLOW)).append("\"");
            } else if (detail.matches("\\d+(?:, \\d+)+")) {
                String[] lines = detail.split(", ");
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) text.append(", ");
                    text.append(Text.literal(lines[i]).formatted(Formatting.YELLOW));
                }
            } else {
                text.append(Text.literal(detail).formatted(Formatting.YELLOW));
            }
            offset = details.end();
        }
        return text.append(message.substring(offset));
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> playerArgument() {
        return argument("player", StringArgumentType.word()).suggests((context, builder) ->
                CommandSource.suggestMatching(Arrays.asList(context.getSource().getServer().getPlayerManager().getPlayerNames()), builder));
    }

    private static RequiredArgumentBuilder<ServerCommandSource, Identifier> structureArgument() {
        return argument("id", IdentifierArgumentType.identifier()).suggests((context, builder) ->
                CommandSource.suggestIdentifiers(commandStructures(context.getSource().getRegistryManager().get(RegistryKeys.STRUCTURE).getIds()), builder));
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> structureTargets(boolean unlock) {
        return playerArgument()
                .then(literal("everything").executes(context -> change(context, unlock, "everything")))
                .then(literal("structure").then(structureArgument().executes(context -> change(context, unlock, "structure"))))
                .then(literal("structureSet").then(argument("id", IdentifierArgumentType.identifier())
                        .suggests((context, builder) -> CommandSource.suggestIdentifiers(
                                context.getSource().getRegistryManager().get(RegistryKeys.STRUCTURE_SET).getIds(), builder))
                        .executes(context -> change(context, unlock, "structureSet"))))
                .then(literal("requirement").then(argument("requirement", StringArgumentType.string())
                        .suggests((context, builder) -> CommandSource.suggestMatching(UnlockingRules.get().requirementNames(), builder))
                        .executes(context -> change(context, unlock, "requirement"))));
    }

    private static int change(CommandContext<ServerCommandSource> context, boolean unlock, String selector) throws CommandSyntaxException {
        var server = context.getSource().getServer();
        UUID player = player(context);
        UnlockedStructures state = UnlockedStructures.get(server);
        Set<Identifier> targets;
        switch (selector) {
            case "everything" -> targets = unlock ? server.getRegistryManager().get(RegistryKeys.STRUCTURE).getIds() : state.getStructures(player);
            case "requirement" -> {
                targets = UnlockingRules.get().requirementTargets(StringArgumentType.getString(context, "requirement"));
                if (targets == null) throw error("Unknown requirement");
            }
            case "structureSet" -> {
                Identifier id = IdentifierArgumentType.getIdentifier(context, "id");
                if (!server.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).containsId(id)) throw error("Unknown structure set: " + id);
                targets = UnlockingRules.setMembers(server, id);
            }
            default -> {
                Identifier id = IdentifierArgumentType.getIdentifier(context, "id");
                requireNotExcluded(id);
                if (!server.getRegistryManager().get(RegistryKeys.STRUCTURE).containsId(id)
                        && (unlock || !state.contains(player, id))) throw error("Unknown structure: " + id);
                targets = Set.of(id);
            }
        }
        targets = commandStructures(targets);
        Set<Identifier> added = new HashSet<>();
        if (unlock) {
            added.addAll(targets);
            added.removeAll(state.getStructures(player));
        }
        int changed = unlock ? state.add(player, targets) : state.remove(player, targets);
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(player);
        if (unlock && changed > 0) {
            if (online != null) UnlockNotifications.newlyAdded(online, changed);
            else state.queueNotification(player, added);
        }
        PartySharing.synchronize(server);
        if (!unlock && changed > 0 && online != null && online.networkHandler.isConnectionOpen()) {
            ExplorersCompass.EXPLORERS_COMPASS_ITEM.revalidateAccess(online);
            com.chaosthedude.explorerscompass.network.PartyRefreshPacket.send(online);
        }
        context.getSource().sendFeedback(() -> Text.literal((unlock ? "Unlocked " : "Locked ") + changed
                + " saved structure entries for " + player + "."), true);
        return changed;
    }

    private static RequiredArgumentBuilder<ServerCommandSource, Identifier> visitArgument(boolean set) {
        return argument("id", IdentifierArgumentType.identifier()).suggests((c, b) -> {
            if (!set) return CommandSource.suggestIdentifiers(commandStructures(c.getSource().getRegistryManager().get(RegistryKeys.STRUCTURE).getIds()), b);
            return CommandSource.suggestIdentifiers(c.getSource().getRegistryManager().get(RegistryKeys.STRUCTURE_SET).getIds().stream()
                .filter(id -> !StructureUtils.structureIdIsExcluded(id)
                    && !commandStructures(UnlockingRules.setMembers(c.getSource().getServer(), id)).isEmpty()), b);
        });
    }

    private static Set<Identifier> visitTargets(CommandContext<ServerCommandSource> c, boolean set) throws CommandSyntaxException {
        Identifier id = IdentifierArgumentType.getIdentifier(c, "id");
        requireNotExcluded(id);
        var registry = c.getSource().getRegistryManager();
        if (!set && registry.get(RegistryKeys.STRUCTURE).containsId(id)) return Set.of(id);
        if (!set || !registry.get(RegistryKeys.STRUCTURE_SET).containsId(id)) throw error("Unknown structure: " + id);
        Set<Identifier> members = commandStructures(UnlockingRules.setMembers(c.getSource().getServer(), id));
        if (members.isEmpty()) throw error("Unknown structure set: " + id);
        return members;
    }

    private static int listVisits(CommandContext<ServerCommandSource> c, boolean set) throws CommandSyntaxException {
        var entries = VisitedStructures.get(c.getSource().getServer()).list(player(c), visitTargets(c, set));
        Identifier id = IdentifierArgumentType.getIdentifier(c, "id");
        c.getSource().sendFeedback(() -> Text.literal("Listing visited " + id + " structures:"), false);
        for (int i = 0; i < entries.size(); i++) {
            var v = entries.get(i);
            var world = c.getSource().getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, v.dimension()));
            var chunk = new ChunkPos(v.chunkX(), v.chunkZ());
            var structure = world == null ? null : StructureUtils.getStructureForID(world, v.structure());
            var start = StructureDestination.readStart(world, chunk, structure);
            var target = StructureDestination.refine(StructureDestination.pieces(start),
                    new BlockPos(chunk.getCenterX(), 0, chunk.getCenterZ())).pos();
            Text line = Text.literal(i + ": " + v.structure() + " (" + v.dimension() + ") ")
                    .append(visitCoordinates(v.structure(), target.getX(), target.getZ()));
            c.getSource().sendFeedback(() -> line, false);
        }
        if (entries.isEmpty()) c.getSource().sendFeedback(() -> Text.literal("No matching visits."), false);
        return entries.size();
    }

    static Text visitCoordinates(Identifier id, int x, int z) {
        return Text.literal("[" + x + ", " + z + "]").styled(style -> style.withColor(Formatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/explorerscompass tp " + id + " " + x + " " + z))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("chat.coordinates.tooltip"))));
    }

    private static RequiredArgumentBuilder<ServerCommandSource, Identifier> visitRanges(boolean set) {
        return visitArgument(set)
            .then(literal("everything").executes(c -> clearRange(c, set, "everything")))
            .then(literal("only").then(argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(c -> clearRange(c, set, "only"))))
            .then(literal("until").then(argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(c -> clearRange(c, set, "until"))))
            .then(literal("from").then(argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(c -> clearRange(c, set, "from"))
                .then(literal("until").then(argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(c -> clearRange(c, set, "range"))))));
    }

    private static int clearRange(CommandContext<ServerCommandSource> c, boolean set, String mode) throws CommandSyntaxException {
        UUID player = player(c);
        var state = VisitedStructures.get(c.getSource().getServer());
        var entries = state.list(player, visitTargets(c, set));
        if (mode.equals("everything")) return reportCleared(c, state.remove(player, entries));
        int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "x");
        int first = mode.equals("until") ? 0 : x;
        int last = mode.equals("from") ? entries.size() - 1 : mode.equals("range") ? com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "y") : x;
        if (first < 0 || first >= entries.size() || last < first || last >= entries.size()) throw error("Invalid visit ordinal or range; no visits cleared.");
        return reportCleared(c, state.remove(player, entries.subList(first, last + 1)));
    }

    private static int reportCleared(CommandContext<ServerCommandSource> c, int count) {
        c.getSource().sendFeedback(() -> Text.literal("Cleared " + count + " visited structure instances."), true);
        return count;
    }

    static Set<Identifier> commandStructures(Collection<Identifier> structures) {
        return structures.stream().filter(id -> !StructureUtils.structureIdIsExcluded(id)).collect(Collectors.toSet());
    }

    static void requireNotExcluded(Identifier id) throws CommandSyntaxException {
        if (StructureUtils.structureIdIsExcluded(id)) throw error("Unknown structure: " + id);
    }

    private static UUID player(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "player");
        try { return UUID.fromString(name); } catch (IllegalArgumentException ignored) { }
        var server = context.getSource().getServer();
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(name);
        if (online != null) return online.getUuid();
        // Read cached profiles only; do not perform a blocking account lookup or invent an offline UUID.
        var cache = server.getRunDirectory().toPath().resolve("usercache.json");
        if (Files.exists(cache)) {
            try (Reader reader = Files.newBufferedReader(cache)) {
                for (JsonElement entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                    var profile = entry.getAsJsonObject();
                    if (name.equalsIgnoreCase(profile.get("name").getAsString())) return UUID.fromString(profile.get("uuid").getAsString());
                }
            } catch (Exception e) {
                ExplorersCompass.LOGGER.warn("Could not read cached player names", e);
            }
        }
        throw error("Unknown cached player: " + name + ". Use the player's UUID instead.");
    }

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Text.literal(message)).create();
    }
}
