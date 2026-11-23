package io.github.enriquemetner.explorerscompass.gui.dialog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchStatusDialogTest {
    @Test void onlyExistingTransientNoticesExpire() {
        for (var kind : SearchStatusDialog.Kind.values()) {
            var dialog = SearchStatusDialog.notice(null, kind, 0, 0);
            boolean timed = kind == SearchStatusDialog.Kind.WRONG_DIMENSION || kind == SearchStatusDialog.Kind.INSUFFICIENT_COST;
            assertEquals(timed, dialog.expired(System.currentTimeMillis() + 4000), kind::name);
        }
    }
}
