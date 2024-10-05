package su.knst.telegram.ai.logging;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class LogsInitializer {
    public static final Path LOGS_DIRECTORY = Path.of("./logs/");
    public static final Path LAST_LOG_FILE = LOGS_DIRECTORY.resolve("last.log");
    public static final Path HISTORY_DIRECTORY = LOGS_DIRECTORY.resolve("history/");

    protected static SystemStreamsHandler handler;

    public static void init() throws IOException {
        if (Files.notExists(LOGS_DIRECTORY))
            Files.createDirectory(LOGS_DIRECTORY);

        if (Files.notExists(HISTORY_DIRECTORY))
            Files.createDirectory(HISTORY_DIRECTORY);

        if (Files.exists(LAST_LOG_FILE)) {
            BasicFileAttributes attributes = Files.readAttributes(LAST_LOG_FILE, BasicFileAttributes.class);
            Date creationDate = new Date(attributes.creationTime().to(TimeUnit.MILLISECONDS));
            String historyFilename = new SimpleDateFormat("dd-MM-yy_HH-mm-ss").format(creationDate) + ".log";

            Files.move(LAST_LOG_FILE, HISTORY_DIRECTORY.resolve(historyFilename));
            Files.createFile(LAST_LOG_FILE);
        }

        handler = new SystemStreamsHandler(new FileOutputStream(LAST_LOG_FILE.toFile()));
        handler.replaceStreams();
    }
}
