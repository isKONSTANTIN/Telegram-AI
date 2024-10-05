package su.knst.telegram.ai.utils;

public enum EmojiList {
    NEXT("➡️"),
    BACK("⬅️"),
    STAR("⭐"),
    MAKE_NOTE("\uD83D\uDCDD"), // 📝
    EXIST_NOTE("\uD83D\uDCCB"), // 📋
    ACCEPT("✅"),
    WARNING("⚠️"),
    SEARCH("\uD83D\uDD0D"), // 🔍
    REFRESH("\uD83D\uDD04"), // 🔄
    SETTINGS("⚙"), // ⚙
    ACCOUNT("\uD83D\uDCB5"), // 💵
    BRAIN("\uD83E\uDDE0"), // 🧠
    LIGHT_BULB("\uD83D\uDCA1"), // 💡
    CLIPBOARD("\uD83D\uDCCB"), // 📋
    TAG("\uD83C\uDFF7\uFE0F"), // 🏷️
    EYES("\uD83D\uDC40"), // 👀
    SPEECH_BALLOON("\uD83D\uDCAC"), // 💬
    BOT("\uD83E\uDD16"), // 🤖
    CREDIT_CARD("\uD83D\uDCB3"),
    CANCEL("\uD83D\uDEAB"); // 🚫

    public final String symbol;

    EmojiList(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public String toString() {
        return symbol;
    }
}