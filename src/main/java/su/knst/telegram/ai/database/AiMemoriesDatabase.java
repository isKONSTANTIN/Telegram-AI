package su.knst.telegram.ai.database;

import org.jooq.DSLContext;
import org.jooq.Record1;
import su.knst.telegram.ai.jooq.tables.records.AiContextMemoriesRecord;

import java.util.Optional;

import static su.knst.telegram.ai.jooq.Tables.AI_CONTEXT_MEMORIES;

public class AiMemoriesDatabase extends AbstractDatabase {
    public AiMemoriesDatabase(DSLContext context) {
        super(context);
    }

    public Optional<AiContextMemoriesRecord> addMemory(long contextId, long lastMessage, String memory) {
        return context.insertInto(AI_CONTEXT_MEMORIES)
                .set(AI_CONTEXT_MEMORIES.AI_CONTEXT, contextId)
                .set(AI_CONTEXT_MEMORIES.LAST_MESSAGE, lastMessage)
                .set(AI_CONTEXT_MEMORIES.MEMORY, memory)
                .returningResult(AI_CONTEXT_MEMORIES)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<AiContextMemoriesRecord> getLastMemory(long contextId) {
        return context.selectFrom(AI_CONTEXT_MEMORIES)
                .where(AI_CONTEXT_MEMORIES.AI_CONTEXT.eq(contextId))
                .orderBy(AI_CONTEXT_MEMORIES.ID.desc())
                .limit(1)
                .fetchOptional();
    }
}
