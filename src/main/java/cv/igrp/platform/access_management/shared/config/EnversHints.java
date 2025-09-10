package cv.igrp.platform.access_management.shared.config;

import jakarta.persistence.Entity;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.data.envers.repository.support.EnversRevisionRepositoryImpl;
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
            
        }

        private void registerFixedClasses(RuntimeHints hints) {
            // Hibernate/Envers
            Class<?>[] fixedClasses = {
                EnversRevisionRepositoryImpl.class,
                org.hibernate.proxy.ProxyConfiguration.class,
                org.hibernate.engine.spi.SessionImplementor.class,
                
		
                
            };
            
            for (Class<?> clazz : fixedClasses) {
                hints.reflection().registerType(clazz, MemberCategory.values());
            }
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