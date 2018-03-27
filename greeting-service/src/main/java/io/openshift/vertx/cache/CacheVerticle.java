package io.openshift.vertx.cache;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.ext.web.handler.StaticHandler;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CacheVerticle extends AbstractVerticle {

    private static final String KEY = "NAME";
    private RemoteCache<String, String> cache;
    private WebClient client;
    private int ttl = 10;
    private final Logger LOGGER = LoggerFactory.getLogger("Cache-Verticle");

    @Override
    public void start(Future<Void> future) {
        ttl = config().getInteger("cache.ttl", 5);
        // HTTP API
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.get("/api/greeting").handler(this::greeting);
        router.get("/api/cached").handler(this::isCached);
        router.delete("/api/cached").handler(this::clearTheValue);
        router.post("/api/ttl").handler(this::setTTL);
        router.get("/health").handler(rc -> rc.response().end("OK"));
        router.get("/*").handler(StaticHandler.create());


        // Access to Cute name service.
        client = WebClient.create(vertx, new WebClientOptions()
            .setDefaultHost("cute-name-service")
            .setDefaultPort(8080)
        );


        Completable startHttpServer = vertx
            .createHttpServer()
            .requestHandler(router::accept)
            .rxListen(config().getInteger("http.port", 8080))
            .toCompletable()
            .doOnComplete(() -> LOGGER.info("HTTP Server started"));
        
        startHttpServer
            .subscribe(CompletableHelper.toObserver(future));
    }

    private void clearTheValue(RoutingContext rc) {
        retrieveCache()
            .flatMap(cache -> vertx.rxExecuteBlocking(future -> {
                cache.remove(KEY);
                future.complete();
            }))
            .toCompletable()
            .subscribe(
                () -> rc.response().setStatusCode(204).end(),
                rc::fail
            );
    }

    private void setTTL(RoutingContext rc) {
        JsonObject body = rc.getBodyAsJson();
        int newTTL = body.getInteger("ttl", -1);
        if (newTTL > 0) {
            rc.response().setStatusCode(400).end("Invalid payload, ttl expected");
        } else {
            ttl = newTTL;
            rc.response()
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("ttl", ttl).encode());
        }
    }

    private Single<RemoteCache<String, String>> retrieveCache() {
        if (cache != null) {
            return Single.just(cache);
        } else {
            return vertx.<RemoteCache<String, String>>rxExecuteBlocking(future ->
                future.complete(new RemoteCacheManager(
                    new ConfigurationBuilder().addServer()
                        .host("cache-server")
                        .port(11222)
                        .build())
                    .getCache()
                ))
                .doOnSuccess(c -> {
                    cache = c;
                    LOGGER.info("The cache has been retrieved");
                });
        }
    }

    private void isCached(RoutingContext rc) {
        getCachedValue()
            .map(Optional::isPresent)
            .onErrorReturnItem(false)
            .map(cached -> new JsonObject().put("cached", cached))
            .map(JsonObject::encode)
            .subscribe(
                message -> rc.response()
                    .putHeader("content-type", "application/json")
                    .end(message),
                rc::fail
            );
    }

    private void greeting(RoutingContext rc) {
        getCachedValue()
            .flatMap(maybe ->
                maybe.map(Single::just)
                    .orElse(
                        client.get("/api/name")
                            .rxSend()
                            .map(HttpResponse::bodyAsJsonObject)
                            .map(j -> j.getString("name"))
                            .flatMap(this::putInCache)
                    )
            )
            .map(name -> new JsonObject().put("message", "Hello " + name))
            .onErrorReturn(t -> new JsonObject().put("message", "Unable to call the service: " + t.getMessage()))
            .map(JsonObject::encode)
            .subscribe(
                message -> rc.response()
                    .putHeader("content-type", "application/json")
                    .end(message),
                rc::fail
            );
    }

    private Single<String> putInCache(String name) {
        return vertx
            .rxExecuteBlocking(future -> {
                cache.put(KEY, name, ttl, TimeUnit.SECONDS);
                future.complete();
            })
            .map(x -> name);
    }

    private Single<Optional<String>> getCachedValue() {
        return retrieveCache()
            .flatMap(cache ->
                vertx.<Optional<String>>rxExecuteBlocking(future -> {
                    String value = cache.get(KEY);
                    future.complete(Optional.ofNullable(value));
                }))
            .doOnError(t -> LOGGER.error("Unable to retrieve the cache", t));
    }
}
