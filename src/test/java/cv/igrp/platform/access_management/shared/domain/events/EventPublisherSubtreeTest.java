package cv.igrp.platform.access_management.shared.domain.events;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** v3-31 (#39): a department created or deleted below changes the subtree of every department above. */
class EventPublisherSubtreeTest {

    private final ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
    private final EventPublisher events = new EventPublisher(spring);

    @Test
    void everyDepartmentAboveGetsAStructureEvent() {
        DepartmentEntity top = department("TOP", null);
        DepartmentEntity middle = department("MIDDLE", top);

        events.publishSubtreeChanged(middle);

        ArgumentCaptor<DepartmentScopeChangedEvent> published = ArgumentCaptor.forClass(DepartmentScopeChangedEvent.class);
        verify(spring, times(2)).publishEvent(published.capture());
        assertThat(published.getAllValues()).extracting(DepartmentScopeChangedEvent::getDepartmentCode)
                .containsExactly("MIDDLE", "TOP");
        assertThat(published.getAllValues()).extracting(DepartmentScopeChangedEvent::getChangeType)
                .containsOnly(DepartmentScopeChangedEvent.CHANGE_STRUCTURE);
    }

    @Test
    void aTopLevelDepartmentHasNoneAbove() {
        events.publishSubtreeChanged(null);

        verifyNoInteractions(spring);
    }

    private static DepartmentEntity department(String code, DepartmentEntity parent) {
        DepartmentEntity department = new DepartmentEntity();
        department.setCode(code);
        department.setParentId(parent);
        return department;
    }
}
