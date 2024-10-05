package su.knst.telegram.ai.scenes.main;

import app.finwave.tat.event.chat.EditedMessageEvent;
import app.finwave.tat.event.chat.MessageReactionEvent;
import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.handlers.scened.ScenedAbstractChatHandler;
import app.finwave.tat.scene.BaseScene;
import app.finwave.tat.utils.MessageBuilder;
import com.pengrad.telegrambot.model.request.ChatAction;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendPhoto;
import su.knst.telegram.ai.jooq.tables.records.AiMessagesRecord;
import su.knst.telegram.ai.jooq.tables.records.ChatsPreferencesRecord;
import su.knst.telegram.ai.managers.ChatPreferencesManager;
import su.knst.telegram.ai.utils.ContextMode;
import su.knst.telegram.ai.utils.functions.FileDownloader;
import su.knst.telegram.ai.utils.functions.FunctionImageResult;
import su.knst.telegram.ai.workers.AiWorker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MainScene extends BaseScene<NewMessageEvent> {
    protected AiWorker aiWorker;

    protected ChatPreferencesManager preferencesManager;
    protected ChatsPreferencesRecord preferences;

    protected UserBridge userBridge;
    protected AiBridge aiBridge;

    protected long lastContext;

    public MainScene(ScenedAbstractChatHandler chatHandler, AiWorker aiWorker, ChatPreferencesManager preferencesManager) {
        super(chatHandler);

        this.aiWorker = aiWorker;
        this.preferencesManager = preferencesManager;

        this.userBridge = new UserBridge(this, chatId, aiWorker);
        this.aiBridge = new AiBridge(this, chatId, aiWorker);

        eventHandler.registerListener(NewMessageEvent.class, userBridge::newMessage);
        eventHandler.registerListener(MessageReactionEvent.class, userBridge::reaction);
        eventHandler.registerListener(EditedMessageEvent.class, userBridge::editedMessage);
    }

    public void resetLastContext() {
        lastContext = -1;
    }

    public void askAndAnswer(long contextId, int replyTo) {
        CompletableFuture<Boolean> future = aiWorker.ask(contextId, chatId, (u) -> {
            try {
                aiBridge.contextUpdate(u, replyTo);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        });

        chatHandler.setActionUntil(future, ChatAction.typing);

        future.whenComplete((r, t) -> {
            if (t == null)
                return;

            chatHandler.sendMessage(MessageBuilder.text("Error"));
            t.printStackTrace();
        });
    }

    public void deleteContext(long contextId) {
        if (lastContext != -1 && contextId == lastContext)
            lastContext = -1;

        List<AiMessagesRecord> records = aiWorker.getContextManager().deleteContext(contextId);

        records.stream()
                .map(AiMessagesRecord::getMessageId)
                .filter(id -> id != -1)
                .map(Long::intValue)
                .forEach(chatHandler::deleteMessage);
    }

    public void deleteContext(int messageId) {
        Optional<AiMessagesRecord> messagesRecord = aiWorker.getMessagesManager().getMessage(chatId, messageId);

        if (messagesRecord.isEmpty())
            return;

        deleteContext(messagesRecord.get().getAiContext());
    }

    public void deleteContext() {
        if (lastContext != -1) {
            deleteContext(lastContext);

            lastContext = -1;
        }
    }

    @Override
    public void start() {
        super.start();

        this.preferences = preferencesManager.getPreferences(chatId).orElseThrow();
    }

    @Override
    public void start(NewMessageEvent arg) {
        super.start(arg);

        this.userBridge.newMessage(arg);
    }

    @Override
    public void stop() {
        super.stop();
    }
}