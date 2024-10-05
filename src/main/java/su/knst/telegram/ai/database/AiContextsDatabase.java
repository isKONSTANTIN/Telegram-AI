package su.knst.telegram.ai.database;

import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.jooq.Record1;
import su.knst.telegram.ai.jooq.tables.records.AiContextsRecord;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static su.knst.telegram.ai.jooq.Tables.AI_CONTEXTS;

public class AiContextsDatabase extends AbstractDatabase {
    public AiContextsDatabase(DSLContext context) {
        super(context);
    }

    public Optional<AiContextsRecord> newContext(long chatId, long presetId) {
        return context.insertInto(AI_CONTEXTS)
                .set(AI_CONTEXTS.CHAT_ID, chatId)
                .set(AI_CONTEXTS.CREATED_AT, OffsetDateTime.now())
                .set(AI_CONTEXTS.LAST_PRESET_ID, presetId)
                .returningResult(AI_CONTEXTS)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<AiContextsRecord> setContextPreset(long contextId, long presetId) {
        return context.update(AI_CONTEXTS)
                .set(AI_CONTEXTS.LAST_PRESET_ID, presetId)
                .where(AI_CONTEXTS.ID.eq(contextId))
                .returningResult(AI_CONTEXTS)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<AiContextsRecord> getContext(long contextId) {
        return context.selectFrom(AI_CONTEXTS)
                .where(AI_CONTEXTS.ID.eq(contextId))
                .fetchOptional();
    }

    public List<AiContextsRecord> getChatContexts(long chatId) {
        return context.selectFrom(AI_CONTEXTS)
                .where(AI_CONTEXTS.CHAT_ID.eq(chatId))
                .fetch();
    }

    public boolean chatOwnContext(long chatId, long contextId) {
        return context.select(AI_CONTEXTS.ID)
                .from(AI_CONTEXTS)
                .where(AI_CONTEXTS.CHAT_ID.eq(chatId).and(AI_CONTEXTS.ID.eq(contextId)))
                .fetchOptional()
                .isPresent();
    }

    public void delete(long contextId) {
        context.delete(AI_CONTEXTS)
                .where(AI_CONTEXTS.ID.eq(contextId))
                .execute();
    }
}
