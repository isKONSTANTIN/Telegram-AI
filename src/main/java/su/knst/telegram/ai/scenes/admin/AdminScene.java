package su.knst.telegram.ai.scenes.admin;

import app.finwave.tat.event.chat.CallbackQueryEvent;
import app.finwave.tat.event.handler.EventListener;
import app.finwave.tat.handlers.scened.ScenedAbstractChatHandler;
import app.finwave.tat.menu.FlexListButtonsLayout;
import app.finwave.tat.menu.MessageMenu;
import app.finwave.tat.scene.BaseScene;
import app.finwave.tat.utils.MessageBuilder;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.managers.AiModelsManager;
import su.knst.telegram.ai.managers.WhitelistManager;
import su.knst.telegram.ai.utils.UserPermission;
import su.knst.telegram.ai.workers.AiWorker;
import su.knst.telegram.ai.workers.BotWorker;

import java.util.concurrent.ExecutionException;

public class AdminScene extends BaseScene<Object> {
    protected WhitelistManager whitelistManager;
    protected AiModelsManager modelsManager;

    protected MessageMenu<FlexListButtonsLayout> menu;
    protected WhitelistMenu whitelistMenu;
    protected ModelsUsageMenu modelsUsageMenu;
    protected ModelsMenu modelsMenu;

    public AdminScene(ScenedAbstractChatHandler chatHandler, AiWorker aiWorker, WhitelistManager whitelistManager, BotWorker botWorker, ConfigWorker configWorker) {
        super(chatHandler);
        this.whitelistManager = whitelistManager;
        this.modelsManager = aiWorker.getModelsManager();

        eventHandler.setValidator((e) -> {
            UserPermission permission = whitelistManager.getPermission(chatId);

            if (permission != UserPermission.ADMIN && permission != UserPermission.SUPER_ADMIN) {
                chatHandler.stopActiveScene();
                chatHandler.startScene("main");

                return false;
            }

            return true;
        });

        EventListener<CallbackQueryEvent> backListener = (e) -> {
            menu.setUseLastMessage(true);
            menu.apply();
        };

        whitelistMenu = new WhitelistMenu(this, backListener, whitelistManager);
        modelsUsageMenu = new ModelsUsageMenu(this, backListener, modelsManager, whitelistManager);
        modelsMenu = new ModelsMenu(this, backListener, aiWorker, configWorker);

        menu = new MessageMenu<>(this, new FlexListButtonsLayout(2));
        menu.setMessage(MessageBuilder.text("Admin menu"));

        menu.getLayout().addButton(new InlineKeyboardButton("Whitelist"), (e) -> {
            whitelistMenu.apply();
        });

        menu.getLayout().addButton(new InlineKeyboardButton("Models"), (e) -> {
            modelsMenu.apply();
        });

        menu.getLayout().addButton(new InlineKeyboardButton("Models Usage"), (e) -> {
            modelsUsageMenu.apply();
        });

        menu.getLayout().addButton(new InlineKeyboardButton("Back"), 2, (e) -> {
            try {
                menu.setUseLastMessage(true);
                menu.delete().get();
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }
            chatHandler.stopActiveScene();
            chatHandler.startScene("main");
        });
    }

    @Override
    public void start() {
        super.start();

        menu.setUseLastMessage(false);
        menu.apply();
    }

    @Override
    public void stop() {
        super.stop();
    }
}
