package cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.UserIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserIdentityJpaRepository extends JpaRepository<UserIdentityEntity, UUID> {

    /**
     * Raw lookup — returns the identity link regardless of the linked user's
     * status. Use only when the caller explicitly needs to inspect a
     * soft-deleted identity (e.g. anonymisation routines, audit screens).
     * Most code wants {@link #findActiveByProviderAndUserId(String, String)}.
     */
    Optional<UserIdentityEntity> findByProviderAndUserId(String provider, String userId);

    /**
     * Login-safe lookup: returns the identity link ONLY when the underlying
     * {@code t_user} row is still in a logged-in-able state (ACTIVE or
     * TEMPORARY). Soft-deleted ({@code DELETED}) and administratively
     * suspended ({@code INACTIVE}) users are excluded so the federated-login
     * path treats them as "no existing link" and falls through to
     * provisioning a fresh account.
     *
     * <p>Without this guard, a previously-DELETED user logging back in via
     * the same upstream IdP would silently resurrect the deleted row as
     * the JWT subject — defeating the deletion and producing a "valid token,
     * always-denied requests" UX (UserStatusGuard rejects DELETED). See
     * the IgrpOidcUserService / ClaimsEnrichmentService call sites.
     */
    @Query("""
                SELECT i FROM UserIdentityEntity i
                WHERE i.provider = :provider
                  AND i.userId   = :userId
                  AND i.user.status IN (
                      cv.igrp.platform.access_management.shared.application.constants.Status.ACTIVE,
                      cv.igrp.platform.access_management.shared.application.constants.Status.TEMPORARY
                  )
            """)
    Optional<UserIdentityEntity> findActiveByProviderAndUserId(@Param("provider") String provider,
                                                               @Param("userId") String userId);
}
