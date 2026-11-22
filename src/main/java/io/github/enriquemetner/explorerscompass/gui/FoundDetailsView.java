package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.ExplorersCompass;
import io.github.enriquemetner.explorerscompass.util.RenderUtils;
import io.github.enriquemetner.explorerscompass.util.StructureUtils;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

final class FoundDetailsView {
    static final int TOP = 50;
    private static final int LINE_STEP = 12;
    private static final String BIOMES_LABEL = "Biomes: ";

    private final TextRenderer textRenderer;
    private final int targetX;
    private final int targetZ;
    private final boolean showCoordinates;
    private final boolean showDistance;
    private final List<Text> biomeNames;
    private final Boolean visited;

    FoundDetailsView(TextRenderer textRenderer, int targetX, int targetZ, boolean showCoordinates,
                     boolean showDistance, List<Identifier> biomes, Set<Identifier> validBiomes,
                     String language, Boolean visited) {
        this.textRenderer = textRenderer;
        this.targetX = targetX;
        this.targetZ = targetZ;
        this.showCoordinates = showCoordinates;
        this.showDistance = showDistance;
        this.biomeNames = sortedBiomeNames(biomes, validBiomes, language);
        this.visited = visited;
    }

    int targetX() {
        return targetX;
    }

    int targetZ() {
        return targetZ;
    }

    static int rowsHeight(int rows, int fontHeight) {
        return (rows - 1) * LINE_STEP + fontHeight;
    }

    int contentHeight() {
        int locationRows = (showDistance ? 1 : 0) + (showCoordinates ? 2 : 0) + (visited != null ? 1 : 0);
        return rowsHeight(Math.max(3 + biomeNames.size(), locationRows), textRenderer.fontHeight);
    }

    private record Line(String label, Text value) {}

    private List<Line> infoLines(Identifier structureId) {
        Identifier setId = structureId == null ? null : ExplorersCompass.structureIDsToSetIDs.get(structureId);
        return List.of(
                new Line("Name: ", Text.literal(StructureUtils.getStructureName(structureId))),
                new Line("Source: ", Text.literal(StructureUtils.getStructureSource(structureId))),
                new Line("Set: ", Text.literal(StructureUtils.formatIdPath(setId))));
    }

    private List<Line> locationLines(PlayerEntity player) {
        List<Line> lines = new ArrayList<>();
        if (showCoordinates) {
            lines.add(new Line("Coordinates:", Text.empty()));
            lines.add(new Line("", Text.literal(targetX + ", " + targetZ)));
        }
        if (showDistance) {
            int distance = player == null ? 0 : StructureUtils.getHorizontalDistanceToLocation(player, targetX, targetZ);
            lines.add(new Line("Distance: ", Text.literal(Integer.toString(distance))));
        }
        if (visited != null) {
            lines.add(new Line("Visited: ", Text.literal(visited ? "Yes" : "No")));
        }
        return lines;
    }

    private int widestLine(List<Line> lines) {
        int width = 0;
        for (Line line : lines) {
            width = Math.max(width, textRenderer.getWidth(line.label()) + textRenderer.getWidth(line.value()));
        }
        return width;
    }

    private static List<Text> sortedBiomeNames(List<Identifier> biomes, Set<Identifier> validBiomes, String language) {
        record BiomeName(Identifier id, Text name) {}
        Collator collator = Collator.getInstance(Locale.forLanguageTag(language.replace('_', '-')));
        return biomes.stream()
                .filter(validBiomes::contains)
                .map(id -> new BiomeName(id, Text.translatableWithFallback(
                        "biome." + id.getNamespace() + "." + id.getPath().replace('/', '.'),
                        StructureUtils.getStructureName(id)).formatted(Formatting.GRAY)))
                .sorted(Comparator.comparing((BiomeName biome) -> biome.name().getString(), collator)
                        .thenComparing(BiomeName::id))
                .map(BiomeName::name)
                .toList();
    }

    void render(DrawContext context, int x, int y, int width, int dialogHeight,
                Identifier structureId, PlayerEntity player) {
        int top = y + TOP;
        int bottom = y + dialogHeight - 30;
        if (bottom <= top) return;
        List<Line> lines = infoLines(structureId);
        List<Line> locationLines = locationLines(player);
        int indent = textRenderer.getWidth(BIOMES_LABEL);
        int leftWidth = width - 24;
        int rightWidth = widestLine(locationLines);
        if (!locationLines.isEmpty()) {
            int leftWanted = widestLine(lines);
            for (Text name : biomeNames) leftWanted = Math.max(leftWanted, indent + textRenderer.getWidth(name));
            int available = Math.max(2, leftWidth - 12);
            int rightLabelWidth = 0;
            for (Line line : locationLines) rightLabelWidth = Math.max(rightLabelWidth, textRenderer.getWidth(line.label()));
            int preferredLeftWidth = textRenderer.getWidth("Source: Repurposed Structures");
            int reservedLeftWidth = Math.min(preferredLeftWidth, Math.max(available / 2, available - rightLabelWidth));
            rightWidth = Math.min(rightWidth, Math.max(rightLabelWidth, available - reservedLeftWidth));
            leftWidth = Math.min(leftWanted, Math.max(1, available - rightWidth));
        }

        int leftX = x + 12;
        int rightX = x + width - 12 - rightWidth;
        context.enableScissor(leftX, top, x + width - 12, bottom);
        for (int i = 0; i < lines.size(); i++) {
            drawDetail(context, lines.get(i), leftX, top + i * LINE_STEP, leftWidth);
        }
        for (int i = 0; i < biomeNames.size(); i++) {
            int biomeY = top + (lines.size() + i) * LINE_STEP;
            if (i == 0) context.drawTextWithShadow(textRenderer, BIOMES_LABEL, leftX, biomeY, 0xffffff);
            RenderUtils.drawTextToFit(context, textRenderer, biomeNames.get(i), leftX + indent, biomeY, leftWidth - indent, 0xaaaaaa);
        }
        for (int i = 0; i < locationLines.size(); i++) {
            drawDetail(context, locationLines.get(i), rightX, top + i * LINE_STEP, rightWidth);
        }
        context.disableScissor();
    }

    private void drawDetail(DrawContext context, Line line, int x, int y, int width) {
        int labelWidth = textRenderer.getWidth(line.label());
        if (labelWidth > 0) context.drawTextWithShadow(textRenderer, line.label(), x, y, 0xffffff);
        RenderUtils.drawTextToFit(context, textRenderer, line.value(), x + labelWidth, y, width - labelWidth, 0xaaaaaa);
    }
}
