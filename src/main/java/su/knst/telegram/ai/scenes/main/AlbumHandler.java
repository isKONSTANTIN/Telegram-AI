package su.knst.telegram.ai.scenes.main;

import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.utils.Pair;
import com.pengrad.telegrambot.model.Message;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class AlbumHandler {
    public static final int CHECK_RATE = 100;

    protected static ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    protected Consumer<AlbumData> albumsConsumer;
    protected HashMap<String, AlbumData> stack = new HashMap<>();
    protected ReentrantLock lock = new ReentrantLock();

    protected ScheduledFuture<?> future;

    public AlbumHandler(Consumer<AlbumData> albumsConsumer) {
        this.albumsConsumer = albumsConsumer;
    }

    public void handle(NewMessageEvent event) {
        Message message = event.data;

        if (message == null || message.mediaGroupId() == null || message.photo() == null)
            return;

        lock.lock();

        try {
            if (!stack.containsKey(message.mediaGroupId()))
                stack.put(message.mediaGroupId(), new AlbumData());

            stack.get(message.mediaGroupId()).add(event);
        }finally {
            lock.unlock();
        }

        if (future != null)
            return;

        future = executor.scheduleAtFixedRate(this::checkStack, CHECK_RATE, CHECK_RATE, TimeUnit.MILLISECONDS);
    }

    public void waitAll() {
        if (future == null || future.isDone())
            return;

        try {
            future.get();
        } catch (Exception ignored) {}
    }

    protected void checkStack() {
        Set<AlbumData> removed = new HashSet<>();

        lock.lock();
        try {
            if (stack.isEmpty()) {
                future.cancel(false);
                future = null;

                return;
            }

            stack.values().forEach(a -> {
                if (a.getLastAddingTime() + CHECK_RATE > System.currentTimeMillis())
                    return;

                removed.add(a);
            });
        }finally {
            lock.unlock();
        }

        removed.forEach((a) -> {
            stack.remove(a.getMediaGroupId());
            albumsConsumer.accept(a);
        });
    }
}
