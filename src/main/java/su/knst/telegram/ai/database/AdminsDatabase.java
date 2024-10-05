package su.knst.telegram.ai.database;

import org.jooq.DSLContext;
import org.jooq.Record1;

import static su.knst.telegram.ai.jooq.Tables.ADMINS;

public class AdminsDatabase extends AbstractDatabase {
    public AdminsDatabase(DSLContext context) {
        super(context);
    }

    public boolean chatIsAdmin(long chatId) {
        return context.select(ADMINS.ENABLED)
                .from(ADMINS)
                .where(ADMINS.ID.eq(chatId))
                .fetchOptional()
                .map(Record1::component1)
                .orElse(false);
    }

    public void addAdmin(long chatId) {
        context.insertInto(ADMINS)
                .set(ADMINS.ID, chatId)
                .set(ADMINS.ENABLED, true)
                .execute();
    }

    public void switchAdmin(long chatId, boolean enabled) {
        context.update(ADMINS)
                .set(ADMINS.ENABLED, enabled)
                .where(ADMINS.ID.eq(chatId))
                .execute();
    }
}
