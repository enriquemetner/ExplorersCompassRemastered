package io.github.enriquemetner.explorerscompass.gui;

import io.github.enriquemetner.explorerscompass.availability.*;
import io.github.enriquemetner.explorerscompass.structure.StructureUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Persistent, wrapped dialog with scrollable replacement links. */
final class DisabledStructureView {
    private record Line(OrderedText text, Identifier target) {}
    private final TextRenderer renderer;
    private final List<AvailabilitySelection.Disabled> entries;
    private final boolean single;
    private final Runnable back,hide;
    private final BooleanSupplier hidden;
    private final Consumer<Identifier> navigate;
    private double scroll;
    private Double grabOffset;

    private Scrollbar scrollbar(int x, int y, int width, int height, List<Line> lines) {
        int track = Math.max(1, height - 76);
        double max = maxScroll(lines, height);
        return new Scrollbar(x + width - 15, y + 32, y + 32 + track,
                Scrollbar.pixelThumbHeight(track, max, 12, 0), scroll, max);
    }

    void drag(double mouseY, int sw, int sh) {
        if (grabOffset == null) return;
        int w = width(sw), h = height(sw, sh);
        scroll = scrollbar((sw-w)/2, (sh-h)/2, w, h, lines(w-32)).offsetForMouse(mouseY, grabOffset);
    }

    void release() {
        grabOffset = null;
    }
    DisabledStructureView(TextRenderer renderer,List<AvailabilitySelection.Disabled> entries,boolean single,
            Runnable back,Runnable hide,BooleanSupplier hidden,Consumer<Identifier> navigate) {
        this.renderer=renderer;this.entries=List.copyOf(entries);this.single=single;
        this.back=back;this.hide=hide;this.hidden=hidden;this.navigate=navigate;
    }
    static Text message(Availability availability) {
        if(availability.reason()==Availability.Reason.GENERIC)
            return Text.literal("A mod or datapack is preventing this structure from generating.");
        return Text.literal("The mod ").append(Text.literal(availability.modName()).styled(s -> s.withItalic(true).withUnderline(false)))
                .append(" disabled this structure's generation.");
    }
    private List<Line> lines(int width) {
        List<Line> result=new ArrayList<>();
        for(var entry:entries) {
            append(result, Text.literal(StructureUtils.getStructureName(entry.structure())), width, null);
            append(result, Text.literal(entry.structure().toString()).formatted(net.minecraft.util.Formatting.GRAY), width, null);
            append(result, Text.empty(), width, null);
            append(result,message(entry.availability()),width,null);
            if(entry.availability().reason()==Availability.Reason.YUNG && !entry.availability().replacements().isEmpty()) {
                append(result,Text.empty(),width,null);
                append(result,Text.literal("Instead search:"),width,null);
                for(Identifier replacement:entry.availability().replacements()) {
                    append(result,Text.literal(StructureUtils.getStructureName(replacement)),width,replacement);
                    append(result, Text.literal(replacement.toString()).formatted(net.minecraft.util.Formatting.GRAY), width, null);
                    append(result,Text.empty(),width,null);
                }
            }
            if(!single)append(result,Text.empty(),width,null);
        }
        return result;
    }
    private void append(List<Line> rows,Text text,int width,Identifier target) {
        var wrapped=renderer.wrapLines(text,Math.max(1,width));
        if(wrapped.isEmpty())rows.add(new Line(Text.empty().asOrderedText(),target));
        else for(var row:wrapped)rows.add(new Line(row,target));
    }
    private int width(int screenWidth){return Math.min(Math.max(1,screenWidth-20),380);}
    private int height(int screenWidth,int screenHeight){return Math.min(Math.max(1,screenHeight-20),Math.max(120,lines(width(screenWidth)-32).size()*(renderer.fontHeight+3)+76));}
    private double maxScroll(List<Line> lines,int height){return Math.max(0,lines.size()*(renderer.fontHeight+3)-(height-76));}
    void scroll(double amount){scroll=Math.max(0,scroll-amount*(renderer.fontHeight+3)*3);}
    private int buttonWidth(int width) { return Math.max(1,Math.min(70,(width-30)/2)); }
    private int firstButtonX(int x,int width) { return x+(width-(single?2*buttonWidth(width)+8:buttonWidth(width)))/2; }
    private int backButtonX(int x,int width) { return firstButtonX(x,width)+(single?buttonWidth(width)+8:0); }
    private int hoveredLink(List<Line> lines, int x, int y, int height, double mouseX, double mouseY) {
        if (mouseY < y + 30 || mouseY >= y + height - 42) return -1;
        int row = (int) Math.floor((mouseY - (y + 31) + (int) scroll) / (renderer.fontHeight + 3));
        if (row < 0 || row >= lines.size()) return -1;
        Line line = lines.get(row);
        if (line.target == null) return -1;
        int textX = x + 12;
        return mouseX >= textX - 2 && mouseX < textX + renderer.getWidth(line.text) + 2 ? row : -1;
    }

