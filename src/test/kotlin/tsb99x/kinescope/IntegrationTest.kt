package tsb99x.kinescope

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.*
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.kotlin.core.json.jsonObjectOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.TimeUnit

val dockerImage: DockerImageName = DockerImageName.parse("localstack/localstack:1.3.1")
val localstack: GenericContainer<*> = GenericContainer(dockerImage).withExposedPorts(4566)
    .withEnv("SERVICES", "Kinesis")
    .apply { start() }

val config = jsonObjectOf(
    "AWS_ACCESS_KEY_ID" to "local",
    "AWS_SECRET_ACCESS_KEY" to "local",
    "AWS_REGION" to "eu-central-1",
    "AWS_ENDPOINT_OVERRIDE" to "http://${localstack.host}:${localstack.firstMappedPort}"
)

@ExtendWith(VertxExtension::class)
class IntegrationTest {

    companion object {

        @JvmStatic
        @BeforeAll
        fun beforeAll(vertx: Vertx, testContext: VertxTestContext) {
            vertx.deployVerticle(
                Application(),
                deploymentOptionsOf(config = config),
                testContext.succeedingThenComplete()
            )
        }

    }

    @Test
    @Timeout(5, timeUnit = TimeUnit.SECONDS)
    fun `index of services should work`(vertx: Vertx, testContext: VertxTestContext) {
        vertx.createHttpClient()
            .get("/")
            .expectOk()
            .expectHtml()
            .compose { it.body() }
            .map { assertEquals("<a href='kinesis'>kinesis</a>", it.toString()); it }
            .bindToContext(testContext)
    }

    @Test
    @Timeout(5, timeUnit = TimeUnit.SECONDS)
    fun `listing of streams should work with no streams present`(vertx: Vertx, testContext: VertxTestContext) {
        vertx.createHttpClient()
            .get("/kinesis")
            .expectOk()
            .expectHtml()
            .compose { it.body() }
            .map { assertEquals("no stream exists yet", it.toString()); it }
            .bindToContext(testContext)
    }

}

private fun HttpClient.get(uri: String): Future<HttpClientResponse> {
    return request(HttpMethod.GET, 8888, "localhost", uri).compose { it.send() }
}

private fun Future<HttpClientResponse>.expectOk(): Future<HttpClientResponse> {
    return map { assertEquals(200, it.statusCode()); it }
}

private fun Future<HttpClientResponse>.expectHtml(): Future<HttpClientResponse> {
    return map { assertEquals("text/html", it.headers().get("Content-Type")); it }
}

private fun <T> Future<T>.bindToContext(testContext: VertxTestContext) {
    this.onSuccess { testContext.completeNow() }
        .onFailure { testContext.failNow(it) }
}
