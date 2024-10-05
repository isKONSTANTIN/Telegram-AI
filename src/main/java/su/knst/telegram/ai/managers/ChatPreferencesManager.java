package su.knst.telegram.ai.managers;

import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import su.knst.telegram.ai.database.ChatPreferencesDatabase;
import su.knst.telegram.ai.database.DatabaseWorker;
import su.knst.telegram.ai.jooq.tables.records.ChatsPreferencesRecord;
import su.knst.telegram.ai.utils.CacheHandyBuilder;
import su.knst.telegram.ai.utils.ContextMode;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Singleton
public class ChatPreferencesManager {
    protected ChatPreferencesDatabase database;

    protected LoadingCache<Long, Optional<ChatsPreferencesRecord>> preferencesCache;

    @Inject
    public ChatPreferencesManager(DatabaseWorker databaseWorker) {
        this.database = databaseWorker.get(ChatPreferencesDatabase.class);

        preferencesCache = CacheHandyBuilder.loading(
                1, TimeUnit.DAYS,
                100,
                (chatId) -> database.getPreferences(chatId)
        );
    }

    public Optional<ChatsPreferencesRecord> initPreferences(long chatId, long defaultPreset) {
        Optional<ChatsPreferencesRecord> preferences = database.initPreferences(chatId, defaultPreset);

        if (preferences.isEmpty())
            return preferences;

        preferencesCache.put(chatId, preferences);

        return preferences;
    }

    public Optional<ChatsPreferencesRecord> getPreferences(long chatId) {
        try {
            return preferencesCache.get(chatId);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public Optional<ChatsPreferencesRecord> setDefaultPreset(long chatId, long defaultPreset) {
        Optional<ChatsPreferencesRecord> preferences = database.setDefaultPreset(chatId, defaultPreset);

        if (preferences.isEmpty())
            return preferences;

        preferencesCache.put(chatId, preferences);

        return preferences;
    }

    public Optional<ChatsPreferencesRecord> setContextMode(long chatId, ContextMode mode) {
        Optional<ChatsPreferencesRecord> preferences = database.setContextMode(chatId, mode);

        if (preferences.isEmpty())
            return preferences;

        preferencesCache.put(chatId, preferences);

        return preferences;
    }
}
