-- Truoc day UNIQUE don cot (vnp_transaction_no) dung chung cho MOI event type. Gap: neu 1 event
-- AMOUNT_MISMATCH duoc ghi truoc cho 1 vnp_transaction_no, IPN PAYMENT_RESULT hop le den sau cung
-- transaction no se bi insertIfAbsent tra ve 0 dong -> bi coi la "da xu ly", payment khong bao gio
-- duoc confirm (silent failure).
--
-- Khong dung UNIQUE (vnp_transaction_no, event_type) don thuan: se pha hang rao chong double-process
-- GIUA 2 duong PAYMENT_RESULT (IPN) va QUERY_RECONCILE (querydr) — 2 duong nay co chu dich dung
-- chung 1 barrier (xem comment trong PaymentResultApplier). Thay vao do dung 2 partial unique index:
--   1. Nhom "processing" (PAYMENT_RESULT + QUERY_RECONCILE): van chung 1 barrier theo transaction no
--      — IPN va reconcile dong thoi chi 1 ben thang, giu nguyen semantics cu.
--   2. AMOUNT_MISMATCH: dedupe rieng, khong chan nhom processing nua.
-- insertIfAbsent doi sang ON CONFLICT DO NOTHING (khong chi dinh target) de bat conflict tu ca 2 index.

ALTER TABLE payment_webhook_events DROP CONSTRAINT payment_webhook_events_vnp_transaction_no_key;

CREATE UNIQUE INDEX uq_webhook_events_processing_txn_no
    ON payment_webhook_events (vnp_transaction_no)
    WHERE event_type IN ('PAYMENT_RESULT', 'QUERY_RECONCILE');

CREATE UNIQUE INDEX uq_webhook_events_mismatch_txn_no
    ON payment_webhook_events (vnp_transaction_no)
    WHERE event_type = 'AMOUNT_MISMATCH';
