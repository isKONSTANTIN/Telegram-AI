package su.knst.telegram.ai.commands;

import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.handlers.command.AbstractCommand;
import su.knst.telegram.ai.workers.lang.LangWorker;

import static su.knst.telegram.ai.workers.lang.LangManager.lang;

public abstract class LanguageAbstractCommand extends AbstractCommand {

    @Override
    public void run(String[] strings, NewMessageEvent newMessageEvent) {
        String code = newMessageEvent.data.from().languageCode();

        run(lang(code), strings, newMessageEvent);
    }

    abstract void run(LangWorker lang, String[] strings, NewMessageEvent newMessageEvent);
}