    void render(DrawContext context,int sw,int sh,int mx,int my) {
        int w=width(sw),h=height(sw,sh),x=(sw-w)/2,y=(sh-h)/2;
        var lines=lines(w-32);scroll=MathHelper.clamp(scroll,0,maxScroll(lines,h));
        context.getMatrices().push();context.getMatrices().translate(0,0,300);
        context.fill(0,0,sw,sh,0x88000000);context.fill(x-1,y-1,x+w+1,y+h+1,0xff909090);
        context.fill(x,y,x+w,y+h,0xff000000);
        context.drawCenteredTextWithShadow(renderer,Text.literal("Structure Disabled"),x+w/2,y+10,0xffff4444);
        context.enableScissor(x+8,y+30,x+w-8,y+h-42);
        int hovered = hoveredLink(lines, x, y, h, mx, my);
        for(int i=0;i<lines.size();i++) {
            int rowY=y+32+i*(renderer.fontHeight+3)-(int)scroll;
            Line row=lines.get(i);
            context.drawTextWithShadow(renderer,row.text,x+12,rowY,i == hovered ? 0xffffaa00 : 0xffffffff);
        }
        context.disableScissor();
        scrollbar(x, y, w, h, lines).render(context, 0xff242424, 0xff909090);
        if(single)button(context,hidden.getAsBoolean()?"Undo":"Hide",firstButtonX(x,w),y+h-30,buttonWidth(w),mx,my);
        button(context,"Back",backButtonX(x,w),y+h-30,buttonWidth(w),mx,my);
        if(single&&!hidden.getAsBoolean()&&mx>=firstButtonX(x,w)&&mx<firstButtonX(x,w)+buttonWidth(w)
                &&my>=y+h-30&&my<y+h-10) {
            context.drawOrderedTooltip(renderer,renderer.wrapLines(Text.literal(EntryDetailsPopup.HIDE_TOOLTIP),Math.max(1,Math.min(220,sw-30))),mx,my);
        }
        context.getMatrices().pop();
    }
    private void button(DrawContext c, String text, int x, int y, int w, int mx, int my) {
        boolean hovered = ButtonPress.contains(mx, my, x, y, w, 20);
        c.fill(x, y, x + w, y + 20, 0xff909090);
        c.fill(x + 1, y + 1, x + w - 1, y + 19, hovered ? 0xff424242 : 0xff242424);
        c.drawCenteredTextWithShadow(renderer, Text.literal(text), x + w / 2, y + 6, 0xffffffff);
    }

    ButtonPress buttonAt(double mx, double my, int sw, int sh) {
        int w = width(sw), h = height(sw, sh), x = (sw - w) / 2, y = (sh - h) / 2;
        int bw = buttonWidth(w);
        if (single && ButtonPress.contains(mx, my, firstButtonX(x, w), y + h - 30, bw, 20)) {
            return new ButtonPress(this, "hide", hide);
        }
        return ButtonPress.at(mx, my, backButtonX(x, w), y + h - 30, bw, 20, this, "back", back);
    }

    boolean click(double mx,double my,int button,int sw,int sh) {
        int w = width(sw), h = height(sw, sh), x = (sw - w) / 2, y = (sh - h) / 2;
        if (mx < x - 1 || mx >= x + w + 1 || my < y - 1 || my >= y + h + 1) {
            back.run();
            return true;
        }
        if (button != 0) return false;
        var lines = lines(w-32);
        Scrollbar bar = scrollbar(x, y, w, h, lines);
        if (bar.contains(mx, my)) {
            grabOffset = bar.grabOffset(my, 0);
            scroll = bar.offsetForMouse(my, grabOffset);
            return false;
        }
        scroll = MathHelper.clamp(scroll, 0, maxScroll(lines, h));
        int row = hoveredLink(lines, x, y, h, mx, my);
        if (row < 0) return false;
        navigate.accept(lines.get(row).target);
        return true;
    }
}
