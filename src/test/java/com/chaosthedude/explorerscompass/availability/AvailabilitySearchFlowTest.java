package com.chaosthedude.explorerscompass.availability;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Check the actual request entrypoints without constructing a live player or server in JUnit. */
class AvailabilitySearchFlowTest {
    @Test void bothSearchTypesValidateBeforeApplyingErrorPriorityAndStartingWorkers() throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = getClass().getResourceAsStream("/com/chaosthedude/explorerscompass/items/ExplorersCompassItem.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
        }

        for (String name : new String[]{"searchForStructure", "searchForListedStructures"}) {
            var entry = method(node, name);
            assertCallsInOrder(entry, "isBroken", "getAllowedStructureIDs", "startValidatedSearch");
        }

        var preflight = method(node, "availableForSearch");
        assertCallsInOrder(preflight, "classify", "disabled", "locked", "sendLocked",
                "rejectIfInsufficientXp", "rejectWrongDimension");
        for (var instruction : preflight.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                assertFalse(java.util.Set.of("createWorkers", "beginSearch", "damageCompass",
                        "addExperienceLevels", "getChunk").contains(call.name));
            }
        }

        var search = method(node, "startValidatedSearch");
        assertCallsInOrder(search, "availableForSearch", "isEmpty", "beginSearch", "createWorkers");
        assertEquals(Opcodes.RETURN, search.instructions.get(callIndex(search, "isEmpty")).getNext().getNext().getOpcode(),
                "An empty preflight result must return before starting a search");
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static void assertCallsInOrder(MethodNode method, String... names) {
        int previous = -1;
        for (String name : names) {
            int index = callIndex(method, name);
            assertTrue(index > previous, method.name + ": " + name);
            previous = index;
        }
    }

    private static int callIndex(MethodNode method, String name) {
        for (int i = 0; i < method.instructions.size(); i++) {
            if (method.instructions.get(i) instanceof MethodInsnNode call && call.name.equals(name)) return i;
        }
        return -1;
    }
}
