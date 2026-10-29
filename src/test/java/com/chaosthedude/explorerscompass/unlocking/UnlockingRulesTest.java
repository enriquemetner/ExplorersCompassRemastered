package com.chaosthedude.explorerscompass.unlocking;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.UUID;
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
    private static final Set<Identifier> STRUCTURES = Set.of(A, B, C);
    private final UnlockingRules.Catalog catalog = new UnlockingRules.Catalog() {
        public Set<Identifier> structures() { return STRUCTURES; }
        public Set<Identifier> structureSets() { return Set.of(SET, new Identifier("minecraft:other_set")); }
        public Set<Identifier> structureSet(Identifier id) { return id.equals(SET) ? Set.of(A, B) : id.toString().equals("minecraft:other_set") ? Set.of(A, C) : Set.of(); }
        public boolean advancementExists(Identifier id) { return id.equals(ADVANCEMENT) || id.equals(DRAGON_EGG); }
        public Text advancementTitle(Identifier id) { return Text.literal(id.equals(DRAGON_EGG) ? "The Next Generation" : "Stone Age"); }
        public Text advancementDescription(Identifier id) { return Text.literal(id.equals(DRAGON_EGG) ? "Hold the Dragon Egg" : "Mine stone"); }
        public Formatting advancementColor(Identifier id) { return id.equals(DRAGON_EGG) ? Formatting.DARK_PURPLE : Formatting.GREEN; }
    };
    private static class Progress implements UnlockingRules.Progress {
        final Set<Identifier> visits = new HashSet<>(), advancements = new HashSet<>();
        public boolean visited(Identifier id) { return visits.contains(id); }
        public boolean completed(Identifier id) { return advancements.contains(id); }
    }
    private UnlockingRules rules(String json) { return UnlockingRules.parse(json, catalog); }
    private String rule(String condition) {
        return "{\"unlocks\":[\"minecraft:fortress\"],\"condition\":" + condition + "}";
    }
    private UnlockingRules condition(String condition) {
        return rules("{\"default\":\"lockedUntilVisited\",\"rules\":{\"test\":" + rule(condition) + "}}");
    }

    @Test void defaultsAndExplicitRules() {
        Progress p = new Progress();
        assertEquals(STRUCTURES, rules("{}").earnedOnOpen(STRUCTURES, p));
        assertTrue(rules("{\"default\":\"lockedUntilVisited\"}").earnedOnOpen(STRUCTURES, p).isEmpty());
        p.visits.add(A);
        assertEquals(Set.of(A), rules("{\"default\":\"lockedUntilVisited\"}").earnedOnOpen(STRUCTURES, p));
        UnlockingRules rules = rules("{\"default\":\"unlocked\",\"rules\":{\"test\":" + rule("{\"type\":\"advancement\",\"advancements\":[\"minecraft:story/mine_stone\"]}") + "}}");
        assertEquals(Set.of(A, C), rules.earnedOnOpen(STRUCTURES, p));
    }

    @Test void ftbRewardsTakePrecedenceWithoutRelockingSavedStructures() {
        UnlockingRules rules = rules("{\"default\":\"unlocked\",\"rules\":{\"visit\":"
                + rule("{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\"]}") + "}}");
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
                List.of("minecraft:test_set", "minecraft:strong*")));
        assertEquals(Set.of(B), UnlockingRules.resolveRewardTargets(catalog, List.of("fortress")));
        assertThrows(IllegalArgumentException.class,
                () -> UnlockingRules.resolveRewardTargets(catalog, List.of("minecraft:fortres?")));
    }

    @Test void anyClaimedFtbRewardUnlocksCurrentTargetsAfterEditsOrRelocking() {
        UnlockingRules rules = rules("{\"default\":\"lockedUntilVisited\"}");
        FtbStructureRewards.Quest first = new FtbStructureRewards.Quest(1L, Text.literal("First"));
        FtbStructureRewards.Quest second = new FtbStructureRewards.Quest(2L, Text.literal("Second"));
        FtbStructureRewards.Snapshot before = new FtbStructureRewards.Snapshot(
                java.util.Map.of(B, List.of(first, second)), java.util.Map.of(10L, Set.of(B), 20L, Set.of(B)));
        assertFalse(rules.earnedOnOpen(STRUCTURES, new Progress(), before, Set.of()).contains(B));
        assertTrue(rules.earnedOnOpen(STRUCTURES, new Progress(), before, Set.of(10L)).contains(B));
        FtbStructureRewards.Snapshot edited = new FtbStructureRewards.Snapshot(
                java.util.Map.of(B, List.of(first, second), C, List.of(first)),
                java.util.Map.of(10L, Set.of(B, C), 20L, Set.of(B)));
        assertEquals(Set.of(B, C), rules.earnedOnOpen(STRUCTURES, new Progress(), edited, Set.of(10L)));
        // Reapplying a claimed reward after a command lock restores the same target.
        UnlockedStructures saved = new UnlockedStructures();
        UUID player = UUID.randomUUID();
        saved.add(player, Set.of(B));
        saved.remove(player, Set.of(B));
        saved.add(player, rules.earnedOnOpen(STRUCTURES, new Progress(), edited, Set.of(10L)));
        assertTrue(saved.contains(player, B));
    }

    @Test void ftbClaimHistoryPersistsAndQuestRequirementsUseDistinctTitles() {
        UUID player = UUID.randomUUID();
        UnlockedStructures saved = new UnlockedStructures();
        saved.recordFtbClaim(player, 12L);
        saved.add(player, Set.of(A));
        saved = UnlockedStructures.fromNbt(saved.writeNbt(new NbtCompound()));
        assertEquals(Set.of(12L), saved.claimedFtbRewards(player));
        saved.remove(player, Set.of(A));
        assertFalse(saved.contains(player, A));
        assertEquals(Set.of(12L), saved.claimedFtbRewards(player));
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
        assertTrue(condition("{\"all\":[{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\"]}]}"
                ).ruleNames().isEmpty());
        assertTrue(condition("{\"any\":[{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\"]}]}"
                ).ruleNames().isEmpty());
        UnlockingRules rules = condition("{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\"]}");
        Progress p = new Progress();
        p.visits.add(A);
        assertTrue(rules.earnedOnProgress(p, A, null).contains(B));
        assertTrue(rules.earnedOnProgress(p, null, new Identifier("test:unrelated")).isEmpty());
    }

    @Test void visitListsAndSetsRespectAnyAll() {
        Progress p = new Progress(); p.visits.add(A);
        UnlockingRules any = condition("{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\",\"minecraft:stronghold\"]}");
        assertTrue(any.earnedOnOpen(STRUCTURES, p).contains(B));
        assertEquals("Visit any Structure:", any.requirements(B, p).label().getString());
        assertFalse(condition("{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\",\"minecraft:stronghold\"],\"match\":\"all\"}").earnedOnOpen(STRUCTURES, p).contains(B));
        assertTrue(condition("{\"type\":\"visit\",\"structures\":[\"minecraft:test_set\"],\"match\":\"any\"}").earnedOnOpen(STRUCTURES, p).contains(B));
        UnlockingRules all = condition("{\"type\":\"visit\",\"structures\":[\"minecraft:test_set\"],\"match\":\"all\"}");
        assertFalse(all.earnedOnOpen(STRUCTURES, p).contains(B));
        p.visits.add(B);
        assertTrue(all.earnedOnOpen(STRUCTURES, p).contains(B));
    }

    @Test void advancementListsRespectAnyAllAndKeepIndividualChecks() {
        String ids = "[\"minecraft:story/mine_stone\",\"minecraft:end/dragon_egg\"]";
        UnlockingRules any = condition("{\"type\":\"advancement\",\"advancements\":" + ids + ",\"match\":\"any\"}");
        UnlockingRules all = condition("{\"type\":\"advancement\",\"advancements\":" + ids + ",\"match\":\"all\"}");
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
        assertTrue(condition("{\"type\":\"advancement\",\"advancements\":[\"minecraft:story/mine_stone\"]}")
                .earnedOnOpen(STRUCTURES, progress).contains(B));
    }

    @Test void advancementConditionRequiresArrayAndValidMatch() {
        assertTrue(condition("{\"type\":\"advancement\",\"id\":\"minecraft:story/mine_stone\"}").ruleNames().isEmpty());
        assertTrue(condition("{\"type\":\"advancement\",\"advancements\":[]}").ruleNames().isEmpty());
        assertTrue(condition("{\"type\":\"advancement\",\"advancements\":[\"minecraft:story/mine_stone\"],\"match\":\"neither\"}").ruleNames().isEmpty());
    }

    @Test void targetsSupportStructureSets() {
        UnlockingRules rules = rules("""
            {"rules":{
              "set":{"unlocks":["minecraft:test_set"],"condition":{"type":"visit","structures":["minecraft:stronghold"]}}
            }}
            """);
        assertEquals(Set.of(A, B), rules.ruleTargets("set"));
    }

    @Test void selectorsCombineDirectStructuresAndMultipleSetsWithoutDuplicates() {
        UnlockingRules rules = rules("""
            {"rules":{"combined":{
              "unlocks":["minecraft:ancient_city","minecraft:*_set"],
              "condition":{"type":"visit","structures":["minecraft:ancient_city","minecraft:*_set"],"match":"all"}
            }}}
            """);
        assertEquals(STRUCTURES, rules.ruleTargets("combined"));
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
            {"default":"lockedUntilVisited","rules":{"patterns":{
              "unlocks":["minecraft:fortress*","minecraft:test_*set"],
              "condition":{"type":"visit","structures":["ancient_cit*"],"match":"any"}
            }}}
            """);
        assertEquals(Set.of(A, B), rules.ruleTargets("patterns"));
        Progress progress = new Progress();
        progress.visits.add(A);
        assertEquals(Set.of(A, B), rules.earnedOnOpen(STRUCTURES, progress));
        assertTrue(condition("{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_cit?\"]}").ruleNames().isEmpty());
        assertTrue(rules("{\"rules\":{\"invalid\":{\"unlocks\":[\"minecraft:fortres?\"],"
                + "\"condition\":{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\"]}}}}").ruleNames().isEmpty());
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
            {"default":"lockedUntilVisited","rules":{"collision":{
              "unlocks":["minecraft:fortress"],
              "condition":{"type":"visit","structures":["minecraft:fortress"]}
            }}}
            """).getAsJsonObject(), collision);
        assertEquals(Set.of(B), rules.ruleTargets("collision"));
        Progress progress = new Progress();
        progress.visits.add(A);
        assertFalse(rules.earnedOnOpen(STRUCTURES, progress).contains(B));
        progress.visits.add(B);
        assertTrue(rules.earnedOnOpen(STRUCTURES, progress).contains(B));
    }

    @Test void selectorsRequireNonemptyArraysAndRejectOldFields() {
        for (String selector : new String[]{"{}", "[]", "null", "\"minecraft:fortress\"",
                "{\"structures\":[\"minecraft:fortress\"]}", "{\"structureSet\":\"minecraft:test_set\"}"}) {
            assertTrue(rules("{\"rules\":{\"bad\":{\"unlocks\":" + selector
                    + ",\"condition\":{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\"]}}}}").ruleNames().isEmpty());
        }
        assertTrue(condition("{\"type\":\"visit\",\"structures\":[]}").ruleNames().isEmpty());
        assertTrue(condition("{\"type\":\"visit\",\"structureSet\":\"minecraft:test_set\"}").ruleNames().isEmpty());
    }

    @Test void laterRuleReplacesEarlierConditionAndInvalidRuleDoesNotOverrideDefault() {
        UnlockingRules rules = rules("{\"rules\":{\"a\":" + rule("{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\"]}")
                + ",\"b\":" + rule("{\"type\":\"advancement\",\"advancements\":[\"minecraft:story/mine_stone\"]}")
                + ",\"invalid\":{\"unlocks\":[\"minecraft:stronghold\"],\"condition\":{\"all\":[]}}}}");
        Progress p = new Progress();
        assertFalse(rules.earnedOnOpen(STRUCTURES, p).contains(B));
        assertTrue(rules.earnedOnOpen(STRUCTURES, p).contains(C));
        p.visits.add(A);
        assertFalse(rules.earnedOnOpen(STRUCTURES, p).contains(B));
        assertFalse(rules.earnedOnProgress(p, A, null).contains(B));
        p.advancements.add(ADVANCEMENT);
        assertTrue(rules.earnedOnOpen(STRUCTURES, p).contains(B));
        assertTrue(rules.earnedOnProgress(p, null, ADVANCEMENT).contains(B));
        assertEquals(Set.of("a", "b"), rules.ruleNames());
    }

    @Test void duplicateTargetLinesReferToSelectorsIncludingExpandedSets() {
        String source = "{\n" +
                "  \"rules\": {\n" +
                "    \"first\": {\"unlocks\": [\n" +
                "      \"minecraft:test_set\"\n" +
                "    ], \"condition\": {\"type\": \"visit\", \"structures\": [\"minecraft:ancient_city\"]}},\n" +
                "    \"second\": {\"unlocks\": [\n" +
                "      \"minecraft:fortress\"\n" +
                "    ], \"condition\": {\"type\": \"advancement\", \"advancements\": [\"minecraft:story/mine_stone\"]}}\n" +
                "  }\n" +
                "}\n";
        assertEquals(4, UnlockingRules.unlockLines(source).get("first").get(0));
        assertEquals(7, UnlockingRules.unlockLines(source).get("second").get(0));
        UnlockingRules rules = rules(source);
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
              "rules": {
                "first": {"unlocks": ["minecraft:fortress"], "condition": {"type": "visit", "structures": ["minecraft:ancient_city"]}},
                "second": {"unlocks": ["minecraft:fortress"], "condition": {"type": "advancement", "advancements": ["minecraft:story/mine_stone"]}},
                "third": {"unlocks": ["minecraft:fortress"], "condition": {"type": "visit", "structures": ["minecraft:stronghold"]}}
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

    @Test void unavailableRequirementsNeverSatisfyAllVacuously() {
        Progress p = new Progress(); p.visits.add(A);
        assertFalse(condition("{\"type\":\"visit\",\"structures\":[\"missing:set\"],\"match\":\"all\"}").earnedOnOpen(STRUCTURES, p).contains(B));
        assertFalse(condition("{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\",\"missing:type\"],\"match\":\"all\"}").earnedOnOpen(STRUCTURES, p).contains(B));
        p.advancements.add(new Identifier("missing:advancement"));
        assertFalse(condition("{\"type\":\"advancement\",\"advancements\":[\"missing:advancement\"]}").earnedOnOpen(STRUCTURES, p).contains(B));
    }

    @Test void ruleDescriptionIsRejectedAndRequirementsAreGenerated() {
        UnlockingRules rules = rules("""
            {"rules":{"hint":{"unlocks":["minecraft:ancient_city"],
              "description":"Follow the lanterns.","condition":{"type":"advancement","advancements":["minecraft:story/mine_stone"]}}}}
            """);
        assertTrue(rules.ruleNames().isEmpty());
        RequirementNode advancement = condition("{\"type\":\"advancement\",\"advancements\":[\"minecraft:story/mine_stone\"]}").requirements(B, new Progress());
        assertEquals("Complete Advancement:", advancement.label().getString());
        assertEquals("Stone Age", advancement.children().get(0).label().getString());
        assertEquals(Formatting.GREEN.getColorValue(), advancement.children().get(0).label().getStyle().getColor().getRgb());
        assertEquals("Mine stone", advancement.children().get(0).tooltipDescription().getString());
    }

    @Test void requirementTreeShowsEachSatisfiedVisitAndCombinedState() {
        UnlockingRules rules = condition("""
            {"type":"visit","structures":["minecraft:ancient_city","minecraft:stronghold"],"match":"all"}
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
        RequirementNode original = condition("{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\",\"minecraft:stronghold\"],\"match\":\"all\"}")
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
        RequirementNode advancement = condition("{\"type\":\"advancement\",\"advancements\":[\"minecraft:end/dragon_egg\"]}")
                .requirements(B, new Progress()).children().get(0);
        PacketByteBuf advancementPacket = new PacketByteBuf(Unpooled.buffer());
        advancement.write(advancementPacket);
        RequirementNode advancementCopy = RequirementNode.read(advancementPacket);
        assertEquals("The Next Generation", advancementCopy.label().getString());
        assertEquals("Hold the Dragon Egg", advancementCopy.tooltipDescription().getString());
        assertEquals(Formatting.DARK_PURPLE.getColorValue(), advancementCopy.label().getStyle().getColor().getRgb());
    }

    @Test void invalidMatchSkipsRule() {
        assertTrue(condition("{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\"],\"match\":\"typo\"}").ruleNames().isEmpty());
    }

    @Test void latchingSurvivesRequirementLossRuleChangesAndNbtRoundTrip() {
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        UnlockedStructures saved = new UnlockedStructures();
        Progress p = new Progress(); p.visits.add(A);
        UnlockingRules original = condition("{\"type\":\"visit\",\"structures\":[\"minecraft:ancient_city\"]}");
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
