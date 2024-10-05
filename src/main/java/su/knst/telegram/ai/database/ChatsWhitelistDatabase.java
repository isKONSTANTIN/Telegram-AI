package su.knst.telegram.ai.database;

import org.jooq.DSLContext;
import org.jooq.Record1;
import su.knst.telegram.ai.jooq.tables.records.ChatsWhitelistRecord;

import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.not;
import static su.knst.telegram.ai.jooq.Tables.CHATS_WHITELIST;

public class ChatsWhitelistDatabase extends AbstractDatabase {
    public ChatsWhitelistDatabase(DSLContext context) {
        super(context);
    }

    public Optional<ChatsWhitelistRecord> add(long id) {
        return context.insertInto(CHATS_WHITELIST)
                .set(CHATS_WHITELIST.ID, id)
                .set(CHATS_WHITELIST.ENABLED, true)
                .returningResult(CHATS_WHITELIST)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<ChatsWhitelistRecord> getRecord(long id) {
        return context.selectFrom(CHATS_WHITELIST)
                .where(CHATS_WHITELIST.ID.eq(id))
                .fetchOptional();
    }

    public Optional<ChatsWhitelistRecord> switchAccess(long id, boolean enabled) {
        return context.update(CHATS_WHITELIST)
                .set(CHATS_WHITELIST.ENABLED, enabled)
                .where(CHATS_WHITELIST.ID.eq(id))
                .returningResult(CHATS_WHITELIST)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<ChatsWhitelistRecord> editDescription(long id, String description) {
        return context.update(CHATS_WHITELIST)
                .set(CHATS_WHITELIST.DESCRIPTION, description)
                .where(CHATS_WHITELIST.ID.eq(id))
                .returningResult(CHATS_WHITELIST)
                .fetchOptional()
                .map(Record1::component1);
    }

    public Optional<ChatsWhitelistRecord> switchAccess(long id) {
        return context.update(CHATS_WHITELIST)
                .set(CHATS_WHITELIST.ENABLED, not(CHATS_WHITELIST.ENABLED))
                .where(CHATS_WHITELIST.ID.eq(id))
                .returningResult(CHATS_WHITELIST)
                .fetchOptional()
                .map(Record1::component1);
    }

    public List<ChatsWhitelistRecord> getList() {
        return context.selectFrom(CHATS_WHITELIST)
                .orderBy(CHATS_WHITELIST.ENABLED.desc())
                .fetch();
    }
}
