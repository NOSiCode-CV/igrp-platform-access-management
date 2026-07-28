package cv.igrp.platform.access_management.shared.security;

public final class ServiceAccountTokenClaims {

    public static final String CLAIM_PRINCIPAL_TYPE = "principal_type";
    public static final String CLAIM_SERVICE_ACCOUNT_ID = "service_account_id";
    public static final String CLAIM_CLIENT_ID = "client_id";
    /**
     * Code of the application the OAuth client / service account is
     * associated with. Empty string when neither the service account nor
     * the OAuth client carries an application FK.
     */
    public static final String CLAIM_APPLICATION_CODE = "application_code";
    public static final String PRINCIPAL_TYPE_SERVICE_ACCOUNT = "SERVICE_ACCOUNT";

    private ServiceAccountTokenClaims() {
    }
}
