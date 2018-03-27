package io.openshift.vertx.cache;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.openshift.client.OpenShiftClient;
import io.restassured.RestAssured;
import org.arquillian.cube.openshift.impl.enricher.AwaitRoute;
import org.arquillian.cube.openshift.impl.enricher.RouteURL;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.delete;
import static io.restassured.RestAssured.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;


@RunWith(Arquillian.class)
public class CacheVerticleIT {

    @RouteURL("cache-booster")
    @AwaitRoute
    private URL route;

    @ArquillianResource
    private OpenShiftClient oc;

    @Before
    public void setup() {
        RestAssured.baseURI = route.toString();

        await().atMost(5, TimeUnit.MINUTES).until(() -> {
            List<Pod> list = oc.pods().list().getItems();
            Optional<Pod> first = list.stream()
                .filter(p -> p.getMetadata().getName().contains("cache-server")
                    && !p.getMetadata().getName().contains("deploy"))
                .filter(p -> p.getStatus().getPhase().contains("Running"))
                .findFirst();
            
            return first.isPresent();
        });

        System.out.println("JDG ready...");

        await().atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions()
            .until(() ->
                get(route).andReturn().statusCode() == 200
            );

        System.out.println("Application ready...");
    }

    @Test
    public void testCaching() {
        await().atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions()
            .until(() ->
                get(route).andReturn().statusCode() == 200
            );

        long begin = System.currentTimeMillis();

        System.out.println("Call - " + get("/api/greeting").asString());

        get("/api/greeting").then()
            .statusCode(equalTo(200));
        long end = System.currentTimeMillis();
        long duration1 = end - begin;

        begin = System.currentTimeMillis();
        get("/api/greeting").then().statusCode(equalTo(200));
        end = System.currentTimeMillis();
        long duration2 = end - begin;

        assertThat(duration2).isLessThan(duration1);

        get("/api/cached").then().body("cached", is(true));

        await().atMost(120, TimeUnit.SECONDS)
            .catchUncaughtExceptions()
            .until(() ->
                get("/api/cached").asString().contains("false")
            );

        get("/api/cached").then().body("cached", is(false));

        begin = System.currentTimeMillis();
        get("/api/greeting").then().statusCode(equalTo(200));
        end = System.currentTimeMillis();
        long duration3 = end - begin;

        assertThat(duration2).isLessThan(duration3);
    }

    @Test
    public void testCachingAndClear() {
        await().atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions()
            .until(() ->
                get(route).andReturn().statusCode() == 200
            );

        long begin = System.currentTimeMillis();

        get("/api/greeting").then()
            .statusCode(equalTo(200));
        long end = System.currentTimeMillis();
        long duration1 = end - begin;

        begin = System.currentTimeMillis();
        get("/api/greeting").then().statusCode(equalTo(200));
        end = System.currentTimeMillis();
        long duration2 = end - begin;

        assertThat(duration2).isLessThan(duration1);

        get("/api/cached").then().body("cached", is(true));

        delete("/api/cached").then().statusCode(204);

        get("/api/cached").then().body("cached", is(false));
    }
}