package su.knst.telegram.ai.database;

import app.finwave.tat.utils.Pair;
import io.github.stefanbratanov.jvm.openai.Usage;
import org.jooq.DSLContext;
import org.jooq.Record1;
import su.knst.telegram.ai.jooq.tables.records.AiModelsRecord;
import su.knst.telegram.ai.jooq.tables.records.AiModelsUsageRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.*;
import static su.knst.telegram.ai.jooq.Tables.AI_MODELS;
import static su.knst.telegram.ai.jooq.Tables.AI_MODELS_USAGE;

public class AiModelsDatabase extends AbstractDatabase {
    public AiModelsDatabase(DSLContext context) {
        super(context);
    }

    public Optional<AiModelsRecord> getModel(int modelId) {
        return context.selectFrom(AI_MODELS)
                .where(AI_MODELS.ID.eq(modelId))
                .fetchOptional();
    }

    public List<AiModelsRecord> allowedModels() {
        return context.selectFrom(AI_MODELS).fetch();
    }

    public void switchModel(int modelId) {
        context.update(AI_MODELS)
                .set(AI_MODELS.ENABLED, not(AI_MODELS.ENABLED))
                .where(AI_MODELS.ID.eq(modelId))
                .execute();
    }

    public Optional<AiModelsRecord> addModel(short server, String name, String model) {
        return context.insertInto(AI_MODELS)
                .set(AI_MODELS.SERVER, server)
                .set(AI_MODELS.NAME, name)
                .set(AI_MODELS.MODEL, model)
                .set(AI_MODELS.INCLUDED_TOOLS, new String[]{})
                .set(AI_MODELS.ENABLED, true)
                .returningResult(AI_MODELS)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<AiModelsRecord> editModel(int modelId, short server, String name, String model, String[] tools) {
        return context.update(AI_MODELS)
                .set(AI_MODELS.SERVER, server)
                .set(AI_MODELS.NAME, name)
                .set(AI_MODELS.MODEL, model)
                .set(AI_MODELS.INCLUDED_TOOLS, tools)
                .set(AI_MODELS.ENABLED, true)
                .where(AI_MODELS.ID.eq(modelId))
                .returningResult(AI_MODELS)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<AiModelsRecord> editCosts(int modelId, BigDecimal completion, BigDecimal prompt) {
        return context.update(AI_MODELS)
                .set(AI_MODELS.COMPLETION_TOKENS_COST, completion)
                .set(AI_MODELS.PROMPT_TOKENS_COST, prompt)
                .where(AI_MODELS.ID.eq(modelId))
                .returningResult(AI_MODELS)
                .fetchOptional()
                .map(Record1::component1);
    }
}
