package io.github.enriquemetner.explorerscompass.unlocking;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StartupRulesTest {
    private static final Identifier CITY = new Identifier("minecraft:ancient_city");
    private static final Identifier FORTRESS = new Identifier("minecraft:fortress");
    private static final Identifier VILLAGE = new Identifier("minecraft:village_plains");
    private static final Identifier ALT = new Identifier("minecraft:stronghold");
    private static final Identifier EXTRA = new Identifier("minecraft:end_city");
    private static final Identifier TEMPLE = new Identifier("minecraft:desert_pyramid");
    private static final Identifier SET = new Identifier("minecraft:ruins");
    private static final Set<Identifier> STRUCTURES = Set.of(CITY, FORTRESS, VILLAGE, ALT, EXTRA, TEMPLE);
    private final UUID player = UUID.randomUUID();

    @Test void targetEditsOnlyRecheckAddedAndRemovedIdsAndRespectFtbPrecedence() {
        var oldRules = rules("""
            {"default":"lockedUntilVisited","requirements":{"example":{
              "unlockedStructures":["ancient_city","fortress"],"alwaysUnlocked":true
            }}}
            """);
        var newRules = rules("""
            {"default":"lockedUntilVisited","requirements":{"example":{
              "unlockedStructures":["fortress","village_plains"],"alwaysUnlocked":true
            }}}
            """);
        var rewards = List.of(reward(1, "unchanged quest", CITY));
        assertChanges(oldRules, newRules, rewards, rewards, VILLAGE);
        assertChanges(oldRules, newRules, List.of(), List.of(), CITY, VILLAGE);
        assertChanges(oldRules, oldRules, rewards, List.of(reward(1, "unchanged quest", FORTRESS)),
                CITY, FORTRESS);
        assertChanges(oldRules, oldRules, rewards, List.of(), CITY);
    }

    @Test void comparesWinningRequirementsAndNormalizesVisitOrder() {
        var oldRules = rules("""
            {"requirements":{
              "early":{"unlockedStructures":["ancient_city","fortress"],"requiredVisits":["stronghold"]},
              "later":{"unlockedStructures":["ancient_city"],"requiredVisits":["village_plains","end_city"]}
            }}
            """);
        var edited = rules("""
            {"requirements":{
              "early":{"unlockedStructures":["ancient_city","fortress"],"requiredAdvancements":["minecraft:story/root"]},
              "renamed":{"unlockedStructures":["ancient_city"],"requiredVisits":["end_city","village_plains"]}
            }}
            """);
        var reordered = rules("""
            {"requirements":{
              "renamed":{"unlockedStructures":["ancient_city"],"requiredVisits":["village_plains","end_city"]},
              "early":{"unlockedStructures":["ancient_city","fortress"],"requiredAdvancements":["minecraft:story/root"]}
            }}
            """);
        assertChanges(oldRules, edited, List.of(), List.of(), FORTRESS);
        assertChanges(edited, reordered, List.of(), List.of(), CITY);
    }

    @Test void addingEditingAndDeletingAWinningRuleRechecksSavedUnlocks() {
        var state = new UnlockedStructures();
        var progress = new UnlockingRules.Progress() {
            public boolean visited(Identifier id) { return id.equals(VILLAGE); }
            public boolean completed(Identifier id) { return false; }
        };
        String earlier = """
            "earlier":{"unlockedStructures":["ancient_city"],"requiredVisits":["stronghold"]}
            """;
        String later = """
            "later":{"unlockedStructures":["ancient_city"],"requiredVisits":["%s"]}
            """;
        var defaults = rules("{}");
        var first = rules("{\"requirements\":{" + earlier + "}}");
        var withOverride = rules("{\"requirements\":{" + earlier + "," + later.formatted("village_plains") + "}}");
        var editedOverride = rules("{\"requirements\":{" + earlier + "," + later.formatted("desert_pyramid") + "}}");

        state.add(player, STRUCTURES);
        reopen(state, defaults, progress);
        reopen(state, first, progress);
        assertFalse(state.contains(player, CITY));
        assertTrue(state.contains(player, FORTRESS));

        reopen(state, withOverride, progress);
        assertTrue(state.contains(player, CITY));
        reopen(state, editedOverride, progress);
        assertFalse(state.contains(player, CITY));

        reopen(state, withOverride, progress);
        assertTrue(state.contains(player, CITY));
        reopen(state, first, progress);
        assertFalse(state.contains(player, CITY));
        assertTrue(state.contains(player, FORTRESS));
    }

    private void reopen(UnlockedStructures state, UnlockingRules rules, UnlockingRules.Progress progress) {
        UnlockReloads.prepareStartup(state, rules.definitions(List.of()), true, rules.prerequisiteGates());
        UnlockReloads.reconcileUnlocks(state, player, true,
                dependents -> state.add(player, rules.earnedOnOpen(STRUCTURES, progress)));
    }

    @Test void taskOrQuestEditsOnlyRecheckCoveredTargetsAndClaimIdentityMatters() {
        var rules = rules("{}");
        var otherReward = reward(2, "independent task", VILLAGE);
        var before = List.of(reward(1, "original quest graph", CITY, FORTRESS), otherReward);
        assertChanges(rules, rules, before,
                List.of(otherReward, reward(1, "changed dependency task", CITY, FORTRESS)), CITY, FORTRESS);
        assertChanges(rules, rules, before, List.of(otherReward), CITY, FORTRESS);
        assertChanges(rules, rules, before,
                List.of(reward(9, "original quest graph", CITY, FORTRESS), otherReward), CITY, FORTRESS);
    }

    @Test void resolvedSetMembershipAndDefaultChangesOnlyAffectRelevantStructures() {
        String defaults = """
            {"default":"lockedUntilVisitedSet","requirements":{"always":{
              "unlockedStructures":["village_plains"],"alwaysUnlocked":true
            }}}
            """;
        var before = rules(defaults, Set.of(CITY, FORTRESS, VILLAGE));
        var after = rules(defaults, Set.of(CITY, VILLAGE, ALT));
        assertChanges(before, after, List.of(), List.of(), CITY, FORTRESS, ALT);
        assertChanges(after, rules(defaults.replace("lockedUntilVisitedSet", "lockedUntilVisited")),
                List.of(), List.of(), CITY, ALT);

        String wildcard = """
            {"requirements":{"cities":{"unlockedStructures":["ruins"],"requiredVisits":["stronghold"]}}}
            """;
        assertChanges(rules(wildcard, Set.of(CITY, FORTRESS)), rules(wildcard, Set.of(CITY, ALT)),
                List.of(), List.of(), FORTRESS, ALT);
    }

    @Test void prerequisiteEditAppliesEvenToFtbAndCascadesOnlyWhereGatesFail() {
        String source = """
            {"default":"lockedUntilVisited","prerequisites":{
              "city":{"appliesTo":["ancient_city"],"requiresUnlocked":["%s"]},
              "fortress":{"appliesTo":["fortress"],"requiresUnlocked":["ancient_city","stronghold"],"match":"all"},
              "village":{"appliesTo":["village_plains"],"requiresUnlocked":["ancient_city","stronghold"]},
              "extra":{"appliesTo":["end_city"],"requiresUnlocked":["fortress"]}
            }}
            """;
        var rewards = List.of(reward(1, "same claim", CITY));
        var oldRules = rules(source.formatted("stronghold"));
        var newRules = rules(source.formatted("desert_pyramid"));
        assertChanges(oldRules, newRules, rewards, rewards, CITY);

        var state = new UnlockedStructures();
        state.add(player, STRUCTURES);
        UnlockReloads.prepareStartup(state, oldRules.definitions(rewards), true, oldRules.prerequisiteGates());
        state = restart(state);
        UnlockReloads.prepareStartup(state, newRules.definitions(rewards), true, newRules.prerequisiteGates());
        state = restart(state);
        UnlockReloads.prepareStartup(state, newRules.definitions(rewards), true, newRules.prerequisiteGates());
        assertEquals(Set.of(VILLAGE, ALT, TEMPLE), state.getStructures(player));
        assertEquals(0, state.add(player, Set.of(CITY, FORTRESS)));

        var pending = restart(state).takePendingRelock(player);
        assertEquals(Set.of(CITY), pending.direct());
        assertEquals(Set.of(FORTRESS, EXTRA), pending.dependent());
        assertTrue(newRules.applyPrerequisites(pending.dependent(), state.getStructures(player)).isEmpty());
        var rejoining = state;
        assertEquals(0, UnlockReloads.reconcileUnlocks(rejoining, player, false, ignored -> fail("Invalid rules")));
        assertTrue(rejoining.hasPendingRelock(player));
        assertEquals(3, UnlockReloads.reconcileUnlocks(rejoining, player, true, dependent -> {
            Set<Identifier> withClaim = new java.util.HashSet<>(dependent);
            withClaim.add(CITY);
            rejoining.add(player, newRules.applyPrerequisites(withClaim, rejoining.getStructures(player)));
        }));
        assertFalse(rejoining.hasPendingRelock(player));
        assertEquals(STRUCTURES, rejoining.getStructures(player));
        assertEquals(STRUCTURES, rejoining.latestUnlocked(player));
        assertEquals(0, UnlockReloads.reconcileUnlocks(rejoining, player, true,
                ignored -> rejoining.add(player, STRUCTURES)));
    }

    @Test void pendingDependentBecomesDirectIfItsOwnRuleChangesBeforeThePlayerJoins() {
        var gates = rules("""
            {"prerequisites":{"fortress":{"appliesTo":["fortress"],"requiresUnlocked":["ancient_city"]}}}
            """).prerequisiteGates();
        var state = new UnlockedStructures();
        state.add(player, Set.of(CITY, FORTRESS));
        state.relock(Set.of(CITY), gates);
        state = restart(state);
        state.relock(Set.of(FORTRESS), gates);
        var pending = restart(state).takePendingRelock(player);
        assertEquals(Set.of(CITY, FORTRESS), pending.direct());
        assertTrue(pending.dependent().isEmpty());
    }

    @Test void legacyUpgradePreservesSavedUnlocksAndBadLoadsKeepTheBaseline() {
        var state = new UnlockedStructures();
        state.add(player, Set.of(CITY));
        NbtCompound legacy = state.writeNbt(new NbtCompound());
        legacy.putString("DefinitionHistory", "old global hash");
        ((NbtList) legacy.get("Players")).getCompound(0).putBoolean("PendingRelock", true);
        state = UnlockedStructures.fromNbt(legacy);
        var oldRules = rules("{}");
        var newRules = rules("{\"default\":\"lockedUntilVisited\"}");
        UnlockReloads.prepareStartup(state, oldRules.definitions(List.of()), true, Map.of());
        assertFalse(state.hasPendingRelock(player));
        assertEquals(Set.of(CITY), state.getStructures(player));

        var badRules = rules("{\"prerequisites\":{\"bad\":{\"appliesTo\":[\"ancient_city\"],\"requiresUnlocked\":[\"ancient_city\"]}}}");
        assertNull(badRules.definitions(List.of()));
        UnlockReloads.prepareStartup(state, badRules.definitions(List.of()), true, Map.of());
        assertEquals(oldRules.definitions(List.of()), restart(state).definitions());
        assertEquals(Set.of(CITY), state.getStructures(player));

        UnlockReloads.prepareStartup(state, newRules.definitions(List.of()), false, Map.of());
        state = restart(state);
        UnlockReloads.prepareStartup(state, newRules.definitions(List.of()), true, Map.of());
        assertEquals(Set.of(CITY), state.getStructures(player));
    }

    private static UnlockedStructures restart(UnlockedStructures state) {
        return UnlockedStructures.fromNbt(state.writeNbt(new NbtCompound()));
    }

    private static void assertChanges(UnlockingRules before, UnlockingRules after,
                                      List<FtbStructureRewards.RewardDefinition> oldRewards,
                                      List<FtbStructureRewards.RewardDefinition> newRewards, Identifier... expected) {
        var previous = UnlockDefinitions.deserialize(before.definitions(oldRewards).serialize());
        assertEquals(Set.of(expected), after.definitions(newRewards).changedStructures(previous, UnlockDefinitions.ids(STRUCTURES)));
    }

    private static FtbStructureRewards.RewardDefinition reward(long id, String requirement, Identifier... targets) {
        return new FtbStructureRewards.RewardDefinition(id, Set.of(targets), requirement);
    }

    private static UnlockingRules rules(String source) {
        return rules(source, Set.of());
    }

    private static UnlockingRules rules(String source, Set<Identifier> members) {
        return UnlockingRules.parse(source, new UnlockingRules.Catalog() {
            public Set<Identifier> structures() { return STRUCTURES; }
            public Set<Identifier> structureSets() { return members.isEmpty() ? Set.of() : Set.of(SET); }
            public Set<Identifier> structureSet(Identifier id) { return id.equals(SET) ? members : Set.of(); }
            public boolean advancementExists(Identifier id) { return true; }
            public Text advancementTitle(Identifier id) { return Text.empty(); }
            public Text advancementDescription(Identifier id) { return Text.empty(); }
            public Formatting advancementColor(Identifier id) { return Formatting.WHITE; }
        });
    }
}
