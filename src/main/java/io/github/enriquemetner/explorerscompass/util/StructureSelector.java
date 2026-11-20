package io.github.enriquemetner.explorerscompass.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.minecraft.util.Identifier;

/** Matches structure or structure-set IDs, with optional * wildcards. */
public final class StructureSelector {
    private final Identifier exactId;
    private final Pattern pattern;

    public StructureSelector(String selector) {
        int colon = selector.indexOf(':');
        String normalized = colon < 0 ? "minecraft:" + selector : selector;
        Identifier validated = Identifier.tryParse(normalized.replace('*', 'a'));
        if (colon == 0 || colon != selector.lastIndexOf(':') || validated == null || validated.getPath().isEmpty()) {
            throw new IllegalArgumentException("Invalid structure ID or pattern " + selector);
        }

        if (normalized.contains("*")) {
            exactId = null;
            StringBuilder regex = new StringBuilder();
            for (char character : normalized.toCharArray()) {
                if (character == '*') regex.append(".*");
                else regex.append(Pattern.quote(String.valueOf(character)));
            }
            pattern = Pattern.compile(regex.toString());
        } else {
            exactId = validated;
            pattern = null;
        }
    }

    public boolean matches(Identifier id) {
        return exactId != null ? exactId.equals(id) : pattern.matcher(id.toString()).matches();
    }

    public Set<Identifier> matchingIds(Set<Identifier> candidates) {
        if (exactId != null) return candidates.contains(exactId) ? Set.of(exactId) : Set.of();
        return candidates.stream().filter(this::matches).sorted().collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
