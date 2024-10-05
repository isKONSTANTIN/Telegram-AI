package su.knst.telegram.ai;

import app.finwave.tat.BotCore;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.logging.LogsInitializer;
import su.knst.telegram.ai.workers.BotWorker;

import java.io.IOException;

public class Main {
    protected static Injector INJ;
    protected static Logger log;

    protected static String botToken;

    public static void main(String[] args) throws IOException {
        ConfigWorker configWorker = new ConfigWorker();
        botToken = configWorker.telegram.apiToken;

        BotCore core = new BotCore(botToken);

        INJ = Guice.createInjector(binder -> {
            binder.bind(BotCore.class).toInstance(core);
            binder.bind(ConfigWorker.class).toInstance(configWorker);
        });

        LogsInitializer.init();
        log = LoggerFactory.getLogger(Main.class);

        INJ.getInstance(BotWorker.class).init();

        log.info("Bot started");
    }

    public static String getBotToken() {
        return botToken;
    }

    public static Injector getINJ() {
        return INJ;
    }
}