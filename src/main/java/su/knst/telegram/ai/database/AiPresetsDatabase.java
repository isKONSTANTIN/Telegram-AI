package su.knst.telegram.ai.database;

import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.jooq.Record1;
import su.knst.telegram.ai.jooq.tables.records.AiPresetsRecord;

import java.util.List;
import java.util.Optional;

import static su.knst.telegram.ai.jooq.Tables.AI_PRESETS;

public class AiPresetsDatabase extends AbstractDatabase {
    public AiPresetsDatabase(DSLContext context) {
        super(context);
    }

    public Optional<AiPresetsRecord> addPreset(long chatId, int model, float temperature, float topP, float frequencyPenalty, float presencePenalty, int maxTokens, String text, String tag) {
        return context.insertInto(AI_PRESETS)
                .set(AI_PRESETS.CHAT_ID, chatId)
                .set(AI_PRESETS.TEMPERATURE, temperature)
                .set(AI_PRESETS.MODEL, model)
                .set(AI_PRESETS.TOP_P, topP)
                .set(AI_PRESETS.FREQUENCY_PENALTY, frequencyPenalty)
                .set(AI_PRESETS.PRESENCE_PENALTY, presencePenalty)
                .set(AI_PRESETS.MAX_TOKENS, maxTokens)
                .set(AI_PRESETS.TEXT, text)
                .set(AI_PRESETS.TAG, tag)
                .returningResult(AI_PRESETS)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<AiPresetsRecord> editPreset(long presetId, int model, float temperature, float topP, float frequencyPenalty, float presencePenalty, int maxTokens, String text, String tag) {
        return context.update(AI_PRESETS)
                .set(AI_PRESETS.TEMPERATURE, temperature)
                .set(AI_PRESETS.MODEL, model)
                .set(AI_PRESETS.TOP_P, topP)
                .set(AI_PRESETS.FREQUENCY_PENALTY, frequencyPenalty)
                .set(AI_PRESETS.PRESENCE_PENALTY, presencePenalty)
                .set(AI_PRESETS.MAX_TOKENS, maxTokens)
                .set(AI_PRESETS.TEXT, text)
                .set(AI_PRESETS.TAG, tag)
                .where(AI_PRESETS.ID.eq(presetId))
                .returningResult(AI_PRESETS)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<AiPresetsRecord> getPreset(long presetId) {
        return context.selectFrom(AI_PRESETS)
                .where(AI_PRESETS.ID.eq(presetId))
                .fetchOptional();
    }

    public Optional<AiPresetsRecord> deletePreset(long presetId) {
        return context.deleteFrom(AI_PRESETS)
                .where(AI_PRESETS.ID.eq(presetId))
                .returningResult(AI_PRESETS)
                .fetchOptional()
                .map(Record1::component1);
    }

    public List<AiPresetsRecord> getList(long chatId) {
        return context.selectFrom(AI_PRESETS)
                .where(AI_PRESETS.CHAT_ID.eq(chatId))
                .fetch();
    }
}
