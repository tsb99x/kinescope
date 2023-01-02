package tsb99x.kinescope

import io.vertx.core.Verticle
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.toReceiveChannel
import kotlinx.coroutines.launch
import java.io.Serializable

interface Request : Serializable
interface Response : Serializable

@JvmInline
value class Postbox<I : Request, O : Response>(val address: String)

suspend fun <I : Request, O : Response> Verticle.request(postbox: Postbox<I, O>, req: I?): O {
    return vertx.eventBus()
        .request<O>(postbox.address, req)
        .await()
        .body()
}

fun <I : Request, O : Response> CoroutineVerticle.receive(postbox: Postbox<I, O>, fn: suspend (I) -> O) {
    val ch = vertx.eventBus().localConsumer<I>(postbox.address).toReceiveChannel(vertx)
    launch {
        for (msg in ch) {
            try {
                msg.reply(fn(msg.body()))
            } catch (t: Throwable) {
                msg.fail(-1, t.message)
            }
        }
    }
}
