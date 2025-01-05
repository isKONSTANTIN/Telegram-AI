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

import java.util.concurrent.ExecutionException;

public class StartCommand extends AbstractCommand {
    protected Value<String> startMessage;

    public StartCommand(ConfigWorker configWorker) {
        this.startMessage = configWorker == null ? Value.wrap("") :configWorker.telegram.map((c) -> c.startMessage);
    }

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
    public void run(String[] args, NewMessageEvent newMessageEvent) {
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

        chatHandler.sendMessage(MessageBuilder.text(startMessage.get())).whenComplete((r, t) -> {
            t.printStackTrace();
            chatHandler.deleteMessage(newMessageEvent.data.messageId());
        });

        ((ScenedAbstractChatHandler) chatHandler).startScene("main");
    }
}
