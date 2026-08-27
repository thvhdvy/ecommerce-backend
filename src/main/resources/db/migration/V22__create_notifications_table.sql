CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    type VARCHAR(30) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    reference_id BIGINT,
    payload VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NOT NULL DEFAULT now(),
    created_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP
);

-- Phuc vu truc tiep query cua NotificationDispatcher (WHERE status='PENDING' AND next_retry_at <= now()).
CREATE INDEX idx_notifications_status_next_retry_at ON notifications (status, next_retry_at);
CREATE INDEX idx_notifications_user_id ON notifications (user_id);
