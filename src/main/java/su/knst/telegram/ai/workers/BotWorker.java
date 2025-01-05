package su.knst.telegram.ai.workers;

import app.finwave.rct.reactive.property.Property;
import app.finwave.tat.BotCore;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.GetMe;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.response.GetMeResponse;
import su.knst.telegram.ai.commands.*;
import su.knst.telegram.ai.config.AiConfig;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.config.TelegramConfig;
import su.knst.telegram.ai.handlers.ChatHandler;
import su.knst.telegram.ai.jooq.tables.records.AiModelsRecord;
import su.knst.telegram.ai.jooq.tables.records.AiPresetsRecord;
import su.knst.telegram.ai.managers.ChatPreferencesManager;
import su.knst.telegram.ai.managers.WhitelistManager;

import java.lang.reflect.Field;
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
    public BotWorker(ConfigWorker configWorker, AiWorker aiWorker, ChatPreferencesManager chatPreferencesManager, WhitelistManager whitelistManager) {
        this.aiWorker = aiWorker;
        this.whitelistManager = whitelistManager;
        this.chatPreferencesManager = chatPreferencesManager;
        this.configWorker = configWorker;

        botCore = new BotCore(configWorker.telegramToken.get());

        configWorker.telegramToken.addChangeListener((n) -> {
            // For some reason, we need to create a new bot first before disabling the old one, otherwise the JVM will simply shut down.
            // Perhaps this is due to the fact that the main thread ended from the very beginning,
            // and at that moment we finish off the rest of the non-demon threads.
            BotCore old = botCore;
            botCore = new BotCore(n);
            init();

            old.shutdown();
        });
    }

    protected void validateSuperAdmin(TelegramConfig telegramConfig) {
        if (telegramConfig == null)
            return;

        if (whitelistManager.inWhitelist(telegramConfig.superAdminId))
            return;

        whitelistManager.addToWhitelist(telegramConfig.superAdminId);
    }

    public void init() {
        if (aiWorker.getModelsManager().getModels().isEmpty()) {
            AiConfig.Model defaultModel = configWorker.ai.get().defaultModel;

            aiWorker.getModelsManager().addModel((short) 0, defaultModel.name, defaultModel.model);
        }

        validateSuperAdmin(configWorker.telegram.get());
        configWorker.telegram.addChangeListener((n) -> validateSuperAdmin(configWorker.telegram.get()));

        BotCommand[] commands = Stream.of(
                        new DeleteCommand(null),
                        new NewContextCommand(),
                        new SettingsCommand(null),
                        new StartCommand(null),
                        new AddPresetCommand(null, null, null))
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
