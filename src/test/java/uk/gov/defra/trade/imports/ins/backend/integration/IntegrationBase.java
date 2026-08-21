package uk.gov.defra.trade.imports.ins.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.utility.DockerImageName.parse;

import io.floci.testcontainers.FlociContainer;
import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueInProgressException;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
abstract class IntegrationBase {

    static final String AWS_REGION = "eu-west-2";
    static final String QUEUE_NAME = "trade_imports_ins_notifications.fifo";

    static final FlociContainer FLOCI = new FlociContainer(
        DockerImageName.parse("floci/floci:latest"))
        .withRegion(AWS_REGION);

    static final MongoDBContainer MONGO_CONTAINER = new MongoDBContainer(
        DockerImageName.parse("mongo:7.0")).withExposedPorts(27017);

    static String queueUrl;

    static {
        Startables.deepStart(MONGO_CONTAINER, FLOCI).join();
        try (SqsClient sqs = localSqsClient()) {
            sqs.createQueue(CreateQueueRequest.builder()
                .queueName(QUEUE_NAME)
                .attributes(new EnumMap<>(Map.of(
                    QueueAttributeName.FIFO_QUEUE, "true",
                    QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true")))
                .build());
            queueUrl = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(QUEUE_NAME).build()).queueUrl();
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_CONTAINER::getReplicaSetUrl);
        registry.add("spring.data.mongodb.ssl.enabled", () -> "false");
        registry.add("aws.sqs.notification.queue-url", () -> queueUrl);
        registry.add("aws.sqs.notification.wait-time-seconds", () -> "1");
        registry.add("app.aws.endpoint-override", FLOCI::getEndpoint);
        registry.add("app.aws.access-key-id", FLOCI::getAccessKey);
        registry.add("app.aws.secret-access-key", FLOCI::getSecretKey);
    }

    protected static void sendToSqs(String body, String messageGroupId) {
        try (SqsClient sqs = localSqsClient()) {
            sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .messageGroupId(messageGroupId)
                .messageDeduplicationId(java.util.UUID.randomUUID().toString())
                .build());
        }
    }

    protected static void purgeQueue() {
        try (SqsClient sqs = localSqsClient()) {
            sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
        } catch (PurgeQueueInProgressException e) {
            log.debug("Queue purge already in progress, skipping: {}", e.getMessage());
        }
    }

    protected static void awaitQueueEmpty() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            try (SqsClient sqs = localSqsClient()) {
                var attrs = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                    .build()).attributes();
                int visible = Integer.parseInt(attrs.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
                int inFlight = Integer.parseInt(attrs.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
                assertThat(visible + inFlight).isZero();
            }
        });
    }

    private static SqsClient localSqsClient() {
        return SqsClient.builder()
            .endpointOverride(URI.create(FLOCI.getEndpoint()))
            .region(Region.of(AWS_REGION))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(FLOCI.getAccessKey(), FLOCI.getSecretKey())))
            .build();
    }
}
