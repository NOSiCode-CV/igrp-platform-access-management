package cv.igrp.platform.access_management.session.infrastructure.persistence.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Guards the one bean name in this application that cannot be the default.
 *
 * <p>Spring Session's {@code JdbcHttpSessionConfiguration} publishes its
 * {@code JdbcIndexedSessionRepository} as {@code sessionRepository} — the same
 * name Spring Data derives from {@link SessionRepository}. With bean-definition
 * overriding disabled (the Boot default) the two collide and the context refuses
 * to start, so the service fails at boot rather than at request time.
 *
 * <p>This resolves the name through {@link AnnotationBeanNameGenerator}, which is
 * what Spring Data's {@code RepositoryBeanNameGenerator} delegates to, so it
 * exercises the real mechanism rather than just re-reading the annotation. It
 * fails if someone drops the explicit name from {@code @Repository}, which looks
 * like harmless tidying and is not.
 *
 * <p>It does not replace a context-start check — only a running context proves
 * the whole graph resolves. It does mean this particular regression cannot reach
 * a deployment again.
 */
class SessionRepositoryBeanNameTest {

    /** The name Spring Session occupies. Must stay free. */
    private static final String SPRING_SESSION_BEAN_NAME = "sessionRepository";

    private String resolveBeanName(Class<?> type) {
        return new AnnotationBeanNameGenerator().generateBeanName(
                new AnnotatedGenericBeanDefinition(type), new DefaultListableBeanFactory());
    }

    @Test
    @DisplayName("The t_user_session repository does not claim Spring Session's bean name")
    void doesNotCollideWithSpringSession() {
        String name = resolveBeanName(SessionRepository.class);

        assertNotEquals(SPRING_SESSION_BEAN_NAME, name, """
                SessionRepository resolves to the bean name Spring Session already uses.

                JdbcHttpSessionConfiguration publishes JdbcIndexedSessionRepository as
                'sessionRepository'. Both definitions cannot be registered, and
                spring.main.allow-bean-definition-overriding is off, so the application
                will not start.

                Restore the explicit name on @Repository rather than enabling overriding —
                which store would win is then down to registration order.""");
        assertEquals("userSessionRepository", name,
                "the explicit @Repository name is what other code and docs refer to");
    }
}
