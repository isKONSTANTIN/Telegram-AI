package su.knst.telegram.ai.workers;

import app.finwave.tat.BotCore;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.GetMe;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.response.GetMeResponse;
import su.knst.telegram.ai.commands.*;
import su.knst.telegram.ai.config.AiConfig;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.handlers.ChatHandler;
import su.knst.telegram.ai.jooq.tables.records.AiModelsRecord;
import su.knst.telegram.ai.jooq.tables.records.AiPresetsRecord;
import su.knst.telegram.ai.managers.ChatPreferencesManager;
import su.knst.telegram.ai.managers.WhitelistManager;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

@Singleton
public class BotWorker {
    protected BotCore botCore;
    protected AiWorker aiWorker;
    protected ChatPreferencesManager chatPreferencesManager;
    protected ConfigWorker configWorker;

    protected WhitelistManager whitelistManager;

    @Inject
    public BotWorker(BotCore botCore, ConfigWorker configWorker, AiWorker aiWorker, ChatPreferencesManager chatPreferencesManager, WhitelistManager whitelistManager) {
        this.botCore = botCore;
        this.aiWorker = aiWorker;
        this.whitelistManager = whitelistManager;
        this.chatPreferencesManager = chatPreferencesManager;
        this.configWorker = configWorker;
    }

    public void init() {
        if (aiWorker.getModelsManager().getModels().isEmpty()) {
            AiConfig.Model defaultModel = configWorker.ai.defaultModel;

            aiWorker.getModelsManager().addModel((short) 0, defaultModel.name, defaultModel.model);
        }

        if (!whitelistManager.inWhitelist(configWorker.telegram.superAdminId)) {
            whitelistManager.addToWhitelist(configWorker.telegram.superAdminId);
        }

        BotCommand[] commands = Stream.of(
                        new DeleteCommand(),
                        new NewContextCommand(),
                        new SettingsCommand(),
                        new StartCommand(null),
                        new AddPresetCommand(null, null))
                .filter((c) -> !c.hidden())
                .filter((c) -> !c.description().isBlank())
                .map((c) -> new BotCommand(c.name(), (c.commandArgsTips() != null ? c.commandArgsTips() + " " : "") + c.description()))
                .toArray(BotCommand[]::new);

        botCore.execute(new SetMyCommands(commands)).whenComplete((r, t) -> {
            if (t != null)
                t.printStackTrace();
        });

        botCore.getUpdatesProcessor().setChatHandlerGenerator(this::chatHandler);
    }

    protected ChatHandler chatHandler(long chatId) {
        return new ChatHandler(botCore, chatId, configWorker, aiWorker, chatPreferencesManager, whitelistManager, this);
    }
}
