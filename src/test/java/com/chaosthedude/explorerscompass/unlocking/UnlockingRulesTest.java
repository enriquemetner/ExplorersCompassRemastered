package com.chaosthedude.explorerscompass.unlocking;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.util.VisitedStructures;
import com.google.gson.JsonParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;
import net.minecraft.network.PacketByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class UnlockingRulesTest {
    private static final Identifier A = new Identifier("minecraft:ancient_city");
    private static final Identifier B = new Identifier("minecraft:fortress");
    private static final Identifier C = new Identifier("minecraft:stronghold");
    private static final Identifier ADVANCEMENT = new Identifier("minecraft:story/mine_stone");
    private static final Identifier DRAGON_EGG = new Identifier("minecraft:end/dragon_egg");
    private static final Identifier SET = new Identifier("minecraft:test_set");

    @Test void newlyUnlockedFilterDependsOnConfiguredRequirementsRatherThanCurrentLocks() {
        assertFalse(rules("{}").hasUnlockingRequirements(FtbStructureRewards.Snapshot.EMPTY));
        assertTrue(rules("{\"default\":\"lockedUntilVisited\"}")
                .hasUnlockingRequirements(FtbStructureRewards.Snapshot.EMPTY));
        assertTrue(condition("{\"requiredVisits\":[\"minecraft:ancient_city\"]}")
                .hasUnlockingRequirements(FtbStructureRewards.Snapshot.EMPTY));
        assertTrue(rules("{}").hasUnlockingRequirements(new FtbStructureRewards.Snapshot(
                java.util.Map.of(), java.util.Map.of(1L, Set.of()), Set.of())));
    }

    @Test void updateNoticesCollapsePersistAndIgnoreStaleAcknowledgments() {
        UUID player = UUID.randomUUID();
        UnlockedStructures state = new UnlockedStructures();
        state.queueReload(Set.of(player), true, true);
        long oldRevision = state.noticeRevision(player);
        state.takePendingRelock(player);
        state.queueReload(Set.of(player), false, true);
        long newest = state.noticeRevision(player);
        assertTrue(newest > oldRevision);
        state = UnlockedStructures.fromNbt(state.writeNbt(new NbtCompound()));
        assertTrue(state.noticeRelocked(player));
        state.acknowledgeNotice(player, oldRevision);
        assertEquals(newest, state.noticeRevision(player));
        state.acknowledgeNotice(player, newest);
        assertEquals(0, state.noticeRevision(player));
        assertFalse(state.noticeRelocked(player));
    }

    @Test void reloadHistoryAndOfflineWorkSurviveRestart() {
        UUID player = UUID.randomUUID();
        UnlockedStructures saved = new UnlockedStructures();
        saved.add(player, Set.of(A));
        saved.enableNotifications(player);
        assertFalse(saved.updateDefinitionHistory("first"));
        assertFalse(saved.updateDefinitionHistory("first"));
        assertTrue(saved.updateDefinitionHistory("second"));
        saved.queueReload(saved.knownPlayers(), true, true);
        saved = UnlockedStructures.fromNbt(saved.writeNbt(new NbtCompound()));
        assertEquals("second", saved.definitionHistory());
        assertTrue(saved.takePendingRelock(player));
        assertFalse(saved.takePendingRelock(player));
        assertTrue(saved.takePendingReloadNotice(player));
        assertFalse(saved.takePendingReloadNotice(player));
        saved.clearStructures(player);
        assertTrue(saved.getStructures(player).isEmpty());
        assertTrue(saved.notificationsEnabled(player));
    }
    private static final Set<Identifier> STRUCTURES = Set.of(A, B, C);
    private final UnlockingRules.Catalog catalog = catalog(STRUCTURES);
    private static UnlockingRules.Catalog catalog(Set<Identifier> structures) {
        return new UnlockingRules.Catalog() {
            public Set<Identifier> structures() { return structures; }
            public Set<Identifier> structureSets() { return Set.of(SET, new Identifier("minecraft:other_set")); }
            public Set<Identifier> structureSet(Identifier id) { return id.equals(SET) ? Set.of(A, B) : id.toString().equals("minecraft:other_set") ? Set.of(A, C) : Set.of(); }
            public boolean advancementExists(Identifier id) { return id.equals(ADVANCEMENT) || id.equals(DRAGON_EGG); }
            public Text advancementTitle(Identifier id) { return Text.literal(id.equals(DRAGON_EGG) ? "The Next Generation" : "Stone Age"); }
            public Text advancementDescription(Identifier id) { return Text.literal(id.equals(DRAGON_EGG) ? "Hold the Dragon Egg" : "Mine stone"); }
            public Formatting advancementColor(Identifier id) { return id.equals(DRAGON_EGG) ? Formatting.DARK_PURPLE : Formatting.GREEN; }
        };
    }
    private static class Progress implements UnlockingRules.Progress {
        final Set<Identifier> visits = new HashSet<>(), advancements = new HashSet<>();
        public boolean visited(Identifier id) { return visits.contains(id); }
        public boolean completed(Identifier id) { return advancements.contains(id); }
    }
    private UnlockingRules rules(String json) { return UnlockingRules.parse(json, catalog); }
    private String rule(String condition) {
        return "{\"unlockedStructures\":[\"minecraft:fortress\"]," + condition.strip().substring(1);
    }
    private UnlockingRules condition(String condition) {
        return rules("{\"default\":\"lockedUntilVisited\",\"requirements\":{\"test\":" + rule(condition) + "}}");
    }

    @Test void alwaysUnlockedOverridesTheDefaultAndStillYieldsToFtb() {
        UnlockingRules parsed = rules("""
                {"default":"lockedUntilVisited","requirements":{
                    "free":{"unlockedStructures":["fortress"],"alwaysUnlocked":true},
                    "off":{"unlockedStructures":["stronghold"],"alwaysUnlocked":false},
                    "string":{"unlockedStructures":["stronghold"],"alwaysUnlocked":"true"},
                    "match":{"unlockedStructures":["stronghold"],"alwaysUnlocked":true,"match":"any"},
                    "both":{"unlockedStructures":["stronghold"],"requiredVisits":["fortress"],
                            "requiredAdvancements":["minecraft:story/mine_stone"]}
                }}
                """);
        assertEquals(Set.of("free"), parsed.requirementNames());
        assertEquals(Set.of(B), parsed.earnedOnOpen(STRUCTURES, new Progress()));
        assertTrue(parsed.earnedOnOpen(STRUCTURES, new Progress(), Set.of(B)).isEmpty());
        assertEquals(4, parsed.diagnostics(UnlockingRules.Severity.ERROR).size());
    }

    @Test void defaultsAndExplicitRules() {
        Progress p = new Progress();
        assertEquals(STRUCTURES, rules("{}").earnedOnOpen(STRUCTURES, p));
        assertTrue(rules("{\"default\":\"lockedUntilVisited\"}").earnedOnOpen(STRUCTURES, p).isEmpty());
        p.visits.add(A);
        assertEquals(Set.of(A), rules("{\"default\":\"lockedUntilVisited\"}").earnedOnOpen(STRUCTURES, p));
        UnlockingRules rules = rules("{\"default\":\"unlocked\",\"requirements\":{\"test\":" + rule("{\"requiredAdvancements\":[\"minecraft:story/mine_stone\"]}") + "}}");
        assertEquals(Set.of(A, C), rules.earnedOnOpen(STRUCTURES, p));
    }

    @Test void visitedSetDefaultUsesDirectMembershipAndFallsBackToSelf() {
        Identifier ungrouped = new Identifier("minecraft:test_structure");
        Set<Identifier> structures = Set.of(A, B, C, ungrouped);
        var rules = UnlockingRules.parse("{\"default\":\"lockedUntilVisitedSet\"}", catalog(structures));
        Progress progress = new Progress();
        assertTrue(rules.hasUnlockingRequirements(FtbStructureRewards.Snapshot.EMPTY));
        assertTrue(rules.earnedOnOpen(structures, progress).isEmpty());
        assertEquals(3, rules.requirements(A, progress).children().size());
        assertEquals("Visit any Structure:", rules.requirements(B, progress).label().getString());
        assertEquals("Visit Structure:", rules.requirements(ungrouped, progress).label().getString());

        progress.visits.add(B);
        assertEquals(Set.of(A, B), rules.earnedOnProgress(progress, B, null));
        assertEquals(Set.of(A, B), rules.earnedOnOpen(structures, progress));
        assertTrue(rules.requirements(A, progress).checked());
        assertFalse(rules.requirements(C, progress).checked());

        progress.visits.add(A);
        assertEquals(STRUCTURES, rules.earnedOnProgress(progress, A, null));
        progress.visits.add(ungrouped);
        assertEquals(Set.of(ungrouped), rules.earnedOnProgress(progress, ungrouped, null));
        assertEquals(structures, rules.earnedOnOpen(structures, progress));
    }

    @Test void visitedSetDefaultRespectsExplicitRulesAndFtbRewards() {
        var rules = rules("{\"default\":\"lockedUntilVisitedSet\",\"requirements\":{\"test\":"
                + rule("{\"requiredAdvancements\":[\"minecraft:story/mine_stone\"]}") + "}}");
        Progress progress = new Progress();
        progress.visits.add(A);
        assertEquals(Set.of(A), rules.earnedOnProgress(progress, A, null, Set.of(C)));
        assertEquals(Set.of(A), rules.earnedOnOpen(STRUCTURES, progress, Set.of(C)));
        assertEquals("Complete Advancement:", rules.requirements(B, progress).label().getString());

        progress.visits.clear();
        progress.visits.add(B);
        assertEquals(Set.of(A), rules.earnedOnProgress(progress, B, null));
        assertEquals(Set.of(A), rules.earnedOnOpen(STRUCTURES, progress));
        progress.advancements.add(ADVANCEMENT);
        assertEquals(Set.of(B), rules.earnedOnProgress(progress, null, ADVANCEMENT));
    }

    @Test void ftbRewardsTakePrecedenceWithoutRelockingSavedStructures() {
        UnlockingRules rules = rules("{\"default\":\"unlocked\",\"requirements\":{\"visit\":"
                + rule("{\"requiredVisits\":[\"minecraft:ancient_city\"]}") + "}}");
        Progress progress = new Progress();
        progress.visits.add(A);
        assertFalse(rules.earnedOnOpen(STRUCTURES, progress, Set.of(B, C)).contains(B));
        assertFalse(rules.earnedOnOpen(STRUCTURES, progress, Set.of(B, C)).contains(C));
        assertFalse(rules.earnedOnProgress(progress, A, null, Set.of(B)).contains(B));
        // Removing the last reward restores the JSON rule or default on the next open.
        assertTrue(rules.earnedOnOpen(STRUCTURES, progress, Set.of()).contains(B));
        assertTrue(rules.earnedOnOpen(STRUCTURES, progress, Set.of()).contains(C));
        UnlockingRules visitedDefault = rules("{\"default\":\"lockedUntilVisited\"}");
        assertFalse(visitedDefault.earnedOnOpen(STRUCTURES, progress, Set.of()).contains(C));
    }

    @Test void ftbRewardSelectorsExpandSetsAndWildcards() {
        assertEquals(STRUCTURES, UnlockingRules.resolveRewardTargets(catalog,
                List.of("minecraft:strong*"), List.of("minecraft:test_set")));
        assertEquals(Set.of(B), UnlockingRules.resolveRewardTargets(catalog, List.of("fortress"), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> UnlockingRules.resolveRewardTargets(catalog, List.of("minecraft:fortres?"), List.of()));
    }

    @Test void ftbFieldsOnlyResolveTheirOwnRegistryAndDeduplicateWithoutReorderingPreview() {
        assertTrue(UnlockingRules.resolveRewardTargets(catalog, List.of("test_set"), List.of()).isEmpty());
        assertTrue(UnlockingRules.resolveRewardTargets(catalog, List.of(), List.of("fortress")).isEmpty());
        assertEquals(STRUCTURES, UnlockingRules.resolveRewardTargets(catalog, List.of(), List.of("*_set")));
        var setsOnly = UnlockingRules.resolveRewardTargets(catalog, List.of(), List.of("test_set", "other_set"));
        var overlapping = UnlockingRules.resolveRewardTargets(catalog, List.of("ancient_city", "fortress"), List.of("test_set", "other_set", "test_set"));
        assertEquals(3, overlapping.size());
        assertEquals(com.chaosthedude.explorerscompass.compat.ftb.FtbRewardPreview.of(setsOnly),
                com.chaosthedude.explorerscompass.compat.ftb.FtbRewardPreview.of(overlapping));
        UnlockingRules.Catalog collision = new UnlockingRules.Catalog() {
            public Set<Identifier> structures() { return STRUCTURES; }
            public Set<Identifier> structureSets() { return Set.of(A); }
            public Set<Identifier> structureSet(Identifier id) { return Set.of(B); }
            public boolean advancementExists(Identifier id) { return false; }
            public Text advancementTitle(Identifier id) { return Text.empty(); }
            public Text advancementDescription(Identifier id) { return Text.empty(); }
            public Formatting advancementColor(Identifier id) { return Formatting.WHITE; }
        };
        assertEquals(Set.of(A), UnlockingRules.resolveRewardTargets(collision, List.of(A.toString()), List.of()));
        assertEquals(Set.of(B), UnlockingRules.resolveRewardTargets(collision, List.of(), List.of(A.toString())));
        assertEquals(Set.of(A, B), UnlockingRules.resolveRewardTargets(collision, List.of(A.toString()), List.of(A.toString())));
    }

    @Test void ftbSelectorsExcludeCommonBlacklistedStructures() {
        List<String> previous = ExplorersCompassConfig.structureBlacklist;
        try {
            ExplorersCompassConfig.structureBlacklist = List.of("minecraft:ancient_*", "minecraft:fortress");
            assertEquals(Set.of(C), UnlockingRules.resolveRewardTargets(catalog,
                    List.of("minecraft:strong*", "minecraft:ancient_city"), List.of("minecraft:test_set")));
            assertTrue(UnlockingRules.resolveRewardTargets(catalog, List.of("minecraft:fortress"), List.of()).isEmpty());
        } finally {
            ExplorersCompassConfig.structureBlacklist = previous;
        }
    }

    @Test void anyClaimedFtbRewardUnlocksCurrentTargetsAfterEditsOrRelocking() {
        UnlockingRules rules = rules("{\"default\":\"lockedUntilVisited\"}");
        FtbStructureRewards.Quest first = new FtbStructureRewards.Quest(1L, Text.literal("First"));
        FtbStructureRewards.Quest second = new FtbStructureRewards.Quest(2L, Text.literal("Second"));
        FtbStructureRewards.Snapshot before = new FtbStructureRewards.Snapshot(
                java.util.Map.of(B, List.of(first, second)), java.util.Map.of(10L, Set.of(B), 20L, Set.of(B)), Set.of());
        assertFalse(rules.earnedOnOpen(STRUCTURES, new Progress(), before).contains(B));
        FtbStructureRewards.Snapshot edited = new FtbStructureRewards.Snapshot(
                java.util.Map.of(B, List.of(first, second), C, List.of(first)),
                java.util.Map.of(10L, Set.of(B, C), 20L, Set.of(B)), Set.of(10L));
        assertEquals(Set.of(B, C), rules.earnedOnOpen(STRUCTURES, new Progress(), edited));
        // Reapplying a claimed reward after a command lock restores the same target.
        UnlockedStructures saved = new UnlockedStructures();
        UUID player = UUID.randomUUID();
        saved.add(player, Set.of(B));
        saved.remove(player, Set.of(B));
        saved.add(player, rules.earnedOnOpen(STRUCTURES, new Progress(), edited));
        assertTrue(saved.contains(player, B));

        // FTB resetting the reward clears its claim. A later command lock must now stick.
        FtbStructureRewards.Snapshot reset = new FtbStructureRewards.Snapshot(
                edited.quests(), edited.rewardTargets(), Set.of());
        saved.remove(player, Set.of(B, C));
        saved.add(player, rules.earnedOnOpen(STRUCTURES, new Progress(), reset));
        assertFalse(saved.contains(player, B));
        assertFalse(saved.contains(player, C));
        // A fresh claim of either reward restores only that reward's current targets.
        FtbStructureRewards.Snapshot reclaimed = new FtbStructureRewards.Snapshot(
                edited.quests(), edited.rewardTargets(), Set.of(20L));
        assertEquals(Set.of(B), rules.earnedOnOpen(STRUCTURES, new Progress(), reclaimed));
    }

    @Test void unlockedStructuresPersistAndQuestRequirementsUseDistinctTitles() {
        UUID player = UUID.randomUUID();
        UnlockedStructures saved = new UnlockedStructures();
        saved.add(player, Set.of(A));
        saved = UnlockedStructures.fromNbt(saved.writeNbt(new NbtCompound()));
        assertTrue(saved.contains(player, A));
        saved.remove(player, Set.of(A));
        assertFalse(saved.contains(player, A));
        RequirementNode single = UnlockingRules.questNode(List.of(new FtbStructureRewards.Quest(1L, Text.literal("First"))));
        assertEquals("Complete Quest:", single.label().getString());
        assertEquals("First", single.children().get(0).label().getString());
        assertEquals(1L, single.children().get(0).questId());
        RequirementNode multiple = UnlockingRules.questNode(List.of(
                new FtbStructureRewards.Quest(1L, Text.literal("First")),
                new FtbStructureRewards.Quest(1L, Text.literal("First")),
                new FtbStructureRewards.Quest(2L, Text.literal("Second"))));
        assertEquals("Complete any Quest:", multiple.label().getString());
        assertEquals(2, multiple.children().size());
        PacketByteBuf questPacket = new PacketByteBuf(Unpooled.buffer());
        multiple.write(questPacket);
        RequirementNode restored = RequirementNode.read(questPacket);
        assertEquals(1L, restored.children().get(0).questId());
        assertEquals(2L, restored.children().get(1).questId());
    }

    @Test void conditionGroupsAreRejectedAndVisitProgressUnlocksImmediately() {
        assertTrue(condition("{\"all\":[{\"requiredVisits\":[\"minecraft:ancient_city\"]}]}"
                ).requirementNames().isEmpty());
        assertTrue(condition("{\"any\":[{\"requiredVisits\":[\"minecraft:ancient_city\"]}]}"
                ).requirementNames().isEmpty());
        UnlockingRules rules = condition("{\"requiredVisits\":[\"minecraft:ancient_city\"]}");
        Progress p = new Progress();
        p.visits.add(A);
        assertTrue(rules.earnedOnProgress(p, A, null).contains(B));
        assertTrue(rules.earnedOnProgress(p, null, new Identifier("test:unrelated")).isEmpty());
    }

    @Test void visitListsAndSetsRespectAnyAll() {
        Progress p = new Progress(); p.visits.add(A);
        UnlockingRules any = condition("{\"requiredVisits\":[\"minecraft:ancient_city\",\"minecraft:stronghold\"]}");
        assertTrue(any.earnedOnOpen(STRUCTURES, p).contains(B));
        assertEquals("Visit any Structure:", any.requirements(B, p).label().getString());
        assertFalse(condition("{\"requiredVisits\":[\"minecraft:ancient_city\",\"minecraft:stronghold\"],\"match\":\"all\"}").earnedOnOpen(STRUCTURES, p).contains(B));
        assertTrue(condition("{\"requiredVisits\":[\"minecraft:test_set\"],\"match\":\"any\"}").earnedOnOpen(STRUCTURES, p).contains(B));
        UnlockingRules all = condition("{\"requiredVisits\":[\"minecraft:test_set\"],\"match\":\"all\"}");
        assertFalse(all.earnedOnOpen(STRUCTURES, p).contains(B));
        p.visits.add(B);
        assertTrue(all.earnedOnOpen(STRUCTURES, p).contains(B));
    }

    @Test void advancementListsRespectAnyAllAndKeepIndividualChecks() {
        String ids = "[\"minecraft:story/mine_stone\",\"minecraft:end/dragon_egg\"]";
        UnlockingRules any = condition("{\"requiredAdvancements\":" + ids + ",\"match\":\"any\"}");
        UnlockingRules all = condition("{\"requiredAdvancements\":" + ids + ",\"match\":\"all\"}");
        Progress progress = new Progress();
        assertFalse(any.earnedOnOpen(STRUCTURES, progress).contains(B));
        progress.advancements.add(ADVANCEMENT);
        assertTrue(any.earnedOnProgress(progress, null, ADVANCEMENT).contains(B));
        assertFalse(all.earnedOnProgress(progress, null, ADVANCEMENT).contains(B));
        RequirementNode tree = all.requirements(B, progress);
        assertEquals("Complete all Advancements:", tree.label().getString());
        assertFalse(tree.checked());
        assertTrue(tree.children().get(0).checked());
        assertFalse(tree.children().get(1).checked());
        assertEquals(Formatting.DARK_PURPLE.getColorValue(), tree.children().get(1).label().getStyle().getColor().getRgb());
        assertEquals("Hold the Dragon Egg", tree.children().get(1).tooltipDescription().getString());
        progress.advancements.add(DRAGON_EGG);
        assertTrue(all.earnedOnProgress(progress, null, DRAGON_EGG).contains(B));
        assertTrue(all.requirements(B, progress).checked());
        assertEquals("Complete any Advancements:", any.requirements(B, progress).label().getString());
        assertTrue(condition("{\"requiredAdvancements\":[\"minecraft:story/mine_stone\"]}")
                .earnedOnOpen(STRUCTURES, progress).contains(B));
    }

    @Test void advancementConditionRequiresArrayAndValidMatch() {
        assertTrue(condition("{\"id\":\"minecraft:story/mine_stone\"}").requirementNames().isEmpty());
        assertTrue(condition("{\"requiredAdvancements\":[]}").requirementNames().isEmpty());
        assertTrue(condition("{\"requiredAdvancements\":[\"minecraft:story/mine_stone\"],\"match\":\"neither\"}").requirementNames().isEmpty());
    }

    @Test void targetsSupportStructureSets() {
        UnlockingRules rules = rules("""
            {"requirements":{
              "set":{"unlockedStructures":["minecraft:test_set"],"requiredVisits":["minecraft:stronghold"]}
            }}
            """);
        assertEquals(Set.of(A, B), rules.requirementTargets("set"));
    }

    @Test void selectorsCombineDirectStructuresAndMultipleSetsWithoutDuplicates() {
        UnlockingRules rules = rules("""
            {"requirements":{"combined":{
              "unlockedStructures":["minecraft:ancient_city","minecraft:*_set"],
              "requiredVisits":["minecraft:ancient_city","minecraft:*_set"],"match":"all"
            }}}
            """);
        assertEquals(STRUCTURES, rules.requirementTargets("combined"));
        Progress progress = new Progress();
        progress.visits.add(A);
        RequirementNode requirement = rules.requirements(B, progress);
        assertEquals(3, requirement.children().size());
        assertFalse(requirement.checked());
        progress.visits.addAll(STRUCTURES);
        assertTrue(rules.earnedOnOpen(STRUCTURES, progress).contains(B));
    }

    @Test void wildcardStarMatchesZeroOrMoreAndQuestionMarkIsInvalid() {
        UnlockingRules rules = rules("""
            {"default":"lockedUntilVisited","requirements":{"patterns":{
              "unlockedStructures":["minecraft:fortress*","minecraft:test_*set"],
              "requiredVisits":["ancient_cit*"],"match":"any"
            }}}
            """);
        assertEquals(Set.of(A, B), rules.requirementTargets("patterns"));
        Progress progress = new Progress();
        progress.visits.add(A);
        assertEquals(Set.of(A, B), rules.earnedOnOpen(STRUCTURES, progress));
        assertTrue(condition("{\"requiredVisits\":[\"minecraft:ancient_cit?\"]}").requirementNames().isEmpty());
        assertTrue(rules("{\"requirements\":{\"invalid\":{\"unlockedStructures\":[\"minecraft:fortres?\"],"
                + "\"requiredVisits\":[\"minecraft:ancient_city\"]}}}").requirementNames().isEmpty());
    }

    @Test void structureWinsWhenItsIdAlsoNamesASet() {
        UnlockingRules.Catalog collision = new UnlockingRules.Catalog() {
            public Set<Identifier> structures() { return STRUCTURES; }
            public Set<Identifier> structureSets() { return Set.of(B); }
            public Set<Identifier> structureSet(Identifier id) { return Set.of(A); }
            public boolean advancementExists(Identifier id) { return false; }
            public Text advancementTitle(Identifier id) { return Text.empty(); }
            public Text advancementDescription(Identifier id) { return null; }
            public Formatting advancementColor(Identifier id) { return Formatting.WHITE; }
        };
        UnlockingRules rules = UnlockingRules.parse(JsonParser.parseString("""
            {"default":"lockedUntilVisited","requirements":{"collision":{
              "unlockedStructures":["minecraft:fortress"],
              "requiredVisits":["minecraft:fortress"]
            }}}
            """).getAsJsonObject(), collision);
        assertEquals(Set.of(B), rules.requirementTargets("collision"));
        Progress progress = new Progress();
        progress.visits.add(A);
        assertFalse(rules.earnedOnOpen(STRUCTURES, progress).contains(B));
        progress.visits.add(B);
        assertTrue(rules.earnedOnOpen(STRUCTURES, progress).contains(B));
    }

    @Test void selectorsRequireNonemptyArraysAndRejectOldFields() {
        for (String selector : new String[]{"{}", "[]", "null", "\"minecraft:fortress\"",
                "{\"requiredVisits\":[\"minecraft:fortress\"]}", "{\"structureSet\":\"minecraft:test_set\"}"}) {
            assertTrue(rules("{\"requirements\":{\"bad\":{\"unlockedStructures\":" + selector
                    + ",\"requiredVisits\":[\"minecraft:ancient_city\"]}}}").requirementNames().isEmpty());
        }
        assertTrue(condition("{\"requiredVisits\":[]}").requirementNames().isEmpty());
        assertTrue(condition("{\"structureSet\":\"minecraft:test_set\"}").requirementNames().isEmpty());
    }

    @Test void laterRuleReplacesEarlierConditionAndInvalidRuleDoesNotOverrideDefault() {
        UnlockingRules rules = rules("{\"requirements\":{\"a\":" + rule("{\"requiredVisits\":[\"minecraft:ancient_city\"]}")
                + ",\"b\":" + rule("{\"requiredAdvancements\":[\"minecraft:story/mine_stone\"]}")
                + ",\"invalid\":{\"unlockedStructures\":[\"minecraft:stronghold\"],\"all\":[]}}}");
        Progress p = new Progress();
        assertFalse(rules.earnedOnOpen(STRUCTURES, p).contains(B));
        assertTrue(rules.earnedOnOpen(STRUCTURES, p).contains(C));
        p.visits.add(A);
        assertFalse(rules.earnedOnOpen(STRUCTURES, p).contains(B));
        assertFalse(rules.earnedOnProgress(p, A, null).contains(B));
        p.advancements.add(ADVANCEMENT);
        assertTrue(rules.earnedOnOpen(STRUCTURES, p).contains(B));
        assertTrue(rules.earnedOnProgress(p, null, ADVANCEMENT).contains(B));
        assertEquals(Set.of("a", "b"), rules.requirementNames());
    }

    @Test void duplicateTargetLinesReferToSelectorsIncludingExpandedSets() {
        String source = "{\n" +
                "  \"requirements\": {\n" +
                "    \"first\": {\"unlockedStructures\": [\n" +
                "      \"minecraft:test_set\"\n" +
                "    ], \"requiredVisits\": [\"minecraft:ancient_city\"]},\n" +
                "    \"second\": {\"unlockedStructures\": [\n" +
                "      \"minecraft:fortress\"\n" +
                "    ], \"requiredAdvancements\": [\"minecraft:story/mine_stone\"]}\n" +
                "  }\n" +
                "}\n";
        UnlockingRules rules = rules(source);
        var warnings = rules.diagnostics(UnlockingRules.Severity.WARNING);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).message().contains("minecraft:fortress"));
        assertTrue(warnings.get(0).message().contains("lines 4, 7"));
        Progress progress = new Progress();
        progress.visits.add(A);
        assertTrue(rules.earnedOnOpen(STRUCTURES, progress).contains(A));
        assertFalse(rules.earnedOnOpen(STRUCTURES, progress).contains(B));
        progress.advancements.add(ADVANCEMENT);
        assertTrue(rules.earnedOnOpen(STRUCTURES, progress).contains(B));
        assertEquals("Complete Advancement:", rules.requirements(B, progress).label().getString());
    }

    @Test void threeDeclarationsUseOnlyTheLastCondition() {
        String source = """
            {
              "default": "lockedUntilVisited",
              "requirements": {
                "first": {"unlockedStructures": ["minecraft:fortress"], "requiredVisits": ["minecraft:ancient_city"]},
                "second": {"unlockedStructures": ["minecraft:fortress"], "requiredAdvancements": ["minecraft:story/mine_stone"]},
                "third": {"unlockedStructures": ["minecraft:fortress"], "requiredVisits": ["minecraft:stronghold"]}
              }
            }
            """;
        UnlockingRules rules = rules(source);
        Progress progress = new Progress();
        progress.visits.add(A);
        progress.advancements.add(ADVANCEMENT);
        assertFalse(rules.earnedOnOpen(STRUCTURES, progress).contains(B));
        progress.visits.add(C);
        assertTrue(rules.earnedOnOpen(STRUCTURES, progress).contains(B));
        assertEquals("Visit Structure:", rules.requirements(B, progress).label().getString());
    }

    @Test void unknownSelectorsRejectOnlyTheirOwnRuleAndReportTheSelectorLine() {
        UnlockingRules parsed = rules("""
                {
                  "default": "lockedUntilVisited",
                  "requirements": {
                    "valid": {"unlockedStructures": ["fortress"], "requiredVisits": ["ancient_city"]},
                    "typo": {"unlockedStructures": ["stronghold"], "requiredVisits": [
                      "ancient_citys"
                    ]},
                    "unmatched": {"unlockedStructures": ["fortress", "missing:*"], "requiredVisits": ["ancient_city"]},
                    "missingAdvancement": {"unlockedStructures": ["fortress"], "requiredAdvancements": ["missing:advancement"]},
                    "missingCondition":
                    {"unlockedStructures": ["fortress"]}
                  }
                }
                """);
        assertEquals(Set.of("valid"), parsed.requirementNames());
        var errors = parsed.diagnostics(UnlockingRules.Severity.ERROR);
        assertEquals(4, errors.size());
        assertTrue(errors.get(0).message().contains("line 6"));
        assertTrue(errors.get(0).message().contains("ancient_citys"));
        assertTrue(errors.get(1).message().contains("missing:*"));
        assertTrue(errors.get(2).message().contains("missing:advancement"));
        assertTrue(errors.get(3).message().contains("line 10"));
        assertEquals(errors, parsed.diagnostics(null));
        assertTrue(parsed.diagnostics(UnlockingRules.Severity.WARNING).isEmpty());
    }

    @Test void ruleDescriptionIsRejectedAndRequirementsAreGenerated() {
        UnlockingRules rules = rules("""
            {"requirements":{"hint":{"unlockedStructures":["minecraft:ancient_city"],
              "description":"Follow the lanterns.","requiredAdvancements":["minecraft:story/mine_stone"]}}}
            """);
        assertTrue(rules.requirementNames().isEmpty());
        RequirementNode advancement = condition("{\"requiredAdvancements\":[\"minecraft:story/mine_stone\"]}").requirements(B, new Progress());
        assertEquals("Complete Advancement:", advancement.label().getString());
        assertEquals("Stone Age", advancement.children().get(0).label().getString());
        assertEquals(Formatting.GREEN.getColorValue(), advancement.children().get(0).label().getStyle().getColor().getRgb());
        assertEquals("Mine stone", advancement.children().get(0).tooltipDescription().getString());
    }

    @Test void requirementTreeShowsEachSatisfiedVisitAndCombinedState() {
        UnlockingRules rules = condition("""
            {"requiredVisits":["minecraft:ancient_city","minecraft:stronghold"],"match":"all"}
            """);
        Progress progress = new Progress();
        progress.visits.add(A);
        RequirementNode root = rules.requirements(B, progress);
        assertEquals("Visit all Structures:", root.label().getString());
        assertFalse(root.logicalGroup());
        assertFalse(root.checked());
        assertTrue(root.children().get(0).checked() || root.children().get(1).checked());
        assertEquals(Formatting.AQUA.getColorValue(), root.children().get(0).label().getStyle().getColor().getRgb());
        progress.visits.add(C);
        root = rules.requirements(B, progress);
        assertTrue(root.checked());
        assertTrue(root.children().get(0).checked());
        assertTrue(root.children().get(1).checked());
    }

    @Test void requirementPacketPreservesChecksAndColors() {
        Progress progress = new Progress();
        progress.visits.add(A);
        RequirementNode original = condition("{\"requiredVisits\":[\"minecraft:ancient_city\",\"minecraft:stronghold\"],\"match\":\"all\"}")
                .requirements(B, progress);
        PacketByteBuf packet = new PacketByteBuf(Unpooled.buffer());
        original.write(packet);
        RequirementNode copy = RequirementNode.read(packet);
        assertFalse(copy.checked());
        assertFalse(copy.logicalGroup());
        assertTrue(copy.children().get(0).checked());
        assertFalse(copy.children().get(1).checked());
        assertEquals(Formatting.AQUA.getColorValue(), copy.children().get(0).label().getStyle().getColor().getRgb());
        PacketByteBuf groupPacket = new PacketByteBuf(Unpooled.buffer());
        new RequirementNode(Text.literal("Do all of:"), true, true, java.util.List.of(original)).write(groupPacket);
        RequirementNode groupCopy = RequirementNode.read(groupPacket);
        assertTrue(groupCopy.logicalGroup());
        assertTrue(groupCopy.checked());
        RequirementNode advancement = condition("{\"requiredAdvancements\":[\"minecraft:end/dragon_egg\"]}")
                .requirements(B, new Progress()).children().get(0);
        PacketByteBuf advancementPacket = new PacketByteBuf(Unpooled.buffer());
        advancement.write(advancementPacket);
        RequirementNode advancementCopy = RequirementNode.read(advancementPacket);
        assertEquals("The Next Generation", advancementCopy.label().getString());
        assertEquals("Hold the Dragon Egg", advancementCopy.tooltipDescription().getString());
        assertEquals(Formatting.DARK_PURPLE.getColorValue(), advancementCopy.label().getStyle().getColor().getRgb());
    }

    @Test void invalidMatchSkipsRule() {
        assertTrue(condition("{\"requiredVisits\":[\"minecraft:ancient_city\"],\"match\":\"typo\"}").requirementNames().isEmpty());
    }

    @Test void latchingSurvivesRequirementLossRuleChangesAndNbtRoundTrip() {
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        UnlockedStructures saved = new UnlockedStructures();
        Progress p = new Progress(); p.visits.add(A);
        UnlockingRules original = condition("{\"requiredVisits\":[\"minecraft:ancient_city\"]}");
        saved.add(player, original.earnedOnOpen(STRUCTURES, p));
        p.visits.clear();
        saved.add(player, original.earnedOnOpen(STRUCTURES, p));
        saved.add(player, rules("{\"default\":\"lockedUntilVisited\"}").earnedOnOpen(STRUCTURES, p));
        saved = UnlockedStructures.fromNbt(saved.writeNbt(new NbtCompound()));
        assertTrue(saved.contains(player, B));
        assertFalse(saved.contains(other, B));
        saved.remove(player, Set.of(B));
        assertFalse(saved.contains(player, B));
        p.visits.add(A);
        saved.add(player, original.earnedOnOpen(STRUCTURES, p));
        assertTrue(saved.contains(player, B));
    }

    @Test void notificationStateSurvivesRestartAndPendingCountsExcludeRelockedStructures() {
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        UnlockedStructures saved = new UnlockedStructures();
        assertFalse(saved.notificationsEnabled(player));
        saved.add(player, Set.of(A, B));
        saved.queueNotification(player, Set.of(A, B));
        saved.remove(player, Set.of(B));
        saved = UnlockedStructures.fromNbt(saved.writeNbt(new NbtCompound()));
        assertEquals(1, saved.takePendingNotificationCount(player));
        assertEquals(0, saved.takePendingNotificationCount(player));
        assertTrue(saved.enableNotifications(player));
        assertFalse(saved.enableNotifications(player));
        saved.add(other, Set.of(C));
        saved.queueNotification(other, Set.of(C));
        saved = UnlockedStructures.fromNbt(saved.writeNbt(new NbtCompound()));
        assertTrue(saved.notificationsEnabled(player));
        assertFalse(saved.notificationsEnabled(other));
        assertEquals(1, saved.takePendingNotificationCount(other));
        saved.queueNotification(player, Set.of(A));
        assertFalse(saved.enableNotifications(player));
        assertEquals(1, saved.takePendingNotificationCount(player));
    }

    @Test void firstCompassClearsPendingAndKeepsAllPreviouslyUnlockedStructures() {
        UUID player = UUID.randomUUID();
        UnlockedStructures saved = new UnlockedStructures();
        saved.add(player, Set.of(A));
        saved.queueNotification(player, Set.of(A));
        saved.add(player, Set.of(B));
        assertTrue(saved.enableNotifications(player));
        saved = UnlockedStructures.fromNbt(saved.writeNbt(new NbtCompound()));
        assertEquals(2, saved.getStructures(player).size());
        assertEquals(0, saved.takePendingNotificationCount(player));
        assertFalse(saved.enableNotifications(player));
    }

    @Test void reearnedCompassRestoresOnlyNewlyUnlockedStructureTypes() {
        UUID player = UUID.randomUUID();
        UnlockedStructures saved = new UnlockedStructures();
        UnlockingRules rules = rules("{}");
        Progress progress = new Progress();
        saved.add(player, STRUCTURES);
        saved.enableNotifications(player);
        saved.remove(player, Set.of(A, B));
        assertEquals(2, saved.add(player, rules.earnedOnOpen(STRUCTURES, progress)));
        assertEquals(0, saved.add(player, rules.earnedOnOpen(STRUCTURES, progress)));
    }

    @Test void rejectsMalformedDocumentsAndInvalidRootFields(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("unlocking_rules.json");
        for (String source : List.of(
                "{\n  \"requirements\": {}",
                "{\n  // comment\n  \"requirements\": {}\n}",
                "{\n  \"default\": \"bad\nvalue\"\n}",
                "{\n  \"default\": \"missing\"\n}",
                "{\n  \"requirements\": []\n}",
                "[]",
                "{\n  \"rules\": {}\n}",
                "{} {}")) {
            Files.writeString(file, source);
            UnlockingRules loaded = UnlockingRules.loadFile(file, catalog);
            assertEquals(STRUCTURES, loaded.earnedOnOpen(STRUCTURES, new Progress()));
            assertEquals(source, Files.readString(file));
            var diagnostic = loaded.diagnostics(UnlockingRules.Severity.ERROR).get(0);
            assertTrue(diagnostic.message().startsWith("Could not load unlocking_rules.json:"));
            assertFalse(diagnostic.message().contains(file.toString()));
            assertTrue(diagnostic.message().contains("line"), diagnostic.message());
        }
        Files.writeString(file, "{}");
        assertTrue(UnlockingRules.loadFile(file, catalog).diagnostics(null).isEmpty());
    }

    @Test void latestReloadReportIncludesFallbackAndClearsAfterCleanLoad(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("unlocking_rules.json");
        try {
            Files.writeString(file, """
                    {"default":"lockedUntilVisited","requirements":{
                        "first":{"unlockedStructures":["fortress*"],"requiredVisits":["ancient_city"]},
                        "invalid":{"unlockedStructures":["fortress"],"requiredVisits":["typo"]},
                        "last":{"unlockedStructures":["test_set"],"requiredVisits":["ancient_city"]}
                    }}
                    """);
            assertEquals(2, UnlockingRules.reload(file, catalog));
            var errors = UnlockingRules.report(UnlockingRules.Severity.ERROR);
            var warnings = UnlockingRules.report(UnlockingRules.Severity.WARNING);
            assertEquals(1, errors.size());
            assertEquals(1, warnings.size());
            assertEquals(List.of(errors.get(0), warnings.get(0)), UnlockingRules.report(null));

            Files.writeString(file, "{");
            assertEquals(-1, UnlockingRules.reload(file, catalog));
            assertEquals(1, UnlockingRules.report(UnlockingRules.Severity.ERROR).size());
            assertTrue(UnlockingRules.report(UnlockingRules.Severity.WARNING).isEmpty());
            assertTrue(UnlockingRules.get().requirementNames().isEmpty());
            var unclaimed = new FtbStructureRewards.Snapshot(
                    java.util.Map.of(B, List.of(new FtbStructureRewards.Quest(1L, Text.literal("Quest")))), java.util.Map.of(), Set.of());
            assertFalse(UnlockingRules.get().earnedOnOpen(STRUCTURES, new Progress(), unclaimed).contains(B));

            Files.writeString(file, "{}");
            assertEquals(0, UnlockingRules.reload(file, catalog));
            assertTrue(UnlockingRules.report(null).isEmpty());
        } finally {
            UnlockingRules.clear();
        }
    }

    @Test void loadingAndMalformedFileDoNotTouchPlayerData(@TempDir Path directory) throws Exception {
        UUID player = UUID.randomUUID();
        UnlockedStructures saved = new UnlockedStructures(); saved.add(player, Set.of(B));
        Path file = directory.resolve("unlocking_rules.json");
        UnlockingRules initial = UnlockingRules.loadFile(file, catalog);
        assertEquals(STRUCTURES, initial.earnedOnOpen(STRUCTURES, new Progress()));
        Files.writeString(file, "{ broken");
        UnlockingRules fallback = UnlockingRules.loadFile(file, catalog);
        assertEquals(STRUCTURES, fallback.earnedOnOpen(STRUCTURES, new Progress()));
        assertEquals(Set.of(B), saved.getStructures(player));
        assertEquals("{ broken", Files.readString(file));
    }

    @Test void clearVisitsRemovesAllInstancesOfOneTypeAndPreservesOtherPlayer() {
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        NbtList players = new NbtList();
        for (UUID uuid : new UUID[]{player, other}) {
            NbtCompound p = new NbtCompound(); p.putUuid("UUID", uuid);
            NbtList visits = new NbtList();
            for (int i = 0; i < 3; i++) {
                NbtCompound v = new NbtCompound();
                v.putString("Dimension", i == 0 ? "minecraft:overworld" : "minecraft:the_nether");
                v.putString("Structure", (i < 2 ? A : B).toString()); v.putInt("ChunkX", i); v.putInt("ChunkZ", i);
                visits.add(v);
            }
            p.put("Visits", visits); players.add(p);
        }
        NbtCompound nbt = new NbtCompound(); nbt.put("Players", players);
        VisitedStructures history = VisitedStructures.fromNbt(nbt);
        assertEquals(2, history.clear(player, A));
        assertFalse(history.hasVisitedType(player, A));
        assertTrue(history.hasVisitedType(player, B));
        assertTrue(history.hasVisitedType(other, A));
        assertEquals(1, history.clear(player, null));
        assertFalse(history.hasVisitedType(player, B));
        assertEquals(3, history.clear(other, null));
    }
}
