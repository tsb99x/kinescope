package tsb99x.kinescope

import io.vertx.core.Future
import io.vertx.core.eventbus.Message
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.coroutines.toReceiveChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun <T> CoroutineVerticle.request(address: String, req: Any?): Future<Message<T>> =
    vertx.eventBus().request(address, req)

fun <T> Message<T>.fail(t: Throwable) =
    fail(-1, t.message)

fun Route.coHandler(fn: suspend (RoutingContext) -> Unit) {
    handler { ctx ->
        CoroutineScope(ctx.vertx().dispatcher()).launch {
            try {
                fn(ctx)
            } catch (e: Exception) {
                ctx.fail(e)
            }
        }
    }
}

fun <I, O> CoroutineVerticle.receive(address: String, fn: suspend (I) -> O) {
    val ch = vertx.eventBus().localConsumer<I>(address).toReceiveChannel(vertx)
    launch {
        for (msg in ch) {
            try {
                msg.reply(fn(msg.body()))
            } catch (t: Throwable) {
                msg.fail(t)
            }
        }
    }
}
