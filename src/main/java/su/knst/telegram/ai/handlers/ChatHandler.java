package su.knst.telegram.ai.handlers;

import app.finwave.tat.BotCore;
import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.handlers.command.AbstractCommand;
import app.finwave.tat.handlers.scened.ScenedAbstractChatHandler;
import app.finwave.tat.utils.MessageBuilder;
import su.knst.telegram.ai.commands.*;
import su.knst.telegram.ai.config.AiConfig;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.jooq.tables.records.AiModelsRecord;
import su.knst.telegram.ai.jooq.tables.records.AiPresetsRecord;
import su.knst.telegram.ai.managers.ChatPreferencesManager;
import su.knst.telegram.ai.managers.WhitelistManager;
import su.knst.telegram.ai.scenes.admin.AdminScene;
import su.knst.telegram.ai.scenes.main.MainScene;
import su.knst.telegram.ai.scenes.settings.SettingsScene;
import su.knst.telegram.ai.utils.UserPermission;
import su.knst.telegram.ai.workers.AiWorker;
import su.knst.telegram.ai.workers.BotWorker;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class ChatHandler extends ScenedAbstractChatHandler {
    protected WhitelistManager whitelistManager;
    protected AiWorker aiWorker;

    protected MainScene mainScene;
    protected boolean presetsInit = false;

    public ChatHandler(BotCore core, long chatId, ConfigWorker configWorker, AiWorker aiWorker, ChatPreferencesManager preferencesManager, WhitelistManager whitelistManager, BotWorker botWorker) {
        super(core, chatId);

        this.whitelistManager = whitelistManager;
        this.aiWorker = aiWorker;

        mainScene = new MainScene(this, aiWorker, preferencesManager);

        registerScene("main", mainScene);
        registerScene("settings", new SettingsScene(this, configWorker, aiWorker, preferencesManager));
        registerScene("admin", new AdminScene(this, aiWorker, whitelistManager, botWorker, configWorker));

        registerCommand(new AdminCommand(whitelistManager));
        registerCommand(new StartCommand(configWorker.telegram.startMessage));
        registerCommand(new SettingsCommand(whitelistManager));
        registerCommand(new NewContextCommand());
        registerCommand(new DeleteCommand(whitelistManager));
        registerCommand(new AddPresetCommand(configWorker, aiWorker, whitelistManager));

        eventHandler.setValidator((e) -> {
            if (whitelistManager.getPermission(chatId) == UserPermission.UNAUTHORIZED) {
                sendMessage(MessageBuilder.text("UNAUTHORIZED: " + chatId));

                return false;
            }

            if (!presetsInit && aiWorker.getPresetsManager().getPresets(chatId).isEmpty()) {
                AiConfig.Preset defaultPreset = configWorker.ai.defaultUserPreset;
                AiModelsRecord modelsRecord = aiWorker.getModelsManager().getModels().get(0);

                Optional<AiPresetsRecord> preset = aiWorker.getPresetsManager().addPreset(chatId, modelsRecord.getId(), defaultPreset.temperature, defaultPreset.topP, defaultPreset.frequencyPenalty, defaultPreset.presencePenalty, defaultPreset.maxTokens, defaultPreset.prompt, "default");

                preferencesManager.initPreferences(chatId, preset.orElseThrow().getId());
            }

            presetsInit = true;

            return true;
        });

        eventHandler.registerListener(NewMessageEvent.class, (e) -> {
            startScene("main", e);
        });
    }

    public MainScene getMainScene() {
        return mainScene;
    }

    @Override
    public void start() {

    }
}
