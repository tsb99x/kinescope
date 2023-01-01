package tsb99x.kinescope

import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpHeaderValues.TEXT_HTML
import io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN
import io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.LoggerHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(HttpServer::class.java)

class HttpServer : CoroutineVerticle() {

    override suspend fun start() {
        val host = config.optionalProperty("HTTP_HOST") ?: "0.0.0.0"
        val port = config.optionalProperty("HTTP_PORT") ?: 8888

        val server = vertx.createHttpServer()
        val router = Router.router(vertx)

        router.route().handler(LoggerHandler.create())
        router.route().failureHandler(this::failure)
        router.get("/").coHandler(this::root)
        router.get("/:streamName").coHandler(this::listShards)
        router.get("/:streamName/:shardId").coHandler(this::readShard)

        server.requestHandler(router)
        val res = server.listen(port, host).await()
        log.info("started HTTP Server Verticle on http://{}:{}", host, res.actualPort())
    }

    override suspend fun stop() {
        log.info("stopped HTTP Server Verticle")
    }

    private fun failure(ctx: RoutingContext) {
        ctx.response()
            .setStatusCode(INTERNAL_SERVER_ERROR.code())
            .putHeader(CONTENT_TYPE, TEXT_PLAIN)
            .end(ctx.failure().message)
    }

    private suspend fun root(ctx: RoutingContext) {
        val res = request(PB_LIST_STREAMS, ListStreams(null))

        if (res.body().streamNames.isEmpty()) {
            ctx.response()
                .putHeader(CONTENT_TYPE, TEXT_PLAIN)
                .end("no stream exists yet")
            return
        }

        val streamNameLinks = res.body().streamNames
            .joinToString("<br/>") { "<a href='$it'>$it</a>" }

        ctx.response()
            .putHeader(CONTENT_TYPE, TEXT_HTML)
            .end(streamNameLinks)
    }

    private suspend fun listShards(ctx: RoutingContext) {
        val streamName = ctx.pathParam("streamName")

        val res = request(PB_LIST_SHARDS, ListShards(streamName))

        val streamNameLinks = res.body().shardIds
            .joinToString("<br/>") { "<a href='${ctx.request().absoluteURI()}/$it'>$it</a>" }

        ctx.response()
            .putHeader(CONTENT_TYPE, HttpHeaders.TEXT_HTML)
            .end(streamNameLinks)
    }

    private suspend fun readShard(ctx: RoutingContext) {
        val streamName = ctx.pathParam("streamName")
        val shardId = ctx.pathParam("shardId")

        val res = request(PB_READ_SHARD, ReadShard(streamName, shardId))

        if (res.body().records.isEmpty()) {
            ctx.response()
                .putHeader(CONTENT_TYPE, TEXT_PLAIN)
                .end("no records exists yet")
            return
        }

        val records = res.body().records
            .joinToString("<br/><br/>")

        ctx.response()
            .putHeader(CONTENT_TYPE, TEXT_HTML)
            .end(records)
    }

}

private fun Route.coHandler(fn: suspend (RoutingContext) -> Unit) {
    handler { ctx ->
        CoroutineScope(ctx.vertx().dispatcher()).launch {
            try {
                fn(ctx)
            } catch (t: Throwable) {
                ctx.fail(t)
            }
        }
    }
}
