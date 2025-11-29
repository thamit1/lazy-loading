package com.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Vert.x SSE lazy-loading microservice
 * - GET /          -> serves index.html (UI)
 * - GET /stream    -> SSE stream: sends "fast", then "slow", then "done" events, and closes
 */
public class App extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new App());
  }

  @Override
  public void start() {
    Router router = Router.router(vertx);

    // Serve the UI from webroot (index.html)
    router.get("/").handler(ctx -> {
      ctx.response()
        .putHeader("content-type", "text/html; charset=utf-8")
        .sendFile("webroot/index.html");
    });

    // Static assets (if any)
    router.route("/static/*").handler(StaticHandler.create("webroot"));

    // SSE endpoint
    router.get("/stream").handler(ctx -> {
      HttpServerResponse resp = ctx.response();
      resp.setChunked(true);
      resp.putHeader("Content-Type", "text/event-stream");
      resp.putHeader("Cache-Control", "no-cache");
      resp.putHeader("Connection", "keep-alive");

      // Prepare data
      List<Map<String, Object>> fastRows = new ArrayList<>();
      for (int i = 1; i <= 6; i++) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", i);
        r.put("name", "Item " + i);
        r.put("price", i * 10);
        fastRows.add(r);
      }

      // 1) send fast event immediately
      String fastJson = toJson(fastRows);
      writeSseEvent(resp, "fast", fastJson);

      // 2) after delay, send slow values
      vertx.setTimer(3000, tid -> {
        List<Map<String, Object>> slowRows = fastRows.stream().map(fr -> {
          Map<String, Object> s = new LinkedHashMap<>();
          s.put("id", fr.get("id"));
          s.put("slow_value", "Computed-" + fr.get("id"));
          return s;
        }).collect(Collectors.toList());

        String slowJson = toJson(slowRows);
        writeSseEvent(resp, "slow", slowJson);

        // 3) send done and close shortly after
        writeSseEvent(resp, "done", "finished");
        vertx.setTimer(100, t2 -> resp.end());
      });
    });

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(9219, ar -> {
        if (ar.succeeded()) {
          System.out.println("HTTP server started on http://localhost:9219");
        } else {
          ar.cause().printStackTrace();
        }
      });
  }

  private static void writeSseEvent(HttpServerResponse resp, String eventName, String data) {
    // SSE format: event: <name>\ndata: <json>\n\n
    resp.write("event: " + eventName + "\n");
    resp.write("data: " + data + "\n\n");
  }

  private static String toJson(Object obj) {
    // Minimal JSON using built-in; for complex cases use Jackson (already added)
    // Here we’ll use a simple manual serialization via Jackson to be safe.
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      return mapper.writeValueAsString(obj);
    } catch (Exception e) {
      return "{}";
    }
  }
}
