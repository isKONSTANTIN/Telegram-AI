package su.knst.telegram.ai.utils.menu;

import app.finwave.tat.event.chat.CallbackQueryEvent;
import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.event.handler.EventListener;
import app.finwave.tat.event.handler.HandlerRemover;
import app.finwave.tat.menu.FlexListButtonsLayout;
import app.finwave.tat.menu.MessageMenu;
import app.finwave.tat.scene.BaseScene;
import app.finwave.tat.utils.MessageBuilder;
import app.finwave.tat.utils.Pair;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.response.BaseResponse;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class AskMenu extends MessageMenu<FlexListButtonsLayout> {
    protected Function<String, Boolean> resultFunction;
    protected HandlerRemover listenerRemover;

    protected ArrayList<Pair<InlineKeyboardButton, EventListener<CallbackQueryEvent>>> buttons = new ArrayList<>();

    public AskMenu(BaseScene<?> scene) {
        super(scene, new FlexListButtonsLayout(1));
    }

    @Override
    public CompletableFuture<? extends BaseResponse> apply() {
        layout.removeAll();

        for (var button : buttons) {
            layout.addButton(button.first(), (e) -> {
                listenerRemover.remove();
                button.second().event(e);
            });
        }

        layout.addButton(new InlineKeyboardButton("Cancel"), (e) -> {
            if (resultFunction == null) {
                listenerRemover.remove();

                return;
            }

            if (resultFunction.apply(null))
                listenerRemover.remove();
        });

        if (listenerRemover != null)
            listenerRemover.remove();

        this.listenerRemover = scene.getEventHandler()
                .registerListener(NewMessageEvent.class, this::messageListener);

        return super.apply();
    }

    protected void messageListener(NewMessageEvent e) {
        scene.getChatHandler().deleteMessage(e.data.messageId());

        if (e.data.text() == null)
            return;

        if (resultFunction == null) {
            listenerRemover.remove();

            return;
        }

        if (resultFunction.apply(e.data.text()))
            listenerRemover.remove();
    }

    public AskMenu setText(String title, String description) {
        setMessage(MessageBuilder.create()
                .bold().line(title).bold()
                .gap()
                .line(description)
                .build()
        );

        return this;
    }

    public AskMenu setResultFunction(Function<String, Boolean> function) {
        this.resultFunction = function;

        return this;
    }

    public AskMenu addAnswer(InlineKeyboardButton button, EventListener<CallbackQueryEvent> listener) {
        buttons.add(new Pair<>(button, listener));

        return this;
    }

    public AskMenu addAnswer(InlineKeyboardButton button, String result) {
        return addAnswer(button, (e) -> {
            if (resultFunction == null) {
                listenerRemover.remove();

                return;
            }

            if (resultFunction.apply(result))
                listenerRemover.remove();
        });
    }

    public AskMenu addAnswer(String title, String result) {
        return addAnswer(new InlineKeyboardButton(title), result);
    }

    public void removeButtons() {
        buttons.clear();
    }

    @Override
    public CompletableFuture<BaseResponse> delete() {
        listenerRemover.remove();

        return super.delete();
    }
}
