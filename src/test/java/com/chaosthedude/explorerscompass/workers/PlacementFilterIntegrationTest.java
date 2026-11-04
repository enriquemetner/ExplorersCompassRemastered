package com.chaosthedude.explorerscompass.workers;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class PlacementFilterIntegrationTest {
    @Test void mixinTargetOccursOnceAfterSavedRecordLookupAndBeforeGenerationCache() throws Exception {
        try (var input = getClass().getResourceAsStream("/net/minecraft/world/StructureLocator.class")) {
            assertNotNull(input);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            var method = node.methods.stream().filter(m -> m.name.equals("getStructurePresence") && m.desc.equals(
                    "(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/gen/structure/Structure;Z)Lnet/minecraft/world/StructurePresence;"))
                    .findFirst().orElseThrow();
            int saved = -1, gate = -1, compute = -1, gates = 0;
            for (int i = 0; i < method.instructions.size(); i++) {
                var instruction = method.instructions.get(i);
                if (instruction instanceof MethodInsnNode call) {
                    if (call.name.equals("getStructurePresence") && call.desc.contains(";ZJ)")) saved = i;
                    if (call.name.equals("computeIfAbsent")) compute = i;
                }
                if (instruction instanceof FieldInsnNode field && field.name.equals("generationPossibilityByStructure")) {
                    gate = i; gates++;
                }
            }
            assertEquals(1, gates);
            assertTrue(saved >= 0 && saved < gate && gate < compute,
                    "The filter must preserve saved records and must not pollute the generation cache");
        }
    }
}
