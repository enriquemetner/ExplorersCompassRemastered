package com.chaosthedude.explorerscompass.config;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.unlocking.UnlockingRuleDocument;
import com.chaosthedude.explorerscompass.unlocking.UnlockingRules;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public record SearchCosts(Type type, int defaultCost, Map<Identifier, Integer> overrides,
                          int initialEnergy, Map<Identifier, Integer> items, boolean allowSearchWithoutXp) {
    public enum Type { XP_LEVELS, XP, ITEMS }

    public static final String DEFAULT_JSON = "{\n  \"type\": \"xpLevels\",\n  \"defaultCost\": 0,\n  \"overrides\": {}\n}\n";
    private static final String ENERGY = "SearchEnergy";
    private static final SearchCosts FREE = new SearchCosts(Type.XP_LEVELS, 0, Map.of(), 0, Map.of(), false);
    private static SearchCosts active = FREE;
    private static SearchCosts remote = FREE;

    public SearchCosts {
        overrides = Map.copyOf(overrides);
        items = Map.copyOf(items);
    }

    public static SearchCosts active() { return active; }
    public static SearchCosts remote() { return remote; }
    public static void setRemote(Type type, int defaultCost, Map<Identifier, Integer> overrides, int initialEnergy, Map<Identifier, Integer> items) {
        remote = new SearchCosts(type, defaultCost, overrides, initialEnergy, items, false);
    }
    public static void clearRemote() { remote = FREE; }

    public static void load(MinecraftServer server) {
        var file = ExplorersCompassConfig.serverConfigFile(server, ExplorersCompassConfig.SEARCH_COSTS);
        try {
            active = parse(Files.readString(file), selector -> UnlockingRules.resolveCostTargets(server, selector),
                    id -> Registries.ITEM.containsId(id) && !id.equals(new Identifier("minecraft", "air")));
        } catch (IOException | IllegalArgumentException error) {
            active = FREE;
            String reason = error instanceof java.nio.file.FileSystemException fs
                    ? java.util.Objects.requireNonNullElse(fs.getReason(), fs.getClass().getSimpleName()) : error.getMessage();
            ExplorersCompass.LOGGER.error("Could not load search_costs.json: {}", reason);
        }
    }

    static SearchCosts parse(String source, Function<String, Set<Identifier>> resolve, Predicate<Identifier> knownItem) {
        var doc = new UnlockingRuleDocument(source);
        JsonObject root = object(doc, doc.root);
        JsonElement typeValue = root.get("type");
        if (typeValue == null || !typeValue.isJsonPrimitive() || !typeValue.getAsJsonPrimitive().isString())
            throw invalid(doc, typeValue == null ? root : typeValue, "Expected \"type\" to be a string");
        Type type = switch (typeValue.getAsString()) {
            case "xp" -> Type.XP;
            case "xpLevels" -> Type.XP_LEVELS;
            case "items" -> Type.ITEMS;
            default -> throw invalid(doc, typeValue, "Unknown cost type " + typeValue);
        };
        Set<String> fields = type == Type.ITEMS ? Set.of("type", "defaultCost", "overrides", "initialEnergy", "items")
                : Set.of("type", "defaultCost", "overrides", "allowSearchWithoutXp");
        for (String key : root.keySet())
            if (!fields.contains(key)) throw invalid(doc, root.get(key), "Unexpected field \"" + key + "\"");

        int cost = number(doc, root, "defaultCost", 0);
        Map<Identifier, Integer> overrides = new LinkedHashMap<>();
        if (root.has("overrides")) {
            var entries = object(doc, root.get("overrides"));
            for (var entry : entries.entrySet()) {
                int amount = number(doc, entries, entry.getKey(), 0);
                Set<Identifier> targets;
                try {
                    targets = resolve.apply(entry.getKey());
                } catch (IllegalArgumentException error) {
                    throw invalid(doc, entry.getValue(), error.getMessage());
                }
                if (targets.isEmpty()) throw invalid(doc, entry.getValue(), "No structures match " + entry.getKey());
                targets.forEach(id -> overrides.put(id, amount));
            }
        }
        if (type == Type.ITEMS) {
            int energy = number(doc, root, "initialEnergy", 0);
            if (!root.has("items")) throw invalid(doc, root, "Missing \"items\"");
            var fuels = object(doc, root.get("items"));
            Map<Identifier, Integer> items = new LinkedHashMap<>();
            for (var entry : fuels.entrySet()) {
                Identifier id = Identifier.tryParse(entry.getKey());
                if (id == null || !knownItem.test(id))
                    throw invalid(doc, entry.getValue(), "Unknown item " + entry.getKey());
                items.put(id, number(doc, fuels, entry.getKey(), 1));
            }
            return new SearchCosts(type, cost, overrides, energy, items, false);
        }
        JsonElement allow = root.get("allowSearchWithoutXp");
        if (allow != null && (!allow.isJsonPrimitive() || !allow.getAsJsonPrimitive().isBoolean()))
            throw invalid(doc, allow, "Expected \"allowSearchWithoutXp\" to be a boolean");
        return new SearchCosts(type, cost, overrides, 0, Map.of(), allow != null && allow.getAsBoolean());
    }

    private static JsonObject object(UnlockingRuleDocument doc, JsonElement element) {
        if (element instanceof JsonObject object) return object;
        throw invalid(doc, element, "Expected a JSON object");
    }

    private static int number(UnlockingRuleDocument doc, JsonObject object, String key, int min) {
        JsonElement value = object.get(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            try {
                int number = value.getAsBigDecimal().intValueExact();
                if (number >= min) return number;
            } catch (ArithmeticException | NumberFormatException ignored) {
                // Report the invalid value with its source location below.
            }
        }
        throw invalid(doc, value == null ? object : value, "Expected an integer >= " + min + " for \"" + key + "\"");
    }

    private static IllegalArgumentException invalid(UnlockingRuleDocument doc, JsonElement value, String reason) {
        return new IllegalArgumentException("line " + doc.line(value) + ": " + reason);
    }

    public boolean showsSearchCosts() {
        return type == Type.ITEMS || defaultCost != 0 || !overrides.isEmpty();
    }

    public int cost(Identifier id) { return overrides.getOrDefault(id, defaultCost); }

    public int energy(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().contains(ENERGY) ? Math.max(0, stack.getNbt().getInt(ENERGY)) : initialEnergy;
    }

    public void initialize(ItemStack stack) {
        if (type == Type.ITEMS && !stack.getOrCreateNbt().contains(ENERGY)) setEnergy(stack, initialEnergy);
    }

    public static int energyAfterConsumingItems(int current, int count, int value) {
        long total = current + (long) count * value;
        return total > Integer.MAX_VALUE ? -1 : (int) total;
    }

    public void setEnergy(ItemStack stack, int energy) { stack.getOrCreateNbt().putInt(ENERGY, energy); }

    public int available(PlayerEntity player, ItemStack stack) {
        return switch (type) {
            case ITEMS -> energy(stack);
            case XP_LEVELS -> player.experienceLevel;
            case XP -> (int) Math.min(Integer.MAX_VALUE, pointsAtLevel(Math.min(32768, player.experienceLevel))
                    + Math.round(player.experienceProgress * player.getNextLevelExperience()));
        };
    }

    public boolean canPay(ServerPlayerEntity player, ItemStack stack, int cost) {
        return player.isCreative() || allowSearchWithoutXp || available(player, stack) >= cost;
    }

    public void pay(ServerPlayerEntity player, ItemStack stack, int cost) {
        if (player.isCreative() || cost == 0) return;
        int remaining = Math.max(0, available(player, stack) - cost);
        switch (type) {
            case ITEMS -> setEnergy(stack, remaining);
            case XP_LEVELS -> player.addExperienceLevels(-cost);
            case XP -> {
                int level = levelAtPoints(remaining);
                player.setExperienceLevel(level);
                player.setExperiencePoints((int) (remaining - pointsAtLevel(level)));
                player.totalExperience = remaining;
            }
        }
        if (type != Type.ITEMS) {
            player.networkHandler.sendPacket(new ExperienceBarUpdateS2CPacket(
                    player.experienceProgress, player.totalExperience, player.experienceLevel));
        }
    }

    static long pointsAtLevel(int level) {
        long l = level;
        if (level > 31) return (9*l*l - 325*l + 4440) / 2;
        if (level > 16) return (5*l*l - 81*l + 720) / 2;
        return l*l + 6*l;
    }

    private static int levelAtPoints(int points) {
        int low = 0, high = 32768;
        while (low + 1 < high) {
            int middle = (low + high) / 2;
            if (pointsAtLevel(middle) <= points) low = middle;
            else high = middle;
        }
        return low;
    }

    public static String equivalentLevels(int points) {
        int level = levelAtPoints(points);
        long start = pointsAtLevel(level);
        long step = pointsAtLevel(level + 1) - start;
        long progressTenths = (points - start) * 10;
        long tenths = level * 10L + (progressTenths + step - 1) / step;
        String comparison = progressTenths % step == 0 ? " = " : " ≈ ";
        return comparison + (tenths / 10) + (tenths % 10 == 0 ? "" : "." + (tenths % 10));
    }
}
