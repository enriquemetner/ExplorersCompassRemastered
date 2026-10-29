package com.chaosthedude.explorerscompass.compat.ftb;

import net.minecraft.text.Text;

/** Optional client boundary for opening quests from compass requirements. */
public final class FtbQuestNavigation {
    public record QuestInfo(Text title, boolean openable, boolean hiddenTitle, boolean invisibleUntilCompleted) {}

    public interface Provider {
        QuestInfo info(long questId);
        void openQuest(long questId);
        void openChapter(long questId);
    }

    private static Provider provider;

    private FtbQuestNavigation() {}

    public static void register(Provider implementation) { provider = implementation; }
    public static QuestInfo info(long questId) { return provider == null ? null : provider.info(questId); }
    public static void openQuest(long questId) { if (provider != null) provider.openQuest(questId); }
    public static void openChapter(long questId) { if (provider != null) provider.openChapter(questId); }
}
