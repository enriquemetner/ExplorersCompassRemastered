package com.chaosthedude.explorerscompass.gui;

import com.chaosthedude.explorerscompass.availability.*;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import java.util.*;
import java.util.function.Consumer;

/** Persistent, wrapped dialog with scrollable replacement links. */
final class DisabledStructureView {
    private record Line(OrderedText text, Identifier target) {}
    private final TextRenderer renderer;
    private final List<AvailabilitySelection.Disabled> entries;
    private final boolean single;
    private final Runnable back,hide;
    private final Consumer<Identifier> navigate;
    private double scroll;
    DisabledStructureView(TextRenderer renderer,List<AvailabilitySelection.Disabled> entries,boolean single,
            Runnable back,Runnable hide,Consumer<Identifier> navigate) {
        this.renderer=renderer;this.entries=List.copyOf(entries);this.single=single;
        this.back=back;this.hide=hide;this.navigate=navigate;
    }
    static Text message(Availability availability) {
        if(availability.reason()==Availability.Reason.GENERIC)
            return Text.literal("A mod or datapack is preventing this structure from generating.");
        return Text.literal("The mod ").append(Text.literal(availability.modName()).styled(s -> s.withItalic(true)))
                .append(" disabled this structure's generation.");
    }
    private List<Line> lines(int width) {
        List<Line> result=new ArrayList<>();
        for(var entry:entries) {
            if(!single) append(result,Text.literal(StructureUtils.getStructureName(entry.structure())+" ("+entry.structure()+")"),width,null);
            append(result,message(entry.availability()),width,null);
            if(entry.availability().reason()==Availability.Reason.YUNG && !entry.availability().replacements().isEmpty()) {
                append(result,Text.empty(),width,null);
                append(result,Text.literal("Instead search:"),width,null);
                for(Identifier replacement:entry.availability().replacements()) {
                    append(result,Text.literal(StructureUtils.getStructureName(replacement)).styled(s -> s.withUnderline(true)),width,replacement);
                    append(result,Text.literal(replacement.toString()).styled(s -> s.withColor(0xaaaaaa)),width,replacement);
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
    private int height(int screenWidth,int screenHeight){return Math.min(Math.max(1,screenHeight-20),Math.max(120,lines(width(screenWidth)-28).size()*(renderer.fontHeight+3)+76));}
    private double maxScroll(List<Line> lines,int height){return Math.max(0,lines.size()*(renderer.fontHeight+3)-(height-76));}
    void scroll(double amount){scroll=Math.max(0,scroll-amount*(renderer.fontHeight+3)*3);}
    void render(DrawContext context,int sw,int sh,int mx,int my) {
        int w=width(sw),h=height(sw,sh),x=(sw-w)/2,y=(sh-h)/2;
        var lines=lines(w-28);scroll=MathHelper.clamp(scroll,0,maxScroll(lines,h));
        context.getMatrices().push();context.getMatrices().translate(0,0,300);
        context.fill(0,0,sw,sh,0x88000000);context.fill(x-1,y-1,x+w+1,y+h+1,0xff909090);
        context.fill(x,y,x+w,y+h,0xff000000);
        context.drawCenteredTextWithShadow(renderer,Text.literal("Structure Disabled"),x+w/2,y+10,0xffff4444);
        context.enableScissor(x+8,y+30,x+w-8,y+h-42);
        for(int i=0;i<lines.size();i++) {
            int rowY=y+32+i*(renderer.fontHeight+3)-(int)scroll;
            Line row=lines.get(i);
            int color=row.target!=null && my>=rowY && my<rowY+renderer.fontHeight+3 && mx>=x+10 && mx<x+w-10 ? 0xffffaa00 : 0xffffffff;
            context.drawTextWithShadow(renderer,row.text,x+12,rowY,color);
        }
        context.disableScissor();
        if(maxScroll(lines,h)>0) {
            int track=h-76;int thumb=Math.max(8,(int)(track*track/(double)(lines.size()*(renderer.fontHeight+3))));
            int top=y+32+(int)((track-thumb)*scroll/maxScroll(lines,h));
            context.fill(x+w-7,y+32,x+w-5,y+h-44,0xff444444);
            context.fill(x+w-7,top,x+w-5,top+thumb,0xffaaaaaa);
        }
        if(single)button(context,"Hide",x+10,y+h-30,Math.min(70,(w-30)/2),mx,my);
        button(context,"Back",x+w-10-Math.min(70,(w-30)/2),y+h-30,Math.min(70,(w-30)/2),mx,my);
        context.getMatrices().pop();
    }
    private void button(DrawContext c,String text,int x,int y,int w,int mx,int my) {
        c.fill(x,y,x+w,y+20,0xff909090);c.fill(x+1,y+1,x+w-1,y+19,mx>=x&&mx<x+w&&my>=y&&my<y+20?0xff424242:0xff242424);
        c.drawCenteredTextWithShadow(renderer,Text.literal(text),x+w/2,y+6,0xffffffff);
    }
    void click(double mx,double my,int button,int sw,int sh) {
        if(button!=0)return;
        int w=width(sw),h=height(sw,sh),x=(sw-w)/2,y=(sh-h)/2,bw=Math.min(70,(w-30)/2);
        if(my>=y+h-30&&my<y+h-10) {
            if(single&&mx>=x+10&&mx<x+10+bw){hide.run();return;}
            if(mx>=x+w-10-bw&&mx<x+w-10){back.run();return;}
        }
        if(mx<x+10||mx>=x+w-10||my<y+30||my>=y+h-42)return;
        var lines=lines(w-28);scroll=MathHelper.clamp(scroll,0,maxScroll(lines,h));
        int row=(int)((my-y-32+scroll)/(renderer.fontHeight+3));
        if(row>=0&&row<lines.size()&&lines.get(row).target!=null)navigate.accept(lines.get(row).target);
    }
}
