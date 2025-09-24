package cv.igrp.platform.access_management.app.application.queries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GetApplicationMenusQueryHandlerTest {

    @InjectMocks
    private GetApplicationMenusQueryHandler getApplicationMenusQueryHandler;

    @BeforeEach
    void setUp() {

    }

    @Test
    void testHandleGetApplicationMenusQuery() {

        // Example:
        // Given
        // GetApplicationMenusQuery query = new GetApplicationMenusQuery(...);
        //
        // When
        // ResponseEntity<List<MenuEntryDTO>> response = getApplicationMenusQueryHandler.handle(query);
        //
        // Then
        // assertNotNull(response);
        // assertEquals(..., response.getBody());
    }

}