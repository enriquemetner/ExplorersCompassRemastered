package io.github.enriquemetner.explorerscompass.availability;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Check the actual request entrypoints without constructing a live player or server in JUnit. */
class AvailabilitySearchFlowTest {
    @Test void bothSearchTypesValidateBeforeStartingWorkers() throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = getClass().getResourceAsStream("/io/github/enriquemetner/explorerscompass/items/ExplorersCompassItem.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
        }

        for (String name : new String[]{"searchForStructure", "searchForListedStructures"}) {
            var entry = method(node, name);
            assertCallsInOrder(entry, "getAllowedStructureIDs", "startValidatedSearch");
        }

        var search = method(node, "startValidatedSearch");
        assertCallsInOrder(search, "classify", "isEmpty", "sendLocked",
                "rejectIfUnaffordable", "beginSearch", "createWorkers");
        assertCallsInOrder(method(node, "succeed"), "rejectIfUnaffordable", "pay", "finishSearch");
        assertCallsInOrder(method(node, "fail"), "pop", "start", "disabled", "finishSearch");
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
