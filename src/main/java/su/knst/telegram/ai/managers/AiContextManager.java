package su.knst.telegram.ai.managers;

import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.stefanbratanov.jvm.openai.Usage;
import org.jooq.JSON;
import su.knst.telegram.ai.config.AiConfig;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.database.*;
import su.knst.telegram.ai.jooq.tables.records.AiContextsRecord;
import su.knst.telegram.ai.jooq.tables.records.AiMessagesRecord;
import su.knst.telegram.ai.utils.CacheHandyBuilder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Singleton
public class AiContextManager {
    protected AiContextsDatabase contextsDatabase;
    protected AiMessagesManager messagesManager;

    protected LoadingCache<Long, Optional<AiContextsRecord>> contextsCache;

    @Inject
    public AiContextManager(DatabaseWorker worker, ConfigWorker configWorker, AiMessagesManager messagesManager) {
        this.contextsDatabase = worker.get(AiContextsDatabase.class);
        this.messagesManager = messagesManager;

        AiConfig.Cache cachingConfig = configWorker.ai.cache;

        this.contextsCache = CacheHandyBuilder.loading(
                1, TimeUnit.DAYS,
                cachingConfig.maxContexts,
                (contextId) -> contextsDatabase.getContext(contextId)
        );
    }

    public Optional<AiContextsRecord> newContext(long chatId, long presetId) {
        Optional<AiContextsRecord> result = contextsDatabase.newContext(chatId, presetId);

        if (result.isPresent()) {
            AiContextsRecord record = result.get();

            contextsCache.put(record.getId(), result);
        }

        return result;
    }

    public Optional<AiContextsRecord> setContextPreset(long contextId, long presetId) {
        Optional<AiContextsRecord> result = contextsDatabase.setContextPreset(contextId, presetId);

        if (result.isPresent()) {
            AiContextsRecord record = result.get();

            contextsCache.put(record.getId(), result);
        }

        return result;
    }

    public List<AiContextsRecord> replacePreset(long oldPresetId, long presetId) {
        List<AiContextsRecord> result = contextsDatabase.replacePreset(oldPresetId, presetId);

        result.forEach((r) -> contextsCache.put(r.getId(), Optional.of(r)));

        return result;
    }

    public boolean chatOwnContext(long chatId, long contextId) {
        try {
            return contextsCache.get(contextId).map(AiContextsRecord::getChatId).orElse(-1L).equals(chatId);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return false;
    }

    public Optional<AiContextsRecord> getContext(long contextId) {
        try {
            return contextsCache.get(contextId);
        }catch (ExecutionException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public List<AiMessagesRecord> deleteContext(long contextId) {
        List<AiMessagesRecord> result = messagesManager.deleteByContext(contextId);

        contextsDatabase.delete(contextId);
        contextsCache.invalidate(contextId);

        return result;
    }
}
