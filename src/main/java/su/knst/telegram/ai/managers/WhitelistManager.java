package su.knst.telegram.ai.managers;

import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.database.AdminsDatabase;
import su.knst.telegram.ai.database.ChatsWhitelistDatabase;
import su.knst.telegram.ai.database.DatabaseWorker;
import su.knst.telegram.ai.jooq.tables.records.ChatsWhitelistRecord;
import su.knst.telegram.ai.utils.CacheHandyBuilder;
import su.knst.telegram.ai.utils.UserPermission;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Singleton
public class WhitelistManager {
    protected long superAdmin;
    protected ChatsWhitelistDatabase chatsWhitelistDatabase;
    protected AdminsDatabase adminsDatabase;

    protected LoadingCache<Long, UserPermission> permissionsCache;
    protected LoadingCache<Long, Boolean> adminsCache;
    protected LoadingCache<Long, Optional<ChatsWhitelistRecord>> whitelistCache;

    @Inject
    public WhitelistManager(ConfigWorker configWorker, DatabaseWorker databaseWorker) {
        this.superAdmin = configWorker.telegram.superAdminId;
        this.chatsWhitelistDatabase = databaseWorker.get(ChatsWhitelistDatabase.class);
        this.adminsDatabase = databaseWorker.get(AdminsDatabase.class);

        this.permissionsCache = CacheHandyBuilder.loading(
                1, TimeUnit.DAYS,
                500,
                this::permission
        );

        this.adminsCache = CacheHandyBuilder.loading(
                1, TimeUnit.DAYS,
                500,
                adminsDatabase::chatIsAdmin
        );

        this.whitelistCache = CacheHandyBuilder.loading(
                1, TimeUnit.DAYS,
                500,
                chatsWhitelistDatabase::getRecord
        );
    }

    protected UserPermission permission(long id) {
        if (superAdmin == id)
            return UserPermission.SUPER_ADMIN;

        try {
            if (!whitelistCache.get(id).map(ChatsWhitelistRecord::getEnabled).orElse(false))
                return UserPermission.UNAUTHORIZED;

            if (adminsCache.get(id))
                return UserPermission.ADMIN;

            return UserPermission.WHITELISTED;
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return UserPermission.UNAUTHORIZED;
    }

    public Optional<ChatsWhitelistRecord> getWhitelistRecord(long id) {
        try {
            return whitelistCache.get(id);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public boolean inWhitelist(long id) {
        try {
            return whitelistCache.get(id).isPresent();
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
        Optional<ChatsWhitelistRecord> record = chatsWhitelistDatabase.add(id);

        whitelistCache.put(id, record);
        permissionsCache.invalidate(id);
    }

    public void switchFromWhitelist(long id, boolean enabled) {
        Optional<ChatsWhitelistRecord> record = chatsWhitelistDatabase.switchAccess(id, enabled);

        whitelistCache.put(id, record);
        permissionsCache.invalidate(id);
    }

    public void editDescription(long id, String description) {
        Optional<ChatsWhitelistRecord> record = chatsWhitelistDatabase.editDescription(id, description);

        whitelistCache.put(id, record);
        permissionsCache.invalidate(id);
    }

    public void switchFromWhitelist(long id) {
        Optional<ChatsWhitelistRecord> record = chatsWhitelistDatabase.switchAccess(id);

        whitelistCache.put(id, record);
        permissionsCache.invalidate(id);
    }

    public void addToAdmins(long id) {
        adminsDatabase.addAdmin(id);

        adminsCache.put(id, true);
        permissionsCache.invalidate(id);
    }

    public void switchAdmin(long id, boolean enabled) {
        adminsDatabase.switchAdmin(id, enabled);

        adminsCache.put(id, enabled);
        permissionsCache.invalidate(id);
    }

    public List<ChatsWhitelistRecord> getWhitelist() {
        List<ChatsWhitelistRecord> result = chatsWhitelistDatabase.getList();
        whitelistCache.cleanUp();
        permissionsCache.invalidateAll();

        result.forEach((r) -> whitelistCache.put(r.getId(), Optional.of(r)));

        return result;
    }
}
