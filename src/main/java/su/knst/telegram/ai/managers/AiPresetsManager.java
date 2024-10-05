package su.knst.telegram.ai.managers;

import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import su.knst.telegram.ai.config.AiConfig;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.database.AiPresetsDatabase;
import su.knst.telegram.ai.database.DatabaseWorker;
import su.knst.telegram.ai.jooq.tables.records.AiPresetsRecord;
import su.knst.telegram.ai.utils.CacheHandyBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class AiPresetsManager {
    protected AiPresetsDatabase presetsDatabase;

    protected LoadingCache<Long, ArrayList<AiPresetsRecord>> chatPresetsCache;
    protected LoadingCache<Long, Optional<AiPresetsRecord>> presetsCache;

    @Inject
    public AiPresetsManager(DatabaseWorker worker, ConfigWorker configWorker) {
        this.presetsDatabase = worker.get(AiPresetsDatabase.class);

        AiConfig.Cache cachingConfig = configWorker.ai.cache;

        this.presetsCache = CacheHandyBuilder.loading(
                1, TimeUnit.DAYS,
                cachingConfig.maxPresets,
                (presetId) -> presetsDatabase.getPreset(presetId)
        );

        this.chatPresetsCache = CacheHandyBuilder.loading(
                1, TimeUnit.DAYS,
                cachingConfig.maxPresets,
                (chatId) -> {
                    List<AiPresetsRecord> result = presetsDatabase.getList(chatId);

                    presetsCache.putAll(
                            result.stream().collect(Collectors.toMap(
                                    AiPresetsRecord::getId,
                                    Optional::of
                            ))
                    );

                    return new ArrayList<>(result);
                }
        );
    }

    public Optional<AiPresetsRecord> addPreset(long chatId, int model, float temperature, float topP, float frequencyPenalty, float presencePenalty, int maxTokens, String text, String tag) {
        Optional<AiPresetsRecord> record = presetsDatabase.addPreset(chatId, model, temperature, topP, frequencyPenalty, presencePenalty, maxTokens, text, tag);

        if (record.isEmpty())
            return Optional.empty();

        presetsCache.put(record.get().getId(), record);

        ArrayList<AiPresetsRecord> cacheRecords = chatPresetsCache.getIfPresent(chatId);

        if (cacheRecords != null)
            cacheRecords.add(record.get());

        return record;
    }

    public Optional<AiPresetsRecord> editPreset(long presetId, int model, float temperature, float topP, float frequencyPenalty, float presencePenalty, int maxTokens, String text, String tag) {
        Optional<AiPresetsRecord> edited = presetsDatabase.editPreset(presetId, model, temperature, topP, frequencyPenalty, presencePenalty, maxTokens, text, tag);

        if (edited.isEmpty())
            return Optional.empty();

        chatPresetsCache.invalidate(edited.get().getChatId());
        presetsCache.put(edited.get().getId(), edited);

        return edited;
    }

    public Optional<AiPresetsRecord> getPreset(long presetId) {
        try {
            return presetsCache.get(presetId);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public List<AiPresetsRecord> getPresets(long chatId) {
        try {
            return chatPresetsCache.get(chatId);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return List.of();
    }
}
