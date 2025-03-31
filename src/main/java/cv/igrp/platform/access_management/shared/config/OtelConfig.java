package cv.igrp.platform.access_management.shared.config;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OtelConfig {

   @Bean
    @ConditionalOnProperty(name = "otel.exporter.otlp.endpoint")
    public OpenTelemetry openTelemetry(
        @Value("${OTEL_COLLECTOR_ENDPOINT}") String endpoint,
        @Value("${OTEL_SERVICE_NAME}") String serviceName) {
        // Criação direta do Resource sem Attributes.of()
        Resource resource = Resource.getDefault()
            .toBuilder()
            .put("service.name", serviceName)  // Método direto do Resource.Builder
            .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(
                BatchSpanProcessor.builder(
                    OtlpGrpcSpanExporter.builder()
                        .setEndpoint(endpoint)
                        .build()
                ).build()
            )
            .setResource(resource)
            .build();

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build();
    }
}