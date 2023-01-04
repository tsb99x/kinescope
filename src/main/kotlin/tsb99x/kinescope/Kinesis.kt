package tsb99x.kinescope

import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.Record
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType
import java.io.UncheckedIOException
import java.net.URI

data class ListStreams(val limit: Int) : Request
data class ListStreamsRes(val streamNames: List<String>) : Response
data class ListShards(val streamName: String, val limit: Int) : Request
data class ListShardsRes(val shardIds: List<String>) : Response
data class ReadShard(val streamName: String, val shardId: String, val limit: Int) : Request
data class ReadShardRes(val records: List<String>) : Response

val PB_LIST_STREAMS = Postbox<ListStreams, ListStreamsRes>("kinesis.list-streams")
val PB_LIST_SHARDS = Postbox<ListShards, ListShardsRes>("kinesis.list-shards")
val PB_READ_SHARD = Postbox<ReadShard, ReadShardRes>("kinesis.read-shard")

private val log = LoggerFactory.getLogger(Kinesis::class.java)

class Kinesis : CoroutineVerticle() {

    private lateinit var client: KinesisAsyncClient

    override suspend fun start() {
        val accessKeyId: String = config.requiredProperty("AWS_ACCESS_KEY_ID")
        val secretAccessKey: String = config.requiredProperty("AWS_SECRET_ACCESS_KEY")
        val sessionToken: String? = config.optionalProperty("AWS_SESSION_TOKEN")
        val region: String = config.requiredProperty("AWS_REGION")
        val endpointOverride: String? = config.optionalProperty("AWS_ENDPOINT_OVERRIDE")

        val credentials = sessionToken?.let {
            log.warn("using session credentials")
            AwsSessionCredentials.create(accessKeyId, secretAccessKey, it)
        } ?: let {
            log.warn("using basic credentials")
            AwsBasicCredentials.create(accessKeyId, secretAccessKey)
        }

        client = KinesisAsyncClient.builder()
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .region(Region.of(region))
            .apply {
                endpointOverride?.let {
                    log.warn("using endpoint override of {}", endpointOverride)
                    endpointOverride(URI.create(endpointOverride))
                } ?: log.warn("using AWS endpoint configuration")
            }
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
            it.limit(req.limit)
        }.await()

        return ListStreamsRes(res.streamNames())
    }

    private suspend fun listShards(req: ListShards): ListShardsRes {
        val res = client.listShards {
            it.streamName(req.streamName)
            it.maxResults(req.limit)
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

        val records = getRecords(res.shardIterator(), req.limit)
            .map {
                try {
                    it.data().asUtf8String()
                } catch (_: UncheckedIOException) {
                    "failed to parse as UTF8 String: ${it.data()}"
                }
            }

        return ReadShardRes(records)
    }

    private suspend fun getRecords(shardIterator: String, limit: Int): List<Record> {
        val res = client.getRecords {
            it.shardIterator(shardIterator)
            it.limit(limit)
        }.await()

        return res.records()
    }

}
