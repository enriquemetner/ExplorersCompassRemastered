package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.availability.Availability;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisabledStructureViewTest {
    @Test void messagesUseExactRequestedTextAndItalicModNames() {
        assertEquals("A mod or datapack is preventing this structure from generating.",DisabledStructureView.message(Availability.generic()).getString());
        for(var reason:List.of(Availability.Reason.YUNG,Availability.Reason.STRUCTURIFY)) {
            String name=reason==Availability.Reason.YUNG?"YUNG's Better Desert Temples":"Structurify";
            var message=DisabledStructureView.message(Availability.disabled(reason,name,List.of()));
            assertEquals("The mod "+name+" disabled this structure's generation.",message.getString());
            assertTrue(message.getSiblings().stream().anyMatch(t -> t.getString().equals(name)&&t.getStyle().isItalic()));
            assertFalse(message.getString().contains("*"));
        }
    }
}
