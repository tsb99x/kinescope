package tsb99x.kinescope

import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(Application::class.java)

class Application : CoroutineVerticle() {

    override suspend fun start() {
        vertx.deployVerticle(HttpServer()).await()
        vertx.deployVerticle(KinesisKotlin()).await()
        log.info("started Application Vertice")
    }

    override suspend fun stop() {
        log.info("stopped Application Vertice")
    }

}
