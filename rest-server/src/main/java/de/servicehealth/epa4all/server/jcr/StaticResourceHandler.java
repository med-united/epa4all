package de.servicehealth.epa4all.server.jcr;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class StaticResourceHandler {

    void setupRoutes(@Observes Router router) {
        router.route("/frontend/*").handler(StaticHandler.create("frontend").setIndexPage("index.html"));
    }
}