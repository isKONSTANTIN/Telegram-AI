package su.knst.telegram.ai.database;

import org.jooq.DSLContext;
import org.jooq.Record1;
import su.knst.telegram.ai.jooq.tables.records.ChatsRecord;

import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.not;
import static su.knst.telegram.ai.jooq.Tables.CHATS;

public class ChatsDatabase extends AbstractDatabase {
    public ChatsDatabase(DSLContext context) {
        super(context);
    }

    public Optional<ChatsRecord> add(long id) {
        return context.insertInto(CHATS)
                .set(CHATS.ID, id)
                .set(CHATS.ENABLED, true)
                .returningResult(CHATS)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<ChatsRecord> getRecord(long id) {
        return context.selectFrom(CHATS)
                .where(CHATS.ID.eq(id))
                .fetchOptional();
    }

    public Optional<ChatsRecord> switchAccess(long id, boolean enabled) {
        return context.update(CHATS)
                .set(CHATS.ENABLED, enabled)
                .where(CHATS.ID.eq(id))
                .returningResult(CHATS)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<ChatsRecord> editDescription(long id, String description) {
        return context.update(CHATS)
                .set(CHATS.DESCRIPTION, description)
                .where(CHATS.ID.eq(id))
                .returningResult(CHATS)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<ChatsRecord> switchAccess(long id) {
        return context.update(CHATS)
                .set(CHATS.ENABLED, not(CHATS.ENABLED))
                .where(CHATS.ID.eq(id))
                .returningResult(CHATS)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<ChatsRecord> switchAdmin(long id) {
        return context.update(CHATS)
                .set(CHATS.IS_ADMIN, not(CHATS.IS_ADMIN))
                .where(CHATS.ID.eq(id))
                .returningResult(CHATS)
                .fetchOptional()
                .map(Record1::component1);
    }

    public List<ChatsRecord> getList() {
        return context.selectFrom(CHATS)
                .orderBy(CHATS.IS_ADMIN.desc(), CHATS.ENABLED.desc(), CHATS.ID)
                .fetch();
    }
}
