package tsb99x.kinescope

import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpHeaderValues.TEXT_HTML
import io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import io.vertx.core.Future
import io.vertx.core.http.HttpServerResponse
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
        router.get("/").handler(this::index)
        router.get("/kinesis").coHandler(this::listStreams)
        router.get("/kinesis/:streamName").coHandler(this::listShards)
        router.get("/kinesis/:streamName/:shardId").coHandler(this::readShard)

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
            .html(ctx.failure().message)
    }

    private fun index(ctx: RoutingContext) {
        ctx.response().html("<a href='kinesis'>kinesis</a>")
    }

    private suspend fun listStreams(ctx: RoutingContext) {
        val res = request(PB_LIST_STREAMS, ListStreams(null))

        if (res.streamNames.isEmpty()) {
            ctx.response().html("no stream exists yet")
            return
        }

        val streamNameLinks = res.streamNames
            .joinToString("<br><br>") { "<a href='${ctx.request().path()}/$it'>$it</a>" }

        ctx.response().html(streamNameLinks)
    }

    private suspend fun listShards(ctx: RoutingContext) {
        val streamName = ctx.pathParam("streamName")

        val res = request(PB_LIST_SHARDS, ListShards(streamName))

        if (res.shardIds.isEmpty()) {
            ctx.response().html("no shard exists yet")
            return
        }

        val shardIds = res.shardIds
            .joinToString("<br><br>") { "<a href='${ctx.request().path()}/$it'>$it</a>" }

        ctx.response().html(shardIds)
    }

    private suspend fun readShard(ctx: RoutingContext) {
        val streamName = ctx.pathParam("streamName")
        val shardId = ctx.pathParam("shardId")

        val res = request(PB_READ_SHARD, ReadShard(streamName, shardId))

        if (res.records.isEmpty()) {
            ctx.response().html("no record exists yet")
            return
        }

        val records = res.records
            .joinToString("<br><br>")

        ctx.response().html(records)
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

private fun HttpServerResponse.html(body: String?): Future<Void> {
    return putHeader(CONTENT_TYPE, TEXT_HTML).end(body)
}
