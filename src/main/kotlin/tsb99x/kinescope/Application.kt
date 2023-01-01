package tsb99x.kinescope

import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

private val log = LoggerFactory.getLogger(Application::class.java)

class Application : CoroutineVerticle() {

    override suspend fun start() {
        log.info("starting Kinescope {}", javaClass.`package`.implementationVersion ?: "SNAPSHOT")
        val startupTime = measureTimeMillis {
            val config = envConfig()
            vertx.deployVerticle(HttpServer(), deploymentOptionsOf(config = config)).await()
            vertx.deployVerticle(Kinesis(), deploymentOptionsOf(config = config)).await()
        }
        log.info("started Application Verticle in {}s", startupTime / 1_000f)
    }

    override suspend fun stop() {
        log.info("stopped Application Verticle")
    }

}
