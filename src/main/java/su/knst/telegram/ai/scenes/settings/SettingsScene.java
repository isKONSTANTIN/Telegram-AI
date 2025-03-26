package su.knst.telegram.ai.scenes.settings;

import app.finwave.rct.reactive.property.Property;
import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.handlers.scened.ScenedAbstractChatHandler;
import app.finwave.tat.menu.FlexListButtonsLayout;
import app.finwave.tat.menu.MessageMenu;
import app.finwave.tat.scene.BaseScene;
import app.finwave.tat.utils.MessageBuilder;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.handlers.ChatHandler;
import su.knst.telegram.ai.jooq.tables.records.ChatsPreferencesRecord;
import su.knst.telegram.ai.managers.AiContextManager;
import su.knst.telegram.ai.managers.ChatPreferencesManager;
import su.knst.telegram.ai.managers.AiModelsManager;
import su.knst.telegram.ai.utils.ContextMode;
import su.knst.telegram.ai.utils.MentionMode;
import su.knst.telegram.ai.workers.AiWorker;
import su.knst.telegram.ai.workers.lang.LangWorker;

import java.util.concurrent.ExecutionException;

public class SettingsScene extends BaseScene<NewMessageEvent> {
    protected MessageMenu<FlexListButtonsLayout> mainMenu;
    protected PresetsMenu presetsMenu;

    protected AiWorker aiWorker;
    protected ChatPreferencesManager preferencesManager;
    protected ConfigWorker configWorker;

    protected NewMessageEvent sourceEvent;

    protected Property<LangWorker> langWorker;

    public SettingsScene(ChatHandler chatHandler, ConfigWorker configWorker, AiWorker aiWorker, ChatPreferencesManager preferencesManager) {
        super(chatHandler);

        this.langWorker = chatHandler.getLang();
        this.aiWorker = aiWorker;
        this.preferencesManager = preferencesManager;
        this.configWorker = configWorker;

        mainMenu = new MessageMenu<>(this, new FlexListButtonsLayout(6));

        presetsMenu = new PresetsMenu(this, (e) -> updateMain(false), aiWorker, preferencesManager, configWorker);
    }

    protected void updateMain(boolean newMessage) {
        var lang = langWorker.get();

        mainMenu.setUseLastMessage(!newMessage);
        mainMenu.getLayout().removeAll();

        ChatsPreferencesRecord preferencesRecord = preferencesManager.getPreferences(chatId).orElseThrow();
        ContextMode contextMode = ContextMode.values()[preferencesRecord.getContextsMode()];
        MentionMode mentionMode = MentionMode.values()[preferencesRecord.getMentionMode()];

        MessageBuilder builder = MessageBuilder.create();

        builder.bold().line("\uD83D\uDEE0 %s ⚙\uFE0F".formatted(lang.get("scenes.settings.title", "Settings Menu"))).bold().gap();

        builder.line(lang.get("scenes.settings.description", """
                Here, you can optimize how you interact with your AI Assistant. Let's look at what you can configure:

                🔄 Context Mode Switching:
                - Single Mode: Perfect for continuous conversations. The bot keeps track of the context without needing you to reply specifically to each message.
                - Multi Mode: Ideal when you want to maintain clarity in context. Reply directly to messages to ensure the AI understands exactly what you're referring to.

                🎨 Preset Configuration:
                Dive into your preset settings to fine-tune your AI interactions. Customize prompt style, response behavior, and more for a truly personalized experience.
                """
        )).gap();

        if (sourceEvent.data.chat().type() != Chat.Type.Private) {
            String mentionString;

            if (mentionMode == MentionMode.WITH_MENTION) {
                mentionString = lang.get("scenes.settings.mentionMode.with", "Mention Mode: With mention of a bot");
            }else {
                mentionString = lang.get("scenes.settings.mentionMode.without", "Mention Mode: Without mention of a bot");
            }

            builder.line(mentionString);

            mainMenu.getLayout().addButton(new InlineKeyboardButton(lang.get("scenes.settings.buttons.switchMention", "Switch Mention Mode")), 3, (e) -> {
                preferencesManager.setMentionMode(chatId, mentionMode == MentionMode.WITH_MENTION ? MentionMode.WITHOUT_MENTION : MentionMode.WITH_MENTION);

                updateMain(false);
            });
        }

        String contextString;

        if (contextMode == ContextMode.MULTI_REPLY) {
            contextString = lang.get("scenes.settings.contextMode.multi", "Context Mode: Multi");
        }else {
            contextString = lang.get("scenes.settings.contextMode.single", "Context Mode: Single");
        }

        builder.line(contextString);

        mainMenu.setMessage(builder.build());

        mainMenu.getLayout().addButton(new InlineKeyboardButton(lang.get("scenes.settings.buttons.switchContext", "Switch Context Mode")), 3, (e) -> {
            preferencesManager.setContextMode(chatId, contextMode == ContextMode.MULTI_REPLY ? ContextMode.SINGLE : ContextMode.MULTI_REPLY);

            updateMain(false);
        });

        mainMenu.getLayout().addButton(new InlineKeyboardButton(lang.get("scenes.settings.buttons.presets", "Presets")), 6, (e) -> {
            presetsMenu.apply();
        });

        mainMenu.getLayout().addButton(new InlineKeyboardButton(lang.get("scenes.settings.buttons.back", "Back")), 6, (e) -> {
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
    public void start(NewMessageEvent sourceEvent) {
        super.start(sourceEvent);
        this.sourceEvent = sourceEvent;

        eventHandler.setValidator((e) -> e.userId == sourceEvent.userId); // lock scene only for one user, who asked settings menu

        updateMain(true);
    }

    @Override
    public void stop() {
        super.stop();
    }
}
