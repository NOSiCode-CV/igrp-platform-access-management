package cv.igrp.platform.access_management.shared.config;

import cv.igrp.framework.filemanager.awss3.S3AutoConfiguration;
import cv.igrp.framework.filemanager.minio.MinioAutoConfiguration;
import cv.igrp.platform.filemanager.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Both storage providers wire up through their own auto-configuration, now that
 * the application no longer scans {@code cv.igrp}: the {@code StorageService}
 * must exist for {@code igrp.storage-provider=s3} as well as for {@code minio}.
 */
class StorageProviderWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MinioAutoConfiguration.class, S3AutoConfiguration.class))
            .withPropertyValues(
                    "igrp.minio.endpoint=localhost", "igrp.minio.port=9000", "igrp.minio.security=false",
                    "igrp.minio.access-key=k", "igrp.minio.secret-key=s", "igrp.minio.bucket-name=b",
                    "igrp.minio.url-expiration-time=300", "igrp.minio.region=us-east-1",
                    "igrp.s3.aws-endpoint=http://localhost:9000", "igrp.s3.aws-access-key=k",
                    "igrp.s3.aws-secret-key=s", "igrp.s3.secure=false", "igrp.s3.aws-bucket=b",
                    "igrp.s3.aws-url-expiration-time=300", "igrp.s3.aws-region=us-east-1",
                    "igrp.s3.aws-path-style-access=true");

    @Test
    @DisplayName("igrp.storage-provider=s3 yields a StorageService")
    void s3() {
        runner.withPropertyValues("igrp.storage-provider=s3").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(StorageService.class).size());
        });
    }

    @Test
    @DisplayName("igrp.storage-provider=minio yields a StorageService")
    void minio() {
        runner.withPropertyValues("igrp.storage-provider=minio").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(StorageService.class).size());
        });
    }
}
