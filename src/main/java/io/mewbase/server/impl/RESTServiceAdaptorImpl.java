package io.mewbase.server.impl;

import io.mewbase.bson.BsonObject;
import io.mewbase.client.Client;
import io.mewbase.server.Binder;
import io.mewbase.server.RESTServiceAdaptor;
import io.mewbase.server.impl.cqrs.CQRSManager;
import io.mewbase.server.impl.cqrs.QueryImpl;
import io.mewbase.util.AsyncResCF;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 *
 * TODO content types
 *
 * Created by tim on 11/01/17.
 */
public class RESTServiceAdaptorImpl implements RESTServiceAdaptor {

    private final static Logger logger = LoggerFactory.getLogger(RESTServiceAdaptorImpl.class);


    private String host = "0.0.0.0";
    private int port = 8080;

    private final ServerImpl server;
    private HttpServer httpServer;
    private Router router;
    private CQRSManager cqrsManager;

    public RESTServiceAdaptorImpl(ServerImpl server) {
        this.server = server;
        this.cqrsManager = server.getCqrsManager();
        this.httpServer = server.getVertx().createHttpServer(new HttpServerOptions().setHost(host).setPort(port));
        router = Router.router(server.getVertx());
        router.route().handler(BodyHandler.create());
        httpServer.requestHandler(router::accept);
    }

    @Override
    public RESTServiceAdaptor exposeCommand(String commandName, String uri, HttpMethod httpMethod) {
        router.route(httpMethod, uri).handler(rc -> {
            // TODO what about params?
            Buffer buff = rc.getBody();
            BsonObject command = new BsonObject(buff);
            CompletableFuture<Void> cf = cqrsManager.callCommandHandler(commandName, command);
            cf.whenComplete((v, t) -> {
                if (t == null) {
                    rc.response().end(); // 200-OK
                } else {
                    rc.response().setStatusCode(500).end();
                }
            });
        });
        return this;
    }

    @Override
    public RESTServiceAdaptor exposeQuery(String queryName, String uri) {

        QueryImpl query = server.getCqrsManager().getQuery(queryName);
        if (query == null) {
            throw new IllegalArgumentException("No such query " + queryName);
        }

        router.route(HttpMethod.GET, uri).handler(rc -> {
            BsonObject params = new BsonObject(rc.pathParams());
            RESTServiceAdaptorQueryExecution qe = new RESTServiceAdaptorQueryExecution(query, params, rc.response());
            rc.response().closeHandler(v -> qe.close());
            qe.start();
        });
        return this;
    }

    @Override
    public RESTServiceAdaptor exposeFindByID(String binderName, String uri) {
        Binder binder = server.getBinder(binderName);
        if (binder == null) {
            throw new IllegalArgumentException("No such binder " + binder);
        }
        router.route(HttpMethod.GET, uri).handler(rc -> {
            String id = rc.request().params().get("id");
            if (id == null) {
                rc.response().setStatusCode(404).end();
            } else {
                binder.get(id).whenComplete((doc, t) -> {
                    if (t != null)  {
                        logger.error("Failed to lookup document", t);
                        rc.response().setStatusCode(500).end();
                    } else {
                        rc.response().end(doc.encodeToString());
                    }
                });
            }
        });
        return this;
    }

    @Override
    public RESTServiceAdaptor setHost(String host) {
        this.host = host;
        return this;
    }

    @Override
    public RESTServiceAdaptor setPort(int port) {
        this.port = port;
        return this;
    }

    @Override
    public CompletableFuture<Void> start() {
        // TODO more than one instance of server

        AsyncResCF<HttpServer> ar = new AsyncResCF<>();
        httpServer.listen(ar);
        return ar.thenApply(server -> null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        AsyncResCF<Void> ar = new AsyncResCF<>();
        httpServer.close(ar);
        return ar;
    }

    private class RESTServiceAdaptorQueryExecution extends QueryExecution {

        private final HttpServerResponse response;

        public RESTServiceAdaptorQueryExecution(QueryImpl query, BsonObject params,
                                                HttpServerResponse response) {
            super(query, params);
            response.setChunked(true);
            this.response = response;
            response.write("[");
        }

        private int toAckBytes;

        @Override
        protected Buffer writeQueryResult(BsonObject document, boolean last) {
            checkContext();
            Buffer buff = Buffer.buffer(document.encodeToString());
            response.write(buff);
            if (!last) {
                response.write(",");
            } else {
                response.write("]");
            }
            if (last) {
                response.end();
            } else {
                if (!response.writeQueueFull()) {
                    sendAck(buff.length());
                } else {
                    toAckBytes += buff.length();
                    response.drainHandler(v -> {
                        sendAck(toAckBytes);
                        toAckBytes = 0;
                    });
                }
            }
            return buff;
        }

        // TODO better flow control
        private void sendAck(int bytes) {
            context.runOnContext(v -> handleAck(bytes));
        }
    }
}