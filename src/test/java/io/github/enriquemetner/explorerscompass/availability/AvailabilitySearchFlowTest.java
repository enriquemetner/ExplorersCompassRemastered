package io.github.enriquemetner.explorerscompass.availability;

import io.github.enriquemetner.explorerscompass.items.ExplorersCompassItem;
import io.github.enriquemetner.explorerscompass.workers.SearchWorker;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Check the actual request entrypoints without constructing a live player or server in JUnit. */
class AvailabilitySearchFlowTest {
    @Test void bothSearchTypesValidateBeforeCreatingAWorker() throws Exception {
        ClassNode node = readClass(ExplorersCompassItem.class);

        for (String name : new String[]{"searchForStructure", "searchForListedStructures"}) {
            var entry = method(node, name);
            assertCallsInOrder(entry, "getAllowedStructureIDs", "startValidatedSearch");
        }

        var search = method(node, "startValidatedSearch");
        assertCallsInOrder(search, "classify", "isEmpty", "sendLocked",
                "rejectIfUnaffordable", "beginSearch", "create", "hasWork", "start");
        assertCallsInOrder(method(node, "succeed"), "rejectIfUnaffordable", "pay", "commitFound", "detach", "logFound");
    }

    @Test void absentAndExhaustedWorkersUseTheSameSavedSearchFallback() throws Exception {
        assertNull(SearchWorker.create(null, null, null, List.of(), List.of(), false, false, null));
        ClassNode item = readClass(ExplorersCompassItem.class);
        assertCallsInOrder(method(item, "startValidatedSearch"), "create", "hasWork", "fail");

        ClassNode worker = readClass(SearchWorker.class);
        assertCallsInOrder(method(worker, "doWork"), "canWorkAt", "fail", "advance");
        assertCallsInOrder(method(worker, "fail"), "clear", "fail");

        var fail = method(item, "fail");
        assertCallsInOrder(fail, "restore", "disabled", "canSend", "detach", "send", "wrongDimension", "finishFailure");
        assertEquals(-1, callIndex(fail, "start"), "Exhaustion must end the current search");
    }

    private static ClassNode readClass(Class<?> type) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
        }
        return node;
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
