package cv.igrp.platform.access_management.central_audit.application.dto;

/**
 * A service's chain in the central store, checked (v3-22, #32).
 *
 * @param checked        rows checked, up to and including the first broken one
 * @param brokenSequence the first row whose hash, previous hash or place does not hold; null when valid
 */
public record BusinessAuditChainReportDTO(String service, long checked, boolean valid, Long brokenSequence,
                                          String reason) {
}
