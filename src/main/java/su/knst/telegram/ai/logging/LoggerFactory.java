package su.knst.telegram.ai.logging;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import su.knst.telegram.ai.Main;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.config.LoggingConfig;

public class LoggerFactory implements ILoggerFactory {
    protected static LoggingConfig config;

    static {
        config = Main.getINJ().getInstance(ConfigWorker.class).loggingConfig;
    }

    @Override
    public Logger getLogger(String name) {
        if (!config.logFullClassName) {
            String[] split = name.split("\\.");
            name = split[split.length - 1];
        }

        return new su.knst.telegram.ai.logging.Logger(name);
    }
}
