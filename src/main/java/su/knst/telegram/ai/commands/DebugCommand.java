package su.knst.telegram.ai.commands;

import app.finwave.rct.reactive.property.Property;
import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.handlers.command.AbstractCommand;
import app.finwave.tat.utils.ComposedMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMessage;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.config.TelegramConfig;
import su.knst.telegram.ai.jooq.tables.records.AiMessagesRecord;
import su.knst.telegram.ai.managers.AiContextManager;
import su.knst.telegram.ai.managers.AiMessagesManager;
import su.knst.telegram.ai.managers.WhitelistManager;
import su.knst.telegram.ai.utils.UserPermission;

import java.util.Date;
import java.util.concurrent.ExecutionException;

public class DebugCommand extends AbstractCommand {
    protected Property<TelegramConfig> telegramConfig;
    protected AiMessagesManager messagesManager;
    protected static String debugKey;

    protected final static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public DebugCommand(ConfigWorker configWorker, AiMessagesManager messagesManager) {
        this.telegramConfig = configWorker.telegram;
        this.messagesManager = messagesManager;
    }

    @Override
    public String name() {
        return "debug";
    }

    @Override
    public String description() {
        return "";
    }

    @Override
    public String commandArgsTips() {
        return "";
    }

    @Override
    public boolean hidden() {
        return true;
    }

    @Override
    public void run(String[] strings, NewMessageEvent newMessageEvent) {
        long superAdminId = telegramConfig.get().superAdminId;
        Message toDebug = newMessageEvent.data.replyToMessage();

        chatHandler.deleteMessage(newMessageEvent.data.messageId());

        if (newMessageEvent.chatId == superAdminId && strings.length > 0) {
            debugKey = strings[0];
            sendMessage("Debug key updated", superAdminId);

            return;
        }

        if (newMessageEvent.chatId != superAdminId) {
            if (strings.length == 0)
                return;

            if (!strings[0].equals(debugKey))
                return;
        }

        if (toDebug == null)
            return;

        var message = messagesManager.getMessage(toDebug.chat().id(), toDebug.messageId());

        if (message.isEmpty()) {
            sendMessage("toDebug is empty!", superAdminId);
            return;
        }

        var messages = messagesManager.getMessages(message.get().getAiContext());

        String json = gson.toJson(messages.stream().map(DatabaseEntryWrapper::new).toList());

        sendFile("[" + new Date(System.currentTimeMillis()) + "] debug chat #" + toDebug.chat().id(), json.getBytes(), superAdminId);
    }

    protected void sendMessage(String text, long chatId) {
        chatHandler.getCore().execute(new SendMessage(chatId, text));
    }

    protected void sendFile(String caption, byte[] content, long chatId) {
        chatHandler.getCore().execute(new SendDocument(chatId, content).caption(caption).fileName("debug.json"));
    }

    static class DatabaseEntryWrapper {
        final long id;
        final long messageId;
        final long chatId;
        final long aiContext;
        final String role;
        final String content;

        public DatabaseEntryWrapper(AiMessagesRecord record) {
            this.id = record.getId();
            this.messageId = record.getMessageId();
            this.chatId = record.getChatId();
            this.aiContext = record.getAiContext();
            this.role = record.getRole();
            this.content = record.getContent().data();
        }
    }
}
