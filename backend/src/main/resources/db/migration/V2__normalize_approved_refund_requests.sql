UPDATE orders
SET refund_request_status = 'APPROVED'
WHERE status = 'REFUNDED'
  AND refund_request_status = 'REQUESTED';
