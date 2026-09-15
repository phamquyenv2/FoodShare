-- Matching candidate scan: role and eligibility predicates.
CREATE INDEX idx_users_matching_eligibility
    ON users (role, active, profile_completed);

-- Dashboard registration range and daily grouping.
CREATE INDEX idx_users_created
    ON users (created_at);

-- Scheduled timeout lookup: status equality followed by deadline range.
CREATE INDEX idx_orders_status_pickup_deadline
    ON orders (order_status, pickup_deadline);

-- Dashboard order range and daily grouping.
CREATE INDEX idx_orders_created
    ON orders (created_at);

-- Global admin queue and dashboard aggregates filter by status and time.
CREATE INDEX idx_payouts_status_created
    ON payouts (payout_status, created_at);

-- Supplier transaction history is ordered across every payout status.
CREATE INDEX idx_payouts_profile_created
    ON payouts (business_profile_id, created_at);

-- User notification feed sorts by creation time without filtering read state.
CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, created_at);

-- Moderation queue filters by status and is normally sorted chronologically.
CREATE INDEX idx_reports_status_created
    ON reports (report_status, created_at);
