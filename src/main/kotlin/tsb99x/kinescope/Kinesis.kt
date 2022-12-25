package tsb99x.kinescope

import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType
import java.io.Serializable
import java.io.UncheckedIOException
import java.net.URI

const val LIST_STREAMS_ADDR = "kinesis.list-streams"
const val LIST_SHARDS_ADDR = "kinesis.list-shards"
const val READ_SHARD_ADDR = "kinesis.read-shard"

data class ListStreamsRes(val streamNames: List<String>) : Serializable
data class ListShards(val streamName: String) : Serializable
data class ListShardsRes(val shardIds: List<String>) : Serializable
data class ReadShard(val streamName: String, val shardId: String) : Serializable
data class ReadShardRes(val records: List<String>) : Serializable

private val log = LoggerFactory.getLogger(KinesisKotlin::class.java)

class KinesisKotlin : CoroutineVerticle() {

    private lateinit var client: KinesisAsyncClient

    override suspend fun start() {
        val region = Region.of(config.getString("kinesis.region", "eu-central-1"))
        val accessKeyId = config.getString("kinesis.access-key-id", "access-key-id")
        val secretAccessKey = config.getString("kinesis.secret-access-key", "secret-access-key")
        val endpointOverride = URI.create(config.getString("kinesis.endpoint-override", "http://localhost:4566"))

        val basicCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey)
        val credentialsProvider = StaticCredentialsProvider.create(basicCredentials)

        client = KinesisAsyncClient.builder()
            .endpointOverride(endpointOverride)
            .region(region)
            .credentialsProvider(credentialsProvider)
            .asyncConfiguration { it.advancedOption(FUTURE_COMPLETION_EXECUTOR, vertx.nettyEventLoopGroup()) }
            .build()

        receive(LIST_STREAMS_ADDR, this::handleListStreams)
        receive(LIST_SHARDS_ADDR, this::handleListShards)
        receive(READ_SHARD_ADDR, this::handleReadShard)

        log.info("started Kinesis Vertice")
    }

    private suspend fun handleListStreams(any: Any): ListStreamsRes {
        val res = client.listStreams {
            it.limit(100)
        }.await()

        return ListStreamsRes(res.streamNames())
    }

    private suspend fun handleListShards(req: ListShards): ListShardsRes {
        val res = client.listShards {
            it.streamName(req.streamName)
            it.maxResults(100)
        }.await()

        val shardIds = res.shards().map { it.shardId() }

        return ListShardsRes(shardIds)
    }

    private suspend fun handleReadShard(req: ReadShard): ReadShardRes {
        val iteratorType = ShardIteratorType.TRIM_HORIZON

        val rgsi = client.getShardIterator {
            it.streamName(req.streamName)
            it.shardId(req.shardId)
            it.shardIteratorType(iteratorType)
        }.await()

        val rgr = client.getRecords {
            it.shardIterator(rgsi.shardIterator())
            it.limit(100)
        }.await()

        val records = rgr.records().map {
            try {
                it.data().asUtf8String()
            } catch (_: UncheckedIOException) {
                "failed to parse as UTF8 String: ${it.data()}"
            }
        }

        return ReadShardRes(records)
    }

    override suspend fun stop() {
        client.close()
        log.info("stopped Kinesis Vertice")
    }

}
