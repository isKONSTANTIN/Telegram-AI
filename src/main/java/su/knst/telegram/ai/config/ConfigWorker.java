package su.knst.telegram.ai.config;

import app.finwave.rct.config.ConfigManager;
import app.finwave.rct.config.ConfigNode;
import app.finwave.rct.reactive.property.Property;

import java.io.File;
import java.io.IOException;

public class ConfigWorker {
    public final Property<TelegramConfig> telegram;
    public final Property<String> telegramToken;

    public final Property<DatabaseConfig> database;
    public final Property<LoggingConfig> loggingConfig;
    public final Property<AiConfig> ai;

    protected final ConfigNode main;

    protected ConfigManager manager;

    public ConfigWorker() throws IOException {
        manager = new ConfigManager();

        main = manager.load(new File("configs/main.conf"));

        database = main.node("database").getAs(DatabaseConfig.class);
        telegram = main.node("telegram").getAs(TelegramConfig.class);
        telegramToken = main.node("telegram").getAsString("apiToken");

        loggingConfig = main.node("logging").getAs(LoggingConfig.class);
        ai = main.node("ai").getAs(AiConfig.class);

        // Not only set default value (via getOr()), but write new class fields if they not exists
        database.set(database.getOr(new DatabaseConfig()));
        telegram.set(telegram.getOr(new TelegramConfig()));
        loggingConfig.set(loggingConfig.getOr(new LoggingConfig()));
        ai.set(ai.getOr(new AiConfig()));
    }
}
