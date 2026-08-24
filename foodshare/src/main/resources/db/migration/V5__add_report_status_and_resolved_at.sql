ALTER TABLE reports ADD COLUMN report_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER report_type;
ALTER TABLE reports ADD COLUMN resolved_at DATETIME(6) NULL AFTER reference_id;
CREATE INDEX idx_reports_status ON reports (report_status);
