package tsb99x.kinescope

import io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE
import io.netty.handler.codec.http.HttpHeaderValues.TEXT_HTML
import io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN
import io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.LoggerHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(HttpServer::class.java)

class HttpServer : CoroutineVerticle() {

    override suspend fun start() {
        val host = config.getString("http.host", "0.0.0.0")
        val port = config.getInteger("http.port", 8080)

        val server = vertx.createHttpServer()
        val router = Router.router(vertx)

        router.route().handler(LoggerHandler.create())
        router.route().failureHandler(this::handleFailure)
        router.get("/").coHandler(this::handleRoot)
        router.get("/:streamName").coHandler(this::handleStream)
        router.get("/:streamName/:shardId").coHandler(this::handleShard)

        server.requestHandler(router)
        val res = server.listen(port, host).await()
        log.info("started HTTP Server Vertice on http://{}:{}", host, res.actualPort())
    }

    private fun handleFailure(ctx: RoutingContext) {
        ctx.response()
            .setStatusCode(INTERNAL_SERVER_ERROR.code())
            .putHeader(CONTENT_TYPE, TEXT_PLAIN)
            .end(ctx.failure().message)
    }

    private suspend fun handleRoot(ctx: RoutingContext) {
        val r = request<ListStreamsRes>(LIST_STREAMS_ADDR, null).await()

        if (r.body().streamNames.isEmpty()) {
            ctx.response()
                .putHeader(CONTENT_TYPE, TEXT_PLAIN)
                .end("no stream exists yet")
            return
        }

        val streamNameLinks = r.body().streamNames
            .joinToString("<br/>") { "<a href='$it'>$it</a>" }

        ctx.response()
            .putHeader(CONTENT_TYPE, TEXT_HTML)
            .end(streamNameLinks)
    }

    private suspend fun handleStream(ctx: RoutingContext) {
        val streamName = ctx.pathParam("streamName")

        val r = request<ListShardsRes>(LIST_SHARDS_ADDR, ListShards(streamName)).await()

        val streamNameLinks = r.body().shardIds
            .joinToString("<br/>") { "<a href='${ctx.request().absoluteURI()}/$it'>$it</a>" }

        ctx.response()
            .putHeader(CONTENT_TYPE, HttpHeaders.TEXT_HTML)
            .end(streamNameLinks)
    }

    private suspend fun handleShard(ctx: RoutingContext) {
        val streamName = ctx.pathParam("streamName")
        val shardId = ctx.pathParam("shardId")

        val r = request<ReadShardRes>(READ_SHARD_ADDR, ReadShard(streamName, shardId)).await()

        if (r.body().records.isEmpty()) {
            ctx.response()
                .putHeader(CONTENT_TYPE, TEXT_PLAIN)
                .end("no records exists yet")
            return
        }

        val records = r.body().records
            .joinToString("<br/><br/>")

        ctx.response()
            .putHeader(CONTENT_TYPE, TEXT_HTML)
            .end(records)
    }

    override suspend fun stop() {
        log.info("stopped HTTP Server Vertice")
    }

}
