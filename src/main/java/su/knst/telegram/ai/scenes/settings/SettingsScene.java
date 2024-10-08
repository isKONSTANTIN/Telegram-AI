package su.knst.telegram.ai.scenes.settings;

import app.finwave.tat.handlers.scened.ScenedAbstractChatHandler;
import app.finwave.tat.menu.FlexListButtonsLayout;
import app.finwave.tat.menu.MessageMenu;
import app.finwave.tat.scene.BaseScene;
import app.finwave.tat.utils.MessageBuilder;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.jooq.tables.records.ChatsPreferencesRecord;
import su.knst.telegram.ai.managers.AiContextManager;
import su.knst.telegram.ai.managers.ChatPreferencesManager;
import su.knst.telegram.ai.managers.AiModelsManager;
import su.knst.telegram.ai.utils.ContextMode;
import su.knst.telegram.ai.workers.AiWorker;

import java.util.concurrent.ExecutionException;

public class SettingsScene extends BaseScene<Object> {
    protected MessageMenu<FlexListButtonsLayout> mainMenu;
    protected PresetsMenu presetsMenu;

    protected AiWorker aiWorker;
    protected ChatPreferencesManager preferencesManager;
    protected ConfigWorker configWorker;

    public SettingsScene(ScenedAbstractChatHandler chatHandler, ConfigWorker configWorker, AiWorker aiWorker, ChatPreferencesManager preferencesManager) {
        super(chatHandler);

        this.aiWorker = aiWorker;
        this.preferencesManager = preferencesManager;
        this.configWorker = configWorker;

        mainMenu = new MessageMenu<>(this, new FlexListButtonsLayout(6));

        presetsMenu = new PresetsMenu(this, (e) -> updateMain(false), aiWorker, preferencesManager, configWorker);
    }

    protected void updateMain(boolean newMessage) {
        mainMenu.setUseLastMessage(!newMessage);
        mainMenu.getLayout().removeAll();

        ChatsPreferencesRecord preferencesRecord = preferencesManager.getPreferences(chatId).orElseThrow();
        ContextMode contextMode = ContextMode.values()[preferencesRecord.getContextsMode()];

        MessageBuilder builder = MessageBuilder.create();

        builder.bold().line("\uD83D\uDEE0 Settings Menu ⚙\uFE0F").bold().gap();

        builder.line("""
                Here, you can optimize how you interact with your AI Assistant. Let's look at what you can configure:

                🔄 Context Mode Switching:
                - Single Mode: Perfect for continuous conversations. The bot keeps track of the context without needing you to reply specifically to each message.
                - Multi Mode: Ideal when you want to maintain clarity in context. Reply directly to messages to ensure the AI understands exactly what you're referring to.

                🎨 Preset Configuration:
                Dive into your preset settings to fine-tune your AI interactions. Customize prompt style, response behavior, and more for a truly personalized experience.
                """).gap();

        builder.line("Context Mode: " + (contextMode == ContextMode.MULTI_REPLY ? "Multi" : "Single"));

        mainMenu.setMessage(builder.build());

        mainMenu.getLayout().addButton(new InlineKeyboardButton("Switch Context Mode"), 6, (e) -> {
            preferencesManager.setContextMode(chatId, contextMode == ContextMode.MULTI_REPLY ? ContextMode.SINGLE : ContextMode.MULTI_REPLY);

            updateMain(false);
        });

        mainMenu.getLayout().addButton(new InlineKeyboardButton("Presets"), 6, (e) -> {
            presetsMenu.apply();
        });

        mainMenu.getLayout().addButton(new InlineKeyboardButton("Back"), 6, (e) -> {
            try {
                mainMenu.setUseLastMessage(true);
                mainMenu.delete().get();
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }
            chatHandler.stopActiveScene();
            chatHandler.startScene("main");
        });

        mainMenu.apply();
    }

    @Override
    public void start() {
        super.start();

        updateMain(true);
    }

    @Override
    public void stop() {
        super.stop();
    }
}
