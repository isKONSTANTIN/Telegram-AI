package su.knst.telegram.ai.commands;

import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.handlers.command.AbstractCommand;
import app.finwave.tat.handlers.scened.ScenedAbstractChatHandler;
import app.finwave.tat.utils.MessageBuilder;
import su.knst.telegram.ai.handlers.ChatHandler;
import su.knst.telegram.ai.workers.lang.LangWorker;

public class NewContextCommand extends LanguageAbstractCommand {
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
    void run(LangWorker lang, String[] strings, NewMessageEvent newMessageEvent) {
        chatHandler.deleteMessage(newMessageEvent.data.messageId());

        ((ChatHandler) chatHandler).getMainScene().resetLastContext();

        chatHandler.sendMessage(MessageBuilder.text(lang.get("commands.newContext.success", "New context created")));
    }
}
