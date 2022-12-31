package tsb99x.kinescope

import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.Record
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType
import java.io.UncheckedIOException
import java.net.URI

data class ListStreams(val any: Any?) : Request
data class ListStreamsRes(val streamNames: List<String>) : Response
data class ListShards(val streamName: String) : Request
data class ListShardsRes(val shardIds: List<String>) : Response
data class ReadShard(val streamName: String, val shardId: String) : Request
data class ReadShardRes(val records: List<String>) : Response

val PB_LIST_STREAMS = Postbox<ListStreams, ListStreamsRes>("kinesis.list-streams")
val PB_LIST_SHARDS = Postbox<ListShards, ListShardsRes>("kinesis.list-shards")
val PB_READ_SHARD = Postbox<ReadShard, ReadShardRes>("kinesis.read-shard")

private val log = LoggerFactory.getLogger(Kinesis::class.java)

class Kinesis : CoroutineVerticle() {

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

        receive(PB_LIST_STREAMS, this::listStreams)
        receive(PB_LIST_SHARDS, this::listShards)
        receive(PB_READ_SHARD, this::readShard)

        log.info("started Kinesis Verticle")
    }

    override suspend fun stop() {
        client.close()
        log.info("stopped Kinesis Verticle")
    }

    private suspend fun listStreams(req: ListStreams): ListStreamsRes {
        val res = client.listStreams {
            it.limit(100)
        }.await()

        return ListStreamsRes(res.streamNames())
    }

    private suspend fun listShards(req: ListShards): ListShardsRes {
        val res = client.listShards {
            it.streamName(req.streamName)
            it.maxResults(100)
        }.await()

        val shardIds = res.shards().map { it.shardId() }

        return ListShardsRes(shardIds)
    }

    private suspend fun readShard(req: ReadShard): ReadShardRes {
        val iteratorType = ShardIteratorType.TRIM_HORIZON

        val res = client.getShardIterator {
            it.streamName(req.streamName)
            it.shardId(req.shardId)
            it.shardIteratorType(iteratorType)
        }.await()

        val records = getRecords(res.shardIterator())
            .map {
                try {
                    it.data().asUtf8String()
                } catch (_: UncheckedIOException) {
                    "failed to parse as UTF8 String: ${it.data()}"
                }
            }

        return ReadShardRes(records)
    }

    private suspend fun getRecords(shardIterator: String): List<Record> {
        val res = client.getRecords {
            it.shardIterator(shardIterator)
            it.limit(100)
        }.await()

        return res.records()
    }

}
