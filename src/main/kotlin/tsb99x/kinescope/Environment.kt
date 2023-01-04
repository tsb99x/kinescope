package tsb99x.kinescope

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.get

fun envConfig(): JsonObject {
    val env = System.getenv().mapValues { it.value.toIntOrNull() ?: it.value }
    return JsonObject(env)
}

class EnvironmentVariableNotFound(envVar: String) :
    RuntimeException("environment variable $envVar is required, but was not found")

inline fun <reified T> JsonObject.requiredProperty(envVar: String): T {
    when (val res: T? = optionalProperty(envVar)) {
        null -> throw EnvironmentVariableNotFound(envVar)
        else -> return res
    }
}

class EnvironmentVariableConversionFailed(envVar: String, targetClass: String) :
    RuntimeException("failed to convert value of environment variable $envVar to type $targetClass")

inline fun <reified T> JsonObject.optionalProperty(envVar: String): T? {
    when (val res: T? = get(envVar)) {
        !is T -> throw EnvironmentVariableConversionFailed(envVar, T::class.java.simpleName)
        else -> return res
    }
}
