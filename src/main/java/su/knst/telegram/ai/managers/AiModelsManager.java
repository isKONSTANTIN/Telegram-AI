package su.knst.telegram.ai.managers;

import app.finwave.rct.reactive.property.Property;
import app.finwave.tat.utils.Pair;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.stefanbratanov.jvm.openai.Usage;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import su.knst.telegram.ai.config.AiConfig;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.database.DatabaseWorker;
import su.knst.telegram.ai.database.AiModelsDatabase;
import su.knst.telegram.ai.jooq.tables.records.AiModelsRecord;
import su.knst.telegram.ai.utils.CacheHandyBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class AiModelsManager {
    protected AiModelsDatabase database;

    protected LoadingCache<Integer, Optional<AiModelsRecord>> allowedModelsCache;

    @Inject
    public AiModelsManager(DatabaseWorker databaseWorker, ConfigWorker configWorker) {
        this.database = databaseWorker.get(AiModelsDatabase.class);

        Property<AiConfig> config = configWorker.ai;
        initCache(config.get().cache);

        config.addChangeListener((n) -> {
            initCache(n.cache);
        });
    }

    protected void initCache(AiConfig.Cache cache) {
        this.allowedModelsCache = CacheHandyBuilder.loading(
                7, TimeUnit.DAYS,
                cache.maxModels,
                (modelId) -> database.getModel(modelId)
        );
    }

    public Optional<AiModelsRecord> getModel(int modelId) {
        try {
            return allowedModelsCache.get(modelId);
        }catch (ExecutionException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public List<AiModelsRecord> getModels() {
        List<AiModelsRecord> result = database.allowedModels();

        var map = result.stream().collect(Collectors.toMap(
                AiModelsRecord::getId,
                Optional::of
        ));

        allowedModelsCache.putAll(map);

        return result;
    }

    public void switchModel(int modelId) {
        database.switchModel(modelId);
        allowedModelsCache.invalidate(modelId);
    }

    public Optional<AiModelsRecord> editModel(int modelId, short server, String name, String model, String[] tools) {
        Optional<AiModelsRecord> result = database.editModel(modelId, server, name, model, tools);

        if (result.isPresent())
            allowedModelsCache.put(result.get().component1(), result);

        return result;
    }

    public Optional<AiModelsRecord> editCosts(int modelId, BigDecimal completion, BigDecimal prompt) {
        Optional<AiModelsRecord> result = database.editCosts(modelId, completion, prompt);

        if (result.isPresent())
            allowedModelsCache.put(result.get().component1(), result);

        return result;
    }

    public Optional<AiModelsRecord> addModel(short server, String name, String model) {
        Optional<AiModelsRecord> result = database.addModel(server, name, model);

        if (result.isPresent())
            allowedModelsCache.put(result.get().component1(), result);

        return result;
    }
}
