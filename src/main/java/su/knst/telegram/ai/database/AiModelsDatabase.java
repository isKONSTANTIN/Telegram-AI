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

    public List<Pair<Long, Usage>> getModelUsage(int modelId, LocalDate date) {
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = date.withDayOfMonth(date.getMonth().maxLength());

        return context.select(sum(AI_MODELS_USAGE.COMPLETION_TOKENS_USED), sum(AI_MODELS_USAGE.PROMPT_TOKENS_USED), AI_MODELS_USAGE.CHAT_ID)
                .from(AI_MODELS_USAGE)
                .where(AI_MODELS_USAGE.MODEL.eq(modelId)
                        .and(AI_MODELS_USAGE.DATE.between(start, end)))
                .groupBy(month(AI_MODELS_USAGE.DATE), AI_MODELS_USAGE.CHAT_ID)
                .fetch()
                .map(r -> Pair.of(
                        r.component3(),
                        new Usage(r.component1().intValue(), r.component2().intValue(), r.component1().add(r.component2()).intValue())
                ));
    }

    public Usage addUsage(int modelId, long chatId, LocalDate date, Usage usage) {
        Optional<AiModelsUsageRecord> recordOptional = context.transactionResult((configuration) -> {
            DSLContext dsl = configuration.dsl();

            boolean recordExists = dsl.selectCount()
                    .from(AI_MODELS_USAGE)
                    .where(AI_MODELS_USAGE.MODEL.eq(modelId)
                            .and(AI_MODELS_USAGE.CHAT_ID.eq(chatId))
                            .and(AI_MODELS_USAGE.DATE.eq(date)))
                    .fetchOptional()
                    .map(Record1::component1)
                    .orElse(0) != 0;

            if (recordExists)
                return dsl.update(AI_MODELS_USAGE)
                        .set(AI_MODELS_USAGE.COMPLETION_TOKENS_USED, AI_MODELS_USAGE.COMPLETION_TOKENS_USED.add(usage.completionTokens()))
                        .set(AI_MODELS_USAGE.PROMPT_TOKENS_USED, AI_MODELS_USAGE.PROMPT_TOKENS_USED.add(usage.promptTokens()))
                        .where(AI_MODELS_USAGE.MODEL.eq(modelId)
                                .and(AI_MODELS_USAGE.CHAT_ID.eq(chatId))
                                .and(AI_MODELS_USAGE.DATE.eq(date)))
                        .returningResult(AI_MODELS_USAGE)
                        .fetchOptional()
                        .map(Record1::component1);

            return dsl.insertInto(AI_MODELS_USAGE)
                    .set(AI_MODELS_USAGE.COMPLETION_TOKENS_USED, usage.completionTokens())
                    .set(AI_MODELS_USAGE.PROMPT_TOKENS_USED, usage.promptTokens())
                    .set(AI_MODELS_USAGE.MODEL, modelId)
                    .set(AI_MODELS_USAGE.CHAT_ID, chatId)
                    .set(AI_MODELS_USAGE.DATE, date)
                    .returningResult(AI_MODELS_USAGE)
                    .fetchOptional()
                    .map(Record1::component1);
        });

        if (recordOptional.isPresent()) {
            AiModelsUsageRecord record = recordOptional.get();
            int completionTokensUsed = record.getCompletionTokensUsed();
            int promptTokensUsed = record.getPromptTokensUsed();

            return new Usage(completionTokensUsed, promptTokensUsed, completionTokensUsed + promptTokensUsed);
        }

        return new Usage(0, 0, 0);
    }
}
