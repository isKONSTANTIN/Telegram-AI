package su.knst.telegram.ai.managers;

import app.finwave.rct.reactive.property.Property;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import su.knst.telegram.ai.config.AiConfig;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.database.AiMemoriesDatabase;
import su.knst.telegram.ai.database.DatabaseWorker;
import su.knst.telegram.ai.jooq.tables.records.AiContextMemoriesRecord;
import su.knst.telegram.ai.jooq.tables.records.AiMessagesRecord;
import su.knst.telegram.ai.utils.CacheHandyBuilder;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Singleton
public class AiMemoryManager {
    protected AiMemoriesDatabase database;

    protected LoadingCache<Long, Optional<AiContextMemoriesRecord>> lastMemoryCache;

    @Inject
    public AiMemoryManager(DatabaseWorker worker, ConfigWorker configWorker) {
        this.database = worker.get(AiMemoriesDatabase.class);

        Property<AiConfig> config = configWorker.ai;
        initCache(config.get().cache);

        config.addChangeListener((n) -> initCache(n.cache));
    }

    protected void initCache(AiConfig.Cache cache) {
        this.lastMemoryCache = CacheHandyBuilder.loading(
                1, TimeUnit.DAYS,
                cache.maxMemories,
                (contextId) -> database.getLastMemory(contextId)
        );
    }

    public Optional<AiContextMemoriesRecord> getLastMemory(long contextId) {
        try {
            return lastMemoryCache.get(contextId);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public Optional<AiContextMemoriesRecord> addMemory(long contextId, long lastMessage, String memory) {
        Optional<AiContextMemoriesRecord> result = database.addMemory(contextId, lastMessage, memory);

        lastMemoryCache.put(contextId, result);

        return result;
    }
}
