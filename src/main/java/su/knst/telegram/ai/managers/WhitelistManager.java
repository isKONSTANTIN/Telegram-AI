package su.knst.telegram.ai.managers;

import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.database.ChatsDatabase;
import su.knst.telegram.ai.database.DatabaseWorker;
import su.knst.telegram.ai.jooq.tables.records.ChatsRecord;
import su.knst.telegram.ai.utils.CacheHandyBuilder;
import su.knst.telegram.ai.utils.UserPermission;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Singleton
public class WhitelistManager {
    protected long superAdmin;
    protected ChatsDatabase chatsDatabase;

    protected LoadingCache<Long, UserPermission> permissionsCache;
    protected LoadingCache<Long, Optional<ChatsRecord>> chatsCache;

    @Inject
    public WhitelistManager(ConfigWorker configWorker, DatabaseWorker databaseWorker) {
        this.superAdmin = configWorker.telegram.superAdminId;
        this.chatsDatabase = databaseWorker.get(ChatsDatabase.class);

        this.permissionsCache = CacheHandyBuilder.loading(
                1, TimeUnit.DAYS,
                500,
                this::permission
        );

        this.chatsCache = CacheHandyBuilder.loading(
                1, TimeUnit.DAYS,
                500,
                chatsDatabase::getRecord
        );
    }

    protected UserPermission permission(long id) {
        if (superAdmin == id)
            return UserPermission.SUPER_ADMIN;

        try {
            Optional<ChatsRecord> chat = chatsCache.get(id);

            if (chat.isEmpty() || !chat.get().getEnabled())
                return UserPermission.UNAUTHORIZED;

            if (chat.get().getIsAdmin())
                return UserPermission.ADMIN;

            return UserPermission.WHITELISTED;
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return UserPermission.UNAUTHORIZED;
    }

    public Optional<ChatsRecord> getWhitelistRecord(long id) {
        try {
            return chatsCache.get(id);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public boolean inWhitelist(long id) {
        try {
            return chatsCache.get(id).isPresent();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return false;
    }

    public UserPermission getPermission(long id) {
        try {
            return permissionsCache.get(id);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return UserPermission.UNAUTHORIZED;
    }

    public void addToWhitelist(long id) {
        Optional<ChatsRecord> record = chatsDatabase.add(id);

        chatsCache.put(id, record);
        permissionsCache.invalidate(id);
    }

    public void switchFromWhitelist(long id, boolean enabled) {
        Optional<ChatsRecord> record = chatsDatabase.switchAccess(id, enabled);

        chatsCache.put(id, record);
        permissionsCache.invalidate(id);
    }

    public void editDescription(long id, String description) {
        Optional<ChatsRecord> record = chatsDatabase.editDescription(id, description);

        chatsCache.put(id, record);
        permissionsCache.invalidate(id);
    }

    public void switchFromWhitelist(long id) {
        Optional<ChatsRecord> record = chatsDatabase.switchAccess(id);

        chatsCache.put(id, record);
        permissionsCache.invalidate(id);
    }

    public void switchAdmin(long id) {
        Optional<ChatsRecord> record =  chatsDatabase.switchAdmin(id);

        chatsCache.put(id, record);
        permissionsCache.invalidate(id);
    }

    public List<ChatsRecord> getChats() {
        List<ChatsRecord> result = chatsDatabase.getList();
        chatsCache.cleanUp();
        permissionsCache.invalidateAll();

        result.forEach((r) -> chatsCache.put(r.getId(), Optional.of(r)));

        return result;
    }
}
