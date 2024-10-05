package su.knst.telegram.ai.commands;

import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.handlers.command.AbstractCommand;
import app.finwave.tat.handlers.scened.ScenedAbstractChatHandler;
import app.finwave.tat.utils.MessageBuilder;
import su.knst.telegram.ai.handlers.ChatHandler;

public class NewContextCommand extends AbstractCommand {
    @Override
    public String name() {
        return "new";
    }

    @Override
    public String description() {
        return "Create new context (useful for single context mode)";
    }

    @Override
    public String commandArgsTips() {
        return "";
    }

    @Override
    public void run(String[] strings, NewMessageEvent newMessageEvent) {
        chatHandler.deleteMessage(newMessageEvent.data.messageId());

        ((ChatHandler) chatHandler).getMainScene().resetLastContext();

        chatHandler.sendMessage(MessageBuilder.text("New context created"));
    }
}
