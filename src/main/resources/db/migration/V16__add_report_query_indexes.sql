-- Report queries (ReportQueryRepository) luon loc order_status_history theo to_status = 'CONFIRMED'
-- roi range theo created_at; idx_order_status_history_order_id hien co khong ho tro duoc filter nay,
-- moi query dang phai full-scan bang nay khi no lon dan theo so luong order status transition.
CREATE INDEX idx_order_status_history_to_status_created_at
    ON order_status_history(to_status, created_at);

-- revenueByDay JOIN refunds theo order_id + loc status = 'REFUNDED' de tinh tong hoan tien —
-- idx_refunds_payment_id hien co khong ho tro duoc truy van nay.
CREATE INDEX idx_refunds_order_id_status ON refunds(order_id, status);
