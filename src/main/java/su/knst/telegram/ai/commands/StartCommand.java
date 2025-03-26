package su.knst.telegram.ai.commands;

import app.finwave.rct.reactive.property.Property;
import app.finwave.rct.reactive.value.Value;
import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.handlers.command.AbstractCommand;
import app.finwave.tat.handlers.scened.ScenedAbstractChatHandler;
import app.finwave.tat.utils.MessageBuilder;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.ChatMember;
import com.pengrad.telegrambot.request.GetChatMember;
import com.pengrad.telegrambot.response.GetChatMemberResponse;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.workers.lang.LangWorker;

import java.util.concurrent.ExecutionException;

public class StartCommand extends LanguageAbstractCommand {
    @Override
    public String name() {
        return "start";
    }

    @Override
    public String description() {
        return "Start bot";
    }

    @Override
    public String commandArgsTips() {
        return "";
    }

    @Override
    void run(LangWorker lang, String[] strings, NewMessageEvent newMessageEvent) {
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

        chatHandler.sendMessage(MessageBuilder.text(lang.get("commands.start.message", "Hello!"))).whenComplete((r, t) -> {
            t.printStackTrace();
            chatHandler.deleteMessage(newMessageEvent.data.messageId());
        });

        ((ScenedAbstractChatHandler) chatHandler).startScene("main");
    }
}
