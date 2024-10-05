package su.knst.telegram.ai.utils;

import com.google.common.cache.*;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class CacheHandyBuilder {
    public static CacheBuilder<Object, Object> genericBuilder(long duration, TimeUnit unit, long maxSize, RemovalListener<Object, Object> removalListener) {
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();

        if (duration > 0 && unit != null)
            builder = builder.expireAfterAccess(duration, unit);

        if (maxSize > 0)
            builder = builder.maximumSize(maxSize);

        if (removalListener != null)
            builder = builder.removalListener(removalListener);

        return builder;
    }

    public static <T, X> LoadingCache<T, X> loading(long duration, TimeUnit unit, long maxSize, Function<T, X> loader, RemovalListener<T, X> removalListener) {
        CacheBuilder<Object, Object> builder = genericBuilder(duration, unit, maxSize, (RemovalListener<Object, Object>) removalListener);

        return builder.build(new CacheLoader<>() {
            @Override
            public X load(T key) {
                return loader.apply(key);
            }
        });
    }

    public static <T, X> LoadingCache<T, X> loading(long duration, TimeUnit unit, long maxSize, Function<T, X> loader) {
        return loading(duration, unit, maxSize, loader, null);
    }

    public static <T, X> LoadingCache<T, X> loading(long duration, TimeUnit unit, Function<T, X> loader) {
        return loading(duration, unit, 0, loader);
    }

    public static <T, X> LoadingCache<T, X> loading(Function<T, X> loader) {
        return loading(0, null, loader);
    }

    public static <T, X> Cache<T, X> cache(long duration, TimeUnit unit, long maxSize, RemovalListener<T, X> removalListener) {
        CacheBuilder<Object, Object> builder = genericBuilder(duration, unit, maxSize, (RemovalListener<Object, Object>) removalListener);

        return builder.build();
    }

    public static <T, X> Cache<T, X> cache(long duration, TimeUnit unit, long maxSize) {
        return cache(duration, unit, maxSize, null);
    }

    public static <T, X> Cache<T, X> cache(long duration, TimeUnit unit) {
        return cache(duration, unit, 0);
    }

    public static <T, X> Cache<T, X> cache() {
        return cache(0, null);
    }
}
