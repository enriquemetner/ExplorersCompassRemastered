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
import com.chaosthedude.explorerscompass.util.VisitedStructures;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class UnlockingCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("explorerscompass").requires(source -> source.hasPermissionLevel(2))
                .then(literal("reload").executes(context -> {
                    int count = UnlockingRules.reload(context.getSource().getServer());
                    if (count < 0) throw error("Could not reload Explorer's Compass unlocking rules. See the server log; active rules and player data were kept.");
                    UnlockReloads.commandReload(context.getSource().getServer());
                    context.getSource().sendFeedback(() -> Text.literal(com.chaosthedude.explorerscompass.config.ExplorersCompassConfig.relockOnReload
                            ? "Reloaded Explorer's Compass unlocking rules from file and relocked all structures."
                            : "Reloaded Explorer's Compass unlocking rules from file."), false);
                    context.getSource().sendFeedback(() -> Text.literal("Note: reloading Explorer's Compass data is a developer convenience tool and not recommended for use on live servers.").formatted(Formatting.GOLD), false);
                    return count;
                }))
                .then(literal("lock").then(structureTargets(false)))
                .then(literal("unlock").then(structureTargets(true)))
                .then(literal("visits")
                    .then(literal("list").then(playerArgument()
                        .then(literal("structure").then(visitArgument(false).executes(c -> listVisits(c, false))))
                        .then(literal("structureSet").then(visitArgument(true).executes(c -> listVisits(c, true))))))
                    .then(literal("clear").then(playerArgument()
                        .then(literal("everything").executes(c -> reportCleared(c, VisitedStructures.get(c.getSource().getServer()).clear(player(c), null))))
                        .then(literal("structure").then(visitRanges(false)))
                        .then(literal("structureSet").then(visitRanges(true)))))));

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
                .then(literal("all").executes(context -> change(context, unlock, "all")))
                .then(literal("structure").then(structureArgument().executes(context -> change(context, unlock, "structure"))))
                .then(literal("structureSet").then(argument("id", IdentifierArgumentType.identifier())
                        .suggests((context, builder) -> CommandSource.suggestIdentifiers(
                                context.getSource().getRegistryManager().get(RegistryKeys.STRUCTURE_SET).getIds(), builder))
                        .executes(context -> change(context, unlock, "structureSet"))))
                .then(literal("rule").then(argument("rule", StringArgumentType.string())
                        .suggests((context, builder) -> CommandSource.suggestMatching(UnlockingRules.get().ruleNames(), builder))
                        .executes(context -> change(context, unlock, "rule"))));
    }

    private static int change(CommandContext<ServerCommandSource> context, boolean unlock, String selector) throws CommandSyntaxException {
        var server = context.getSource().getServer();
        UUID player = player(context);
        UnlockedStructures state = UnlockedStructures.get(server);
        Set<Identifier> targets;
        switch (selector) {
            case "all" -> targets = unlock ? server.getRegistryManager().get(RegistryKeys.STRUCTURE).getIds() : state.getStructures(player);
            case "rule" -> {
                targets = UnlockingRules.get().ruleTargets(StringArgumentType.getString(context, "rule"));
                if (targets == null) throw error("Unknown rule");
            }
            case "structureSet" -> {
                Identifier id = IdentifierArgumentType.getIdentifier(context, "id");
                if (!server.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).containsId(id)) throw error("Unknown structure set: " + id);
                targets = UnlockingRules.setMembers(server, id);
            }
            default -> {
                Identifier id = IdentifierArgumentType.getIdentifier(context, "id");
                requireNotBlacklisted(id);
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
        Set<UUID> affected = unlock ? Set.of(player) : PartySharing.members(server, player);
        if (!unlock && selector.equals("all")) {
            Set<Identifier> all = new HashSet<>(targets);
            affected.forEach(member -> all.addAll(state.getStructures(member)));
            targets = commandStructures(all);
        }
        int changed = 0;
        for (UUID member : affected) {
            changed += unlock ? state.add(member, targets) : state.remove(member, targets);
            if (!unlock) {
                ServerPlayerEntity partyPlayer = server.getPlayerManager().getPlayer(member);
                if (partyPlayer != null) {
                    ExplorersCompass.EXPLORERS_COMPASS_ITEM.cancelSearch(partyPlayer, -1);
                    for (int slot = 0; slot < partyPlayer.getInventory().size(); slot++)
                        ExplorersCompass.EXPLORERS_COMPASS_ITEM.validateTarget(partyPlayer, partyPlayer.getInventory().getStack(slot));
                }
            }
        }
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(player);
        if (unlock && changed > 0) {
            if (online != null) UnlockNotifications.newlyAdded(online, changed);
            else state.queueNotification(player, added);
        }
        if (!unlock && changed > 0) {
            if (online != null) ExplorersCompass.EXPLORERS_COMPASS_ITEM.cancelSearch(online, -1);
        }
        if (unlock) PartySharing.synchronize(server, false);
        for (UUID member : PartySharing.members(server, player)) {
            ServerPlayerEntity recipient = server.getPlayerManager().getPlayer(member);
            if (recipient != null && recipient.networkHandler.isConnectionOpen())
                com.chaosthedude.explorerscompass.network.PartyRefreshPacket.send(recipient);
        }
        int totalChanged = changed;
        context.getSource().sendFeedback(() -> Text.literal((unlock ? "Unlocked " : "Locked ") + totalChanged
                + " saved structure entries for " + (affected.size() > 1 ? "the OPAC party (" + affected.size() + " members)" : player)
                + "."), true);
        return changed;
    }

    private static RequiredArgumentBuilder<ServerCommandSource, Identifier> visitArgument(boolean set) {
        return argument("id", IdentifierArgumentType.identifier()).suggests((c, b) -> {
            if (!set) return CommandSource.suggestIdentifiers(commandStructures(c.getSource().getRegistryManager().get(RegistryKeys.STRUCTURE).getIds()), b);
            return CommandSource.suggestIdentifiers(c.getSource().getRegistryManager().get(RegistryKeys.STRUCTURE_SET).getIds().stream()
                .filter(id -> !StructureUtils.structureIdIsBlacklisted(id)
                    && !commandStructures(UnlockingRules.setMembers(c.getSource().getServer(), id)).isEmpty()), b);
        });
    }

    private static Set<Identifier> visitTargets(CommandContext<ServerCommandSource> c, boolean set) throws CommandSyntaxException {
        Identifier id = IdentifierArgumentType.getIdentifier(c, "id");
        requireNotBlacklisted(id);
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
            int x = v.chunkX() * 16 + 8, z = v.chunkZ() * 16 + 8;
            Text coords = Text.literal("[" + x + ", ~, " + z + "]").styled(style -> style.withColor(Formatting.GREEN)
                .withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.SUGGEST_COMMAND, "/tp " + x + " ~ " + z))
                .withHoverEvent(new net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT, Text.translatable("chat.coordinates.tooltip"))));
            Text line = Text.literal(i + ": " + v.structure() + " (" + v.dimension() + ") ").append(coords);
            c.getSource().sendFeedback(() -> line, false);
        }
        if (entries.isEmpty()) c.getSource().sendFeedback(() -> Text.literal("No matching visits."), false);
        return entries.size();
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
        return structures.stream().filter(id -> !StructureUtils.structureIdIsBlacklisted(id)).collect(Collectors.toSet());
    }

    static void requireNotBlacklisted(Identifier id) throws CommandSyntaxException {
        if (StructureUtils.structureIdIsBlacklisted(id)) throw error("Unknown structure: " + id);
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
