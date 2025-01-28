package su.knst.telegram.ai.utils.functions;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileDownloader {
    protected static ExecutorService executor = Executors.newCachedThreadPool();

    public static CompletableFuture<File> downloadFile(String url) {
        CompletableFuture<File> future = new CompletableFuture<>();

        executor.execute(() -> {
            try {
                File tempFile = File.createTempFile("knst_ai_download_", ".tmp");

                try (BufferedInputStream in = new BufferedInputStream(new URL(url).openStream()); FileOutputStream fileOutputStream = new FileOutputStream(tempFile)) {
                    byte[] dataBuffer = new byte[1024];
                    int bytesRead;

                    while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1)
                        fileOutputStream.write(dataBuffer, 0, bytesRead);
                }

                future.complete(tempFile);
            }catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public static CompletableFuture<String> downloadFileAsString(String url) {
        return downloadFile(url).thenApply((f) -> {
            try {
                return Files.readString(f.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }finally {
                try {
                    Files.deleteIfExists(f.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static boolean isReadable(String input) {
        if (input == null)
            return false;

        for (char c : input.toCharArray())
            if (Character.isISOControl(c) && !Character.isWhitespace(c))
                return false;

        return true;
    }
}
