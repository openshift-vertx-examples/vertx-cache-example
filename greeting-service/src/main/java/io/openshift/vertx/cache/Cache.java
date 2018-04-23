package io.openshift.vertx.cache;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.reactivex.core.Vertx;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.NearCacheMode;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Manages the interactions with the Cache server.
 */
public class Cache<K, V> {

    private final Vertx vertx;
    private final RemoteCache<K, V> cache;

    public static <K, V> Single<Cache<K, V>> create(Vertx vertx) {
        Configuration config = new ConfigurationBuilder()
            .nearCache().mode(NearCacheMode.INVALIDATED).maxEntries(10)
            .addServer()
            .host("cache-server")
            .port(11222)
            .build();
        return vertx.
            <RemoteCache<K, V>>rxExecuteBlocking(
                future -> {
                    RemoteCache<K, V> cache = new RemoteCacheManager(config).getCache();
                    future.complete(cache);
                }
            )
            .map(rc -> new Cache<>(vertx, rc));
    }

    private Cache(Vertx vertx, RemoteCache<K, V> rc) {
        this.vertx = vertx;
        this.cache = rc;
    }

    public Completable remove(K key) {
        return vertx.rxExecuteBlocking(
            future -> {
                cache.remove(key);
                future.complete();
            }
        ).toCompletable();
    }

    public Single<Optional<V>> get(K key) {
        // While this method can use the Maybe type, I found easier to use an Optional.
        return vertx.rxExecuteBlocking(future -> {
            V value = cache.get(key);
            future.complete(Optional.ofNullable(value));
        });
    }

    public Completable put(K key, V value, long ttl) {
        return vertx.rxExecuteBlocking(future -> {
            cache.put(key, value, ttl, TimeUnit.SECONDS);
            future.complete();
        }).toCompletable();
    }

}
