package cv.igrp.platform.access_management.security_audit.application.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditRetentionPropertiesTest {

    @Test
    void defaultsToFiveYears() {
        assertThat(new AuditRetentionProperties().getDays()).isEqualTo(1825);
    }

    @Test
    void aPeriodBelowTheOneYearFloorFailsStartup() {
        AuditRetentionProperties properties = new AuditRetentionProperties();
        properties.setDays(364);

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("igrp.audit.retention.days")
                .hasMessageContaining("365");
    }

    @Test
    void theFloorItselfIsAccepted() {
        AuditRetentionProperties properties = new AuditRetentionProperties();
        properties.setDays(365);

        assertThatCode(properties::afterPropertiesSet).doesNotThrowAnyException();
    }
}
