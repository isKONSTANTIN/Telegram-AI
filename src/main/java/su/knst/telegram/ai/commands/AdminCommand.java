package su.knst.telegram.ai.commands;

import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.handlers.command.AbstractCommand;
import app.finwave.tat.handlers.scened.ScenedAbstractChatHandler;
import app.finwave.tat.utils.MessageBuilder;
import com.pengrad.telegrambot.model.Chat;
import su.knst.telegram.ai.managers.WhitelistManager;
import su.knst.telegram.ai.utils.UserPermission;
import su.knst.telegram.ai.workers.lang.LangWorker;

public class AdminCommand extends LanguageAbstractCommand {
    protected WhitelistManager whitelistManager;

    public AdminCommand(WhitelistManager whitelistManager) {
        this.whitelistManager = whitelistManager;
    }

    @Override
    public String name() {
        return "admin";
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
    void run(LangWorker lang, String[] strings, NewMessageEvent newMessageEvent) {
        if (newMessageEvent.data.chat().type() != Chat.Type.Private) {
            chatHandler.sendMessage(MessageBuilder.text(lang.get("commands.admin.onlyInPrivate", "Available only in private chats")));

            return;
        }

        UserPermission permission = whitelistManager.getPermission(newMessageEvent.data.from().id());

        if (permission != UserPermission.SUPER_ADMIN && permission != UserPermission.ADMIN)
            return;

        chatHandler.deleteMessage(newMessageEvent.data.messageId());

        ((ScenedAbstractChatHandler) chatHandler).startScene("admin");
    }
}
