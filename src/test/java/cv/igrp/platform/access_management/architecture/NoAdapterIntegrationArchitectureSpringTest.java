package cv.igrp.platform.access_management.architecture;


import cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService;
import cv.igrp.platform.access_management.users.application.commands.InviteUserCommandHandler;
import cv.igrp.platform.access_management.users.application.commands.RespondUserInvitationCommandHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(SpringExtension.class)
public class NoAdapterIntegrationArchitectureSpringTest {

    @Mock
    private ConfigurationService mockConfigurationService;

    @Test
    @DisplayName("Validação da Definição de Arquitetura de Integração (sem IAdapter)")
    void validateNoAdapterInIntegrationLayer() {
        // Garantindo que usamos a extensão do Spring Test e Mockito (criado mock)
        org.junit.jupiter.api.Assertions.assertNotNull(mockConfigurationService);
        
        // Verifica as principais classes de handlers e serviços de arranque
        enforceNoAdapterUsage(ConfigurationService.class);
        enforceNoAdapterUsage(InviteUserCommandHandler.class);
        enforceNoAdapterUsage(RespondUserInvitationCommandHandler.class);

        // Simulando fluxo livre do Adapter na camada de Configuration
        Mockito.doNothing().when(mockConfigurationService).initializeSystemConfiguration();
        mockConfigurationService.initializeSystemConfiguration();
        Mockito.verify(mockConfigurationService, Mockito.times(1)).initializeSystemConfiguration();
    }

    private void enforceNoAdapterUsage(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if ("cv.igrp.framework.auth.core.adapter.IAdapter".equals(field.getType().getName()) || 
                "cv.igrp.framework.auth.core.adapter.UserAdapter".equals(field.getType().getName()) ||
                "cv.igrp.framework.auth.core.adapter.RoleAdapter".equals(field.getType().getName())) {
                fail("Violação de Arquitetura (Sem IAdapter): A classe " + clazz.getName() 
                     + " possui injeção direta de " + field.getType().getName());
            }
        }
    }
}
