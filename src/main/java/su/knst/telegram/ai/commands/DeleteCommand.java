package su.knst.telegram.ai.commands;

import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.handlers.command.AbstractCommand;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.ChatMember;
import com.pengrad.telegrambot.request.GetChatMember;
import com.pengrad.telegrambot.response.GetChatMemberResponse;
import su.knst.telegram.ai.handlers.ChatHandler;
import su.knst.telegram.ai.scenes.main.MainScene;

import java.util.concurrent.ExecutionException;

public class DeleteCommand extends AbstractCommand {
    @Override
    public String name() {
        return "delete";
    }

    @Override
    public String description() {
        return "Deletes the specified context (reply to the message) or the last one";
    }

    @Override
    public String commandArgsTips() {
        return "";
    }

    @Override
    public void run(String[] strings, NewMessageEvent newMessageEvent) {
        if (newMessageEvent.data.chat().type() != Chat.Type.Private) {
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

        chatHandler.deleteMessage(newMessageEvent.data.messageId());

        MainScene mainScene = ((ChatHandler) chatHandler).getMainScene();

        if (newMessageEvent.data.replyToMessage() != null)
            mainScene.deleteContext(newMessageEvent.data.replyToMessage().messageId());
        else
            mainScene.deleteContext();
    }
}
