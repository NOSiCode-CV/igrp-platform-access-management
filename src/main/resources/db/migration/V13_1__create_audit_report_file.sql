-- Unified Audit & Reports — archive of generated report exports.
--
-- Each row records one generated document (Audit/Access/Settings x PDF/XLSX/CSV)
-- that was uploaded to object storage. `file_path` is the storage key and the
-- retrieval handle: callers pass it to GET /api/files/url to get a presigned
-- link. Keys are prefixed `private/audit-reports/...` because that endpoint only
-- resolves paths starting with `private/` or `public/`.

CREATE TABLE IF NOT EXISTS t_audit_report_file (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    report_type   VARCHAR(20)  NOT NULL,
    format        VARCHAR(10)  NOT NULL,
    file_path     VARCHAR(500) NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(150),
    size_bytes    BIGINT,
    row_count     BIGINT,
    filters       TEXT,
    period_start  TIMESTAMPTZ,
    period_end    TIMESTAMPTZ,
    generated_by  VARCHAR(255),
    generated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_report_file PRIMARY KEY (id)
);

-- Listing is newest-first; the type/format pair is the common filter.
CREATE INDEX IF NOT EXISTS idx_audit_report_file_generated_at
    ON t_audit_report_file (generated_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_report_file_type_format
    ON t_audit_report_file (report_type, format);
