package com.genealogy.platform.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * S3-compatible object storage fixture. LocalStack provides the
 * S3 API so services can run their full object lifecycle (upload
 * session, multipart, signed URL) against a real HTTP endpoint
 * without depending on AWS. Per ADR-E0.5-01 the production
 * baseline is S3 (SaaS) or MinIO (on-prem); LocalStack stands in
 * for both during tests.
 */
public class S3Fixture implements TestcontainersFixture {

    private static final DockerImageName IMAGE = DockerImageName.parse("localstack/localstack:3.7");

    private final LocalStackContainer container;

    public S3Fixture() {
        this(new LocalStackContainer(IMAGE)
                .withServices(LocalStackContainer.Service.S3)
                .withReuse(true));
    }

    public S3Fixture(LocalStackContainer container) {
        this.container = container;
    }

    @Override
    public void overrideProperties(DynamicPropertyRegistry registry) {
        if (!container.isRunning()) {
            container.start();
        }
        registry.add("platform.s3.endpoint", () -> container.getEndpointOverride(LocalStackContainer.Service.S3));
        registry.add("platform.s3.region", () -> container.getRegion());
        registry.add("platform.s3.access-key", container::getAccessKey);
        registry.add("platform.s3.secret-key", container::getSecretKey);
    }

    public LocalStackContainer container() {
        return container;
    }

    @Override
    public void stop() {
        // no-op; see PostgresFixture
    }
}
