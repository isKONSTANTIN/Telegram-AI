package su.knst.telegram.ai.database;

import app.finwave.tat.utils.Pair;
import io.github.stefanbratanov.jvm.openai.Usage;
import org.jooq.DSLContext;
import org.jooq.Record1;
import su.knst.telegram.ai.jooq.tables.records.AiModelsUsageRecord;
import su.knst.telegram.ai.utils.usage.GeneralUsageType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.month;
import static org.jooq.impl.DSL.sum;
import static su.knst.telegram.ai.jooq.Tables.AI_MODELS_USAGE;
import static su.knst.telegram.ai.jooq.Tables.GENERAL_USAGE;

public class UsageDatabase extends AbstractDatabase {
    public UsageDatabase(DSLContext context) {
        super(context);
    }

    public List<Pair<Long, Usage>> getModelUsage(int modelId, LocalDate date) {
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = date.withDayOfMonth(date.getMonth().length(date.isLeapYear()));

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

    public List<Pair<Integer, Usage>> getModelsUsage(long chatId, LocalDate date) {
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = date.withDayOfMonth(date.getMonth().length(date.isLeapYear()));

        return context.select(sum(AI_MODELS_USAGE.COMPLETION_TOKENS_USED), sum(AI_MODELS_USAGE.PROMPT_TOKENS_USED), AI_MODELS_USAGE.MODEL)
                .from(AI_MODELS_USAGE)
                .where(AI_MODELS_USAGE.CHAT_ID.eq(chatId)
                        .and(AI_MODELS_USAGE.DATE.between(start, end)))
                .groupBy(month(AI_MODELS_USAGE.DATE), AI_MODELS_USAGE.MODEL)
                .fetch()
                .map(r -> Pair.of(
                        r.component3(),
                        new Usage(r.component1().intValue(), r.component2().intValue(), r.component1().add(r.component2()).intValue())
                ));
    }

    public List<Pair<Long, BigDecimal>> getGeneralUsage(GeneralUsageType type, LocalDate date) {
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = date.withDayOfMonth(date.getMonth().length(date.isLeapYear()));

        return context.select(sum(GENERAL_USAGE.COST), GENERAL_USAGE.CHAT_ID)
                .from(GENERAL_USAGE)
                .where(GENERAL_USAGE.TYPE.eq(type.name())
                        .and(GENERAL_USAGE.DATE.between(start, end)))
                .groupBy(month(GENERAL_USAGE.DATE), GENERAL_USAGE.CHAT_ID)
                .fetch()
                .map(r -> Pair.of(
                        r.component2(),
                        r.component1()
                ));
    }

    public List<Pair<GeneralUsageType, BigDecimal>> getGeneralsUsage(long chatId, LocalDate date) {
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = date.withDayOfMonth(date.getMonth().length(date.isLeapYear()));

        return context.select(sum(GENERAL_USAGE.COST), GENERAL_USAGE.TYPE)
                .from(GENERAL_USAGE)
                .where(GENERAL_USAGE.CHAT_ID.eq(chatId)
                        .and(GENERAL_USAGE.DATE.between(start, end)))
                .groupBy(month(GENERAL_USAGE.DATE), GENERAL_USAGE.TYPE)
                .fetch()
                .map(r -> Pair.of(
                        GeneralUsageType.valueOf(r.component2()),
                        r.component1()
                ));
    }

    public BigDecimal addUsage(long chatId, GeneralUsageType type, BigDecimal cost, LocalDate date) {
        Optional<BigDecimal> totalCost = context.transactionResult((configuration) -> {
            DSLContext dsl = configuration.dsl();

            boolean recordExists = dsl.selectCount()
                    .from(GENERAL_USAGE)
                    .where(GENERAL_USAGE.TYPE.eq(type.name())
                            .and(GENERAL_USAGE.CHAT_ID.eq(chatId))
                            .and(GENERAL_USAGE.DATE.eq(date)))
                    .fetchOptional()
                    .map(Record1::component1)
                    .orElse(0) != 0;

            if (recordExists)
                return dsl.update(GENERAL_USAGE)
                        .set(GENERAL_USAGE.COST, GENERAL_USAGE.COST.add(cost))
                        .where(GENERAL_USAGE.TYPE.eq(type.name())
                                .and(GENERAL_USAGE.CHAT_ID.eq(chatId))
                                .and(GENERAL_USAGE.DATE.eq(date)))
                        .returningResult(GENERAL_USAGE.COST)
                        .fetchOptional()
                        .map(Record1::component1);

            return dsl.insertInto(GENERAL_USAGE)
                    .set(GENERAL_USAGE.COST, cost)
                    .set(GENERAL_USAGE.TYPE, type.name())
                    .set(GENERAL_USAGE.CHAT_ID, chatId)
                    .set(GENERAL_USAGE.DATE, date)
                    .returningResult(GENERAL_USAGE.COST)
                    .fetchOptional()
                    .map(Record1::component1);
        });

        return totalCost.orElse(BigDecimal.ZERO);
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
