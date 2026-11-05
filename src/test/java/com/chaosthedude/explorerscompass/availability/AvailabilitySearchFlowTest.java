package com.chaosthedude.explorerscompass.availability;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Check the actual request entrypoints without constructing a live player or server in JUnit. */
class AvailabilitySearchFlowTest {
    @Test void disabledRequestsReturnBeforeCostsSessionsAndWorkerCreation() throws Exception {
        ClassNode node=new ClassNode();
        try(var stream=getClass().getResourceAsStream("/com/chaosthedude/explorerscompass/items/ExplorersCompassItem.class")) {
            assertNotNull(stream);new ClassReader(stream).accept(node,0);
        }
        for(String name:new String[]{"searchForStructure","searchForListedStructures"}) {
            var method=node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();
            int availability=-1,xp=-1,session=-1,workers=-1;
            for(int i=0;i<method.instructions.size();i++) if(method.instructions.get(i) instanceof MethodInsnNode call) {
                if(call.name.equals("availableForSearch"))availability=i;
                if(call.name.equals("rejectIfInsufficientXp"))xp=i;
                if(call.name.equals("beginSearch"))session=i;
                if(call.name.equals("createWorkers"))workers=i;
            }
            assertTrue(availability>=0&&availability<xp&&xp<session&&session<workers,name);
            boolean earlyReturn=false;
            for(int i=availability+1;i<xp;i++)if(method.instructions.get(i).getOpcode()==Opcodes.RETURN)earlyReturn=true;
            assertTrue(earlyReturn,"Empty availability must return before starting a search: "+name);
        }
        var gate=node.methods.stream().filter(m->m.name.equals("availableForSearch")).findFirst().orElseThrow();
        for(var instruction:gate.instructions)if(instruction instanceof MethodInsnNode call)
            assertFalse(java.util.Set.of("createWorkers","beginSearch","damageCompass","addExperienceLevels","getChunk").contains(call.name));
    }
}
