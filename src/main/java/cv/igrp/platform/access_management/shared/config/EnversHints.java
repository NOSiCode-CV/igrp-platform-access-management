package cv.igrp.platform.access_management.shared.config;

import jakarta.persistence.Entity;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.data.envers.repository.support.EnversRevisionRepositoryImpl;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;

@Configuration
@ImportRuntimeHints(EnversHints.Registrar.class)
public class EnversHints {

    public static class Registrar implements RuntimeHintsRegistrar,  ApplicationContextAware {
        private static ApplicationContext applicationContext;

        @Override
        public void setApplicationContext(ApplicationContext context) {
            applicationContext = context;
        }


        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
          
            registerFixedClasses(hints);
            
           
            registerModelEntities(hints, classLoader);
            
		         registerOpenTelemetryHints(hints);    //Somente Opentelemetry
        }

        private void registerFixedClasses(RuntimeHints hints) {
            // Hibernate/Envers
            Class<?>[] fixedClasses = {
                EnversRevisionRepositoryImpl.class,
                org.hibernate.proxy.ProxyConfiguration.class,
                org.hibernate.engine.spi.SessionImplementor.class,
                
		       // OpenTelemetry Essentials  / Somente Opentelemetry
                io.opentelemetry.sdk.resources.Resource.class,
                io.opentelemetry.sdk.trace.SdkTracerProvider.class,
                io.opentelemetry.sdk.trace.export.BatchSpanProcessor.class,
                io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter.class,
                io.opentelemetry.sdk.trace.SpanProcessor.class,
                io.opentelemetry.sdk.common.CompletableResultCode.class
                
            };
            
            for (Class<?> clazz : fixedClasses) {
                hints.reflection().registerType(clazz, MemberCategory.values());
            }
        }



		        // Somente Opentelemetry
        private void registerOpenTelemetryHints(RuntimeHints hints) {
         
            hints.resources().registerPattern("META-INF/services/io.opentelemetry.*");
            
          
            hints.reflection().registerType(io.opentelemetry.context.Context.class, MemberCategory.values());
        }
       


        public void registerModelEntities(RuntimeHints hints, ClassLoader classLoader) {
            if (applicationContext == null) {
                return;
            }

            // Get all beans annotated with @Entity
            Map<String, Object> entityBeans = applicationContext.getBeansWithAnnotation(Entity.class);

            if (entityBeans.isEmpty()) {
                return;
            }

            for (Object entity : entityBeans.values()) {
                hints.reflection().registerType(entity.getClass(), MemberCategory.values());
            }
        }

     
    }
}