package cv.igrp.platform.access_management.shared.config;

import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Bounds how long a storage call may block.
 *
 * <p>The MinIO SDK defaults connect, write <em>and</em> read timeouts to five
 * minutes ({@code S3Base.DEFAULT_CONNECTION_TIMEOUT}). A misconfigured or
 * unreachable storage endpoint therefore pins the calling Tomcat worker for up
 * to five minutes per request rather than failing fast — so enough concurrent
 * uploads (file upload, or an archive request) can exhaust the HTTP thread pool
 * and take the whole API down with it. A storage outage should degrade uploads,
 * not escalate into an API outage.
 *
 * <p>Failing fast also restores the error handling that already exists: a hang
 * is not an exception, so {@code AuditReportArchiveService}'s catch — and the
 * "Failed to upload…" log with it — never fires. With a bounded timeout the call
 * throws, the caller gets a 400, and the cause is logged.
 *
 * <p>{@link MinioClient#setTimeout(long, long, long)} is applied to the client
 * the framework's {@code MinioAutoConfiguration} built, rather than replacing
 * that bean, so none of its endpoint/credential wiring is duplicated here.
 *
 * <p>Timeouts are per socket operation, not per request: a slow-but-progressing
 * upload of a large export is not cut off at {@code write}, only a stall is.
 *
 * <p>Note this covers the MinIO provider only. Under {@code igrp.storage-provider=s3}
 * the AWS SDK builds an immutable {@code S3Client} with no post-hoc setter, so
 * bounding it means rebuilding the bean — deferred rather than duplicating the
 * framework's wiring here.
 */
@Configuration
@ConditionalOnClass(MinioClient.class)
public class StorageClientTimeoutConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageClientTimeoutConfig.class);

    private final ObjectProvider<MinioClient> minioClient;
    private final Duration connectTimeout;
    private final Duration writeTimeout;
    private final Duration readTimeout;

    public StorageClientTimeoutConfig(
            ObjectProvider<MinioClient> minioClient,
            @Value("${igrp.storage.timeout.connect:10s}") Duration connectTimeout,
            @Value("${igrp.storage.timeout.write:60s}") Duration writeTimeout,
            @Value("${igrp.storage.timeout.read:60s}") Duration readTimeout) {
        this.minioClient = minioClient;
        this.connectTimeout = connectTimeout;
        this.writeTimeout = writeTimeout;
        this.readTimeout = readTimeout;
    }

    @PostConstruct
    void applyTimeouts() {
        // Absent when igrp.storage-provider is not 'minio' — nothing to bound.
        minioClient.ifAvailable(client -> {
            client.setTimeout(connectTimeout.toMillis(), writeTimeout.toMillis(), readTimeout.toMillis());
            log.info("MinIO client timeouts bounded: connect={}, write={}, read={} (SDK default is 5m on all three)",
                    connectTimeout, writeTimeout, readTimeout);
        });
    }
}
