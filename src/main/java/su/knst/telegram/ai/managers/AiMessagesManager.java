package su.knst.telegram.ai.managers;

import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.stefanbratanov.jvm.openai.Usage;
import org.flywaydb.core.internal.util.Pair;
import org.jooq.JSON;
import su.knst.telegram.ai.config.AiConfig;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.database.AiMessagesDatabase;
import su.knst.telegram.ai.database.DatabaseWorker;
import su.knst.telegram.ai.jooq.tables.records.AiMessagesRecord;
import su.knst.telegram.ai.utils.CacheHandyBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Singleton
public class AiMessagesManager {
    protected AiMessagesDatabase messagesDatabase;

    protected LoadingCache<Long, ArrayList<AiMessagesRecord>> contextMessagesCache;
    protected LoadingCache<Pair<Long, Long>, Optional<AiMessagesRecord>> chatMessageCache;

    @Inject
    public AiMessagesManager(DatabaseWorker worker, ConfigWorker configWorker) {
        this.messagesDatabase = worker.get(AiMessagesDatabase.class);

        AiConfig.Cache cachingConfig = configWorker.ai.cache;

        this.contextMessagesCache = CacheHandyBuilder.loading(
                1, TimeUnit.DAYS,
                cachingConfig.maxContexts,
                (contextId) -> new ArrayList<>(messagesDatabase.getMessages(contextId)),
                (i) -> {
                    ArrayList<AiMessagesRecord> messages = i.getValue();

                    messages.stream()
                            .map((m) -> Pair.of(m.getChatId(), m.getMessageId()))
                            .forEach(chatMessageCache::invalidate);
                }
        );

        this.chatMessageCache = CacheHandyBuilder.loading(
                1, TimeUnit.DAYS,
                cachingConfig.maxMessages,
                (p) -> messagesDatabase.getMessage(p.getLeft(), p.getRight())
        );
    }

    public Optional<AiMessagesRecord> pushMessage(long contextId, long chatId, String role, JSON content) {
        Optional<AiMessagesRecord> pushed = messagesDatabase.pushMessage(contextId, chatId, role, content);

        if (pushed.isEmpty())
            return pushed;

        ArrayList<AiMessagesRecord> cacheRecords = contextMessagesCache.getIfPresent(contextId);

        if (cacheRecords != null)
            cacheRecords.add(pushed.get());

        return pushed;
    }

    public void linkTelegramMessage(long aiMessageId, long telegramMessageId) {
        Optional<AiMessagesRecord> optionalRecord = messagesDatabase.linkTelegramMessage(aiMessageId, telegramMessageId);

        if (optionalRecord.isPresent()) {
            AiMessagesRecord record = optionalRecord.get();

            chatMessageCache.put(Pair.of(record.getChatId(), telegramMessageId), optionalRecord);

            ArrayList<AiMessagesRecord> messages = contextMessagesCache.getIfPresent(record.getAiContext());

            if (messages == null)
                return;

            int replaceIndex = -1;

            for (int i = 0, messagesSize = messages.size(); i < messagesSize; i++) {
                AiMessagesRecord messageRecord = messages.get(i);

                if (messageRecord.getId().equals(aiMessageId)) {
                    replaceIndex = i;

                    break;
                }
            }

            if (replaceIndex == -1)
                return;

            messages.remove(replaceIndex);
            messages.add(replaceIndex, record);
        }
    }

    public Optional<AiMessagesRecord> getMessage(long chatId, long telegramMessageId) {
        try {
            return chatMessageCache.get(Pair.of(chatId, telegramMessageId));
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public List<AiMessagesRecord> getMessages(long contextId) {
        try {
            return contextMessagesCache.get(contextId);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return List.of();
    }

    public Optional<AiMessagesRecord> getLastMessage(long chatId, String role) {
        Optional<AiMessagesRecord> record = messagesDatabase.getLastTelegramMessage(chatId, role);

        if (record.isPresent())
            chatMessageCache.put(Pair.of(record.get().getChatId(), record.get().getMessageId()), record);

        return record;
    }

    public Optional<AiMessagesRecord> getFirstTelegramMessage(long chatId, long telegramMessageId) {
        Optional<AiMessagesRecord> record = messagesDatabase.getFirstTelegramMessage(chatId, telegramMessageId);

        if (record.isPresent())
            chatMessageCache.put(Pair.of(record.get().getChatId(), record.get().getMessageId()), record);

        return record;
    }

    public List<AiMessagesRecord> deleteAllAfter(long chatId, long aiMessageId) {
        List<AiMessagesRecord> result = messagesDatabase.deleteAllAfter(chatId, aiMessageId);

        long contextId = -1;

        for (AiMessagesRecord deleted : result) {
            if (contextId == -1)
                contextId = deleted.getAiContext();

            if (deleted.getMessageId() != -1)
                chatMessageCache.invalidate(Pair.of(deleted.getChatId(), deleted.getMessageId()));
        }

        if (contextId != -1)
            contextMessagesCache.invalidate(contextId);

        return result;
    }

    public List<AiMessagesRecord> deleteByContext(long contextId) {
        List<AiMessagesRecord> result = messagesDatabase.deleteByContext(contextId);

        contextMessagesCache.invalidate(contextId);

        return result;
    }
}
