package su.knst.telegram.ai.config;

import app.finwave.scw.RootConfig;

import java.io.File;
import java.io.IOException;

public class ConfigWorker {
    public final DatabaseConfig database;
    public final TelegramConfig telegram;
    public final LoggingConfig loggingConfig;
    public final AiConfig ai;

    protected final RootConfig main;

    public ConfigWorker() throws IOException {
        main = new RootConfig(new File("configs/main.conf"), true);
        main.load();

        database = main.subNode("database").getOrSetAs(DatabaseConfig.class, DatabaseConfig::new);
        telegram = main.subNode("telegram").getOrSetAs(TelegramConfig.class, TelegramConfig::new);
        loggingConfig = main.subNode("logging").getOrSetAs(LoggingConfig.class, LoggingConfig::new);
        ai = main.subNode("ai").getOrSetAs(AiConfig.class, AiConfig::new);
    }
}
