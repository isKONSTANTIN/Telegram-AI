package su.knst.telegram.ai.commands;

import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.handlers.command.AbstractCommand;
import app.finwave.tat.handlers.scened.ScenedAbstractChatHandler;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.ChatMember;
import com.pengrad.telegrambot.request.GetChatMember;
import com.pengrad.telegrambot.response.GetChatMemberResponse;
import su.knst.telegram.ai.managers.WhitelistManager;
import su.knst.telegram.ai.utils.UserPermission;

import java.util.concurrent.ExecutionException;

public class SettingsCommand extends AbstractCommand {
    protected WhitelistManager whitelistManager;

    public SettingsCommand(WhitelistManager whitelistManager) {
        this.whitelistManager = whitelistManager;
    }

    @Override
    public String name() {
        return "settings";
    }

    @Override
    public String description() {
        return "Opens the settings menu";
    }

    @Override
    public String commandArgsTips() {
        return "";
    }

    @Override
    public void run(String[] strings, NewMessageEvent newMessageEvent) {
        if (newMessageEvent.data.chat().type() != Chat.Type.Private) {
            UserPermission permission = whitelistManager.getPermission(newMessageEvent.data.from().id());

            if (permission != UserPermission.SUPER_ADMIN && permission != UserPermission.ADMIN) {
                ChatMember.Status status;

                try {
                    status = chatHandler.getCore()
                            .execute(new GetChatMember(newMessageEvent.data.chat().id(), newMessageEvent.data.from().id()))
                            .thenApply(GetChatMemberResponse::chatMember)
                            .thenApply(ChatMember::status)
                            .get();
                } catch (InterruptedException | ExecutionException e) {
                    return;
                }

                if (status != ChatMember.Status.creator && status != ChatMember.Status.administrator)
                    return;
            }
        }

        chatHandler.deleteMessage(newMessageEvent.data.messageId());

        ((ScenedAbstractChatHandler) chatHandler).startScene("settings", newMessageEvent.userId);
    }
}
