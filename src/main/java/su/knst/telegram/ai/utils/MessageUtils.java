package su.knst.telegram.ai.utils;

import app.finwave.tat.utils.MessageBuilder;

public class MessageUtils {
    public static void appendBar(MessageBuilder builder, float percent, int size) {
        int filled = Math.round(percent * size);
        boolean lastFullFilled = filled - Math.round(percent * size * 10) / 10f < 0 || filled == Math.round(percent * size * 10) / 10f;

        builder.append("║");

        for (int i = 0; i < filled; i++)
            builder.append(i != filled - 1 || lastFullFilled ? "▓" : "▒");

        for (int i = 0; i < size - filled; i++)
            builder.append("░");

        builder.append("║");
    }
}
