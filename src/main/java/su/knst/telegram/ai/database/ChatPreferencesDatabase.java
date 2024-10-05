package su.knst.telegram.ai.database;

import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.jooq.Record1;
import su.knst.telegram.ai.jooq.tables.records.ChatsPreferencesRecord;
import su.knst.telegram.ai.utils.ContextMode;

import java.util.Optional;

import static su.knst.telegram.ai.jooq.Tables.CHATS_PREFERENCES;

public class ChatPreferencesDatabase extends AbstractDatabase {
    public ChatPreferencesDatabase(DSLContext context) {
        super(context);
    }

    public Optional<ChatsPreferencesRecord> initPreferences(long chatId, long defaultPreset) {
        return context.insertInto(CHATS_PREFERENCES)
                .set(CHATS_PREFERENCES.DEFAULT_PRESET, defaultPreset)
                .set(CHATS_PREFERENCES.CHAT_ID, chatId)
                .returningResult(CHATS_PREFERENCES)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<ChatsPreferencesRecord> getPreferences(long chatId) {
        return context.selectFrom(CHATS_PREFERENCES)
                .where(CHATS_PREFERENCES.CHAT_ID.eq(chatId))
                .fetchOptional();
    }

    public Optional<ChatsPreferencesRecord> setDefaultPreset(long chatId, long presetId) {
        return context.update(CHATS_PREFERENCES)
                .set(CHATS_PREFERENCES.DEFAULT_PRESET, presetId)
                .where(CHATS_PREFERENCES.CHAT_ID.eq(chatId))
                .returningResult(CHATS_PREFERENCES)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<ChatsPreferencesRecord> setContextMode(long chatId, ContextMode mode) {
        return context.update(CHATS_PREFERENCES)
                .set(CHATS_PREFERENCES.CONTEXTS_MODE, mode.ordinal())
                .where(CHATS_PREFERENCES.CHAT_ID.eq(chatId))
                .returningResult(CHATS_PREFERENCES)
                .fetchOptional()
                .map(Record1::component1);
    }
}
