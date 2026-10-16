package cv.igrp.platform.access_management.m2m.interfaces.rest;

import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.global_configuration.application.dto.GlobalConfigurationDTO;
import cv.igrp.platform.access_management.global_configuration.application.queries.GetGlobalConfigurationQuery;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@code GET /api/m2m/global-configuration} — the machine-readable view of the
 * organization Brand.
 *
 * <p>Framework apps render public pages (login, invitation) with no signed-in
 * user, so they cannot call the user-scoped {@code GET /api/global-configuration}
 * and fall back to default colours until some authenticated request warms the
 * cache. This reads the same row with a {@code client_credentials} token.
 *
 * <p>The status contract is inherited, not reimplemented: the query handler
 * already answers 400 for an unknown type and 404 when nothing is saved, and
 * {@code GetGlobalConfigurationQueryHandlerTest} covers that. What matters here
 * is that this endpoint reaches the SAME query and does not soften either
 * outcome on the way out.
 */
class M2MGlobalConfigurationTest {

    private QueryBus queryBus;
    private M2MController controller;

    @BeforeEach
    void setUp() {
        queryBus = mock(QueryBus.class);
        controller = new M2MController(mock(CommandBus.class), queryBus);
    }

    @Test
    @DisplayName("reads the requested type through the same query as the user-scoped GET")
    void delegatesToTheSameQuery() {
        GlobalConfigurationDTO dto = new GlobalConfigurationDTO();
        when(queryBus.handle(any(GetGlobalConfigurationQuery.class))).thenReturn(ResponseEntity.ok(dto));

        ResponseEntity<GlobalConfigurationDTO> response =
                controller.getGlobalConfigurationForMachine("ORGANIZATION");

        ArgumentCaptor<GetGlobalConfigurationQuery> sent =
                ArgumentCaptor.forClass(GetGlobalConfigurationQuery.class);
        verify(queryBus).handle(sent.capture());
        assertEquals("ORGANIZATION", sent.getValue().getType());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(dto, response.getBody());
    }

    @Test
    @DisplayName("CLUSTER is passed through unchanged — the endpoint does not pin a type")
    void passesClusterThrough() {
        when(queryBus.handle(any(GetGlobalConfigurationQuery.class)))
                .thenReturn(ResponseEntity.ok(new GlobalConfigurationDTO()));

        controller.getGlobalConfigurationForMachine("CLUSTER");

        ArgumentCaptor<GetGlobalConfigurationQuery> sent =
                ArgumentCaptor.forClass(GetGlobalConfigurationQuery.class);
        verify(queryBus).handle(sent.capture());
        assertEquals("CLUSTER", sent.getValue().getType());
    }

    @Test
    @DisplayName("a missing configuration still surfaces as 404, not an empty 200")
    void notFoundPropagates() {
        when(queryBus.handle(any(GetGlobalConfigurationQuery.class))).thenThrow(
                IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_GLOBAL_CONFIGURATION_NOT_FOUND,
                        "ORGANIZATION"));

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> controller.getGlobalConfigurationForMachine("ORGANIZATION"));

        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getStatusCode().value());
    }

    @Test
    @DisplayName("an unknown type still surfaces as 400")
    void unknownTypePropagates() {
        when(queryBus.handle(any(GetGlobalConfigurationQuery.class))).thenThrow(
                IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_GLOBAL_CONFIGURATION_TYPE_NOT_FOUND,
                        "NONSENSE"));

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> controller.getGlobalConfigurationForMachine("NONSENSE"));

        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatusCode().value());
    }
}
