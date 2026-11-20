package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static cv.igrp.platform.access_management.compat.CompatSupport.BUSINESS_DEPARTMENT;
import static cv.igrp.platform.access_management.compat.CompatSupport.BUSINESS_ROLE;
import static cv.igrp.platform.access_management.compat.CompatSupport.q;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #47: accepting an invitation that carries a department and a role, end to end through the
 * real API and Postgres, as the App Center does it. The invitee already exists as TEMPORARY
 * (their first sign-in at the IdP provisions them) and has validated the OTP.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvitationAcceptIT extends AbstractCompatIT {

    @Autowired
    ApplicationContext context;

    CompatSupport api;
    JdbcTemplate jdbc;

    @BeforeAll
    void setUp() {
        api = new CompatSupport(context);
        jdbc = context.getBean(JdbcTemplate.class);
        api.ensureBusinessData();
    }

    @Test
    @DisplayName("Accepting an invitation with a role: the user is ACTIVE, holds the role, and it is active")
    void acceptWithRole() {
        String email = "invitee-" + UUID.randomUUID() + "@example.cv";

        HttpResponse<String> invited = api.postJson("/api/users/invite", api.superadminToken(),
                "{\"email\":" + q(email) + ",\"departmentCode\":" + q(BUSINESS_DEPARTMENT)
                        + ",\"roles\":[" + q(BUSINESS_ROLE) + "],\"locale\":\"pt\"}");
        assertEquals(2, invited.statusCode() / 100, invited.body());
        String token = jdbc.queryForObject(
                "SELECT token FROM t_invitation_entity WHERE identifier_value = ?", String.class, email);

        // As the first sign-in leaves the invitee: TEMPORARY, provisioned from the IdP.
        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO t_user (id, username, name, email, email_verified, status, created_by, created_date)
                VALUES (?, ?, 'Invitee', ?, true, 'TEMPORARY', 'test', now())""", userId, email, email);
        // The OTP sent to the invitation email was validated.
        jdbc.update("""
                INSERT INTO t_otp_verification (otp_code, reference_id, status, expires_at)
                VALUES ('000000', ?, 'APPROVED', ?)""", token, Timestamp.from(Instant.now().plusSeconds(600)));

        HttpResponse<String> accepted = api.postJson("/api/users/invite/response?token=" + token,
                api.userToken(userId, email), "{\"accept\":true}");

        assertEquals(200, accepted.statusCode(), accepted.body());
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM t_user WHERE id = ?", String.class, userId));
        assertEquals(BUSINESS_ROLE, jdbc.queryForObject("""
                SELECT r.code FROM t_user_role_assignment a JOIN t_role r ON r.id = a.role_id
                 WHERE a.user_id = ?""", String.class, userId));
        assertEquals(BUSINESS_ROLE, jdbc.queryForObject("""
                SELECT r.code FROM t_user u JOIN t_role r ON r.id = u.active_role_id WHERE u.id = ?""",
                String.class, userId));
        assertEquals("ACCEPTED", jdbc.queryForObject(
                "SELECT status FROM t_invitation_entity WHERE token = ?", String.class, token));
    }
}
