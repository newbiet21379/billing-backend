package com.acme.billing.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for MinIO S3 client and bucket setup.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(StorageProperties.class)
public class MinioConfig {

    private final StorageProperties storageProperties;

    /**
     * Creates and configures the MinIO client bean.
     *
     * @return Configured MinioClient instance
     */
    @Bean
    public MinioClient minioClient() {
        try {
            // MinIO configuration from application.yml
            String endpoint = System.getenv().getOrDefault("MINIO_ENDPOINT", "http://localhost:9000");
            String accessKey = System.getenv().getOrDefault("MINIO_ACCESS_KEY", "minioadmin");
            String secretKey = System.getenv().getOrDefault("MINIO_SECRET_KEY", "minioadmin");
            String region = System.getenv().getOrDefault("MINIO_REGION", "us-east-1");

            log.info("Configuring MinIO client for endpoint: {}", endpoint);
            log.info("Using bucket: {}", storageProperties.getBucketName());

            MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region(region)
                .build();

            log.info("MinIO client configured successfully");
            return client;

        } catch (Exception e) {
            log.error("Failed to configure MinIO client", e);
            throw new RuntimeException("Failed to configure MinIO client", e);
        }
    }
}