package su.knst.telegram.ai.utils.menu;

import app.finwave.tat.scene.BaseScene;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;

import java.util.function.Consumer;
import java.util.function.Function;

public class TypedAskMenu<X> extends AskMenu {
    protected Consumer<String> invalidValueConsumer;
    protected Function<X, Boolean> typedResultFunction;
    
    public TypedAskMenu(BaseScene<?> scene) {
        super(scene);
    }

    public TypedAskMenu<X> setResultFunction(Function<X, Boolean> function, Function<String, X> mapper) {
        this.typedResultFunction = function;
        
        setResultFunction((s) -> {
            X mapped = null;
            
            try {
                mapped = mapper.apply(s);
            }catch (Exception e) {
                if (invalidValueConsumer != null)
                    invalidValueConsumer.accept(s);
            }

            return function.apply(mapped);
        });

        return this;
    }

    public TypedAskMenu<X> addAnswer(InlineKeyboardButton button, X result) {
        addAnswer(button, (e) -> {
            if (typedResultFunction != null && typedResultFunction.apply(result)) {
                listenerRemover.remove();

                return;
            }

            if (typedResultFunction != null)
                return;

            if (resultFunction != null && result instanceof String stringResult && resultFunction.apply(stringResult))
                listenerRemover.remove();
        });
        
        return this;
    }

    public TypedAskMenu<X> addAnswer(String title, X result) {
        return addAnswer(new InlineKeyboardButton(title), result);
    }
}
