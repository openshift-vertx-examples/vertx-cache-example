/*
 *
 *  Copyright 2016-2017 Red Hat, Inc, and individual contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.openshift.example;

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
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.delete;
import static io.restassured.RestAssured.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

/**
 * @author Martin Kouba
 * @author Slavomir Krupa
 */
@RunWith(Arquillian.class)
public class OpenShiftIT {

    private static final String NAME_SERVICE_APP = "cute-name-service";
    private static final String GREETING_SERVICE_APP = "greeting-service";


    @RouteURL(NAME_SERVICE_APP)
    @AwaitRoute(path = "/health")
    private URL cuteNameService;

    @RouteURL(GREETING_SERVICE_APP)
    @AwaitRoute
    private URL greetingServiceUrl;


    @ArquillianResource
    private OpenShiftClient oc;

    @Before
    public void setup() {
        RestAssured.baseURI = greetingServiceUrl.toExternalForm();
    }

    @Test
    public void testCaching() {
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

        await().atMost(120, TimeUnit.SECONDS)
            .catchUncaughtExceptions()
            .until(() ->
                get("/api/cached").asString().contains("false")
            );

        get("/api/cached").then().body("cached", is(false));
    }

    @Test
    public void testCachingAndClear() {
        await().atMost(2, TimeUnit.MINUTES)
            .catchUncaughtExceptions()
            .until(() ->
                get("/api/cached").asString().contains("false")
            );
        get("/api/greeting").then().statusCode(equalTo(200));
        get("/api/cached").then().body("cached", is(true));
        delete("/api/cached").then().statusCode(204);
        get("/api/cached").then().body("cached", is(false));
    }

}
