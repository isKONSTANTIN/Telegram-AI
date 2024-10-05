package su.knst.telegram.ai.database;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSON;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import su.knst.telegram.ai.jooq.tables.records.AiMessagesRecord;

import java.util.List;
import java.util.Optional;

import static su.knst.telegram.ai.jooq.Tables.AI_MESSAGES;

public class AiMessagesDatabase extends AbstractDatabase {
    public AiMessagesDatabase(DSLContext context) {
        super(context);
    }

    public Optional<AiMessagesRecord> pushMessage(long contextId, long telegramMessageId, String role, JSON content) {
        return context.insertInto(AI_MESSAGES)
                .set(AI_MESSAGES.AI_CONTEXT, contextId)
                .set(AI_MESSAGES.ROLE, role)
                .set(AI_MESSAGES.CONTENT, content)
                .set(AI_MESSAGES.MESSAGE_ID, -1L)
                .set(AI_MESSAGES.CHAT_ID, telegramMessageId)
                .returningResult(AI_MESSAGES)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<AiMessagesRecord> linkTelegramMessage(long aiMessageId, long telegramMessageId) {
        return context.update(AI_MESSAGES)
                .set(AI_MESSAGES.MESSAGE_ID, telegramMessageId)
                .where(AI_MESSAGES.ID.eq(aiMessageId))
                .returningResult(AI_MESSAGES)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<AiMessagesRecord> getMessage(long chatId, long telegramMessageId) {
        return context.selectFrom(AI_MESSAGES)
                .where(AI_MESSAGES.CHAT_ID.eq(chatId).and(
                        AI_MESSAGES.MESSAGE_ID.eq(telegramMessageId)))
                .fetchOptional();
    }

    public Optional<AiMessagesRecord> getLastTelegramMessage(long chatId, String role) {
        return context.selectFrom(AI_MESSAGES)
                .where(AI_MESSAGES.CHAT_ID.eq(chatId)
                        .and(AI_MESSAGES.ROLE.eq(role))
                        .and(AI_MESSAGES.MESSAGE_ID.notEqual(-1L))
                )
                .orderBy(AI_MESSAGES.ID.desc())
                .limit(1)
                .fetchOptional();
    }

    public Optional<AiMessagesRecord> getFirstTelegramMessage(long chatId, long telegramMessageId) {
        return context.selectFrom(AI_MESSAGES)
                .where(AI_MESSAGES.CHAT_ID.eq(chatId).and(AI_MESSAGES.MESSAGE_ID.eq(telegramMessageId)))
                .orderBy(AI_MESSAGES.ID.asc())
                .limit(1)
                .fetchOptional();
    }

    public List<AiMessagesRecord> deleteAllAfter(long chatId, long aiMessageId) {
        return context.deleteFrom(AI_MESSAGES)
                .where(AI_MESSAGES.ID.greaterOrEqual(aiMessageId)
                        .and(AI_MESSAGES.CHAT_ID.eq(chatId))
                )
                .returningResult(AI_MESSAGES)
                .fetch()
                .map(Record1::component1);
    }

    public List<AiMessagesRecord> getMessages(long contextId) {
        return context.selectFrom(AI_MESSAGES)
                .where(AI_MESSAGES.AI_CONTEXT.eq(contextId))
                .orderBy(AI_MESSAGES.ID)
                .fetch();
    }

    public List<AiMessagesRecord> deleteByContext(long contextId) {
        return context.deleteFrom(AI_MESSAGES)
                .where(AI_MESSAGES.AI_CONTEXT.eq(contextId))
                .returningResult(AI_MESSAGES)
                .fetch()
                .map(Record1::component1);
    }
}
