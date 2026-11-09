package com.dailyatelier.dailyatelier.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PointConsistencyRepository {
    private final JdbcTemplate jdbcTemplate;

    public List<DiagnosticRow> findSemanticMismatches() {
        List<DiagnosticRow> rows = new ArrayList<>();
        rows.addAll(query("""
                select 'USER_WITHOUT_ACCOUNT', 'USER', u.user_id,
                       '사용자에게 포인트 계정이 없습니다'
                from users u
                left join point_account a on a.user_id = u.user_id
                where a.user_id is null
                """));
        rows.addAll(query("""
                select 'LEDGER_WITHOUT_ACCOUNT', 'USER', t.user_id,
                       '원장 사용자의 포인트 계정이 없습니다'
                from point_transaction t
                left join point_account a on a.user_id = t.user_id
                where a.user_id is null
                group by t.user_id
                """));
        rows.addAll(query("""
                select 'ACTIVE_HOLD_BALANCE_MISMATCH', 'USER', a.user_id,
                       concat('계정 예치 잔액=', a.held_balance,
                              ', 활성 예치 합계=', coalesce(sum(h.amount), 0))
                from point_account a
                left join point_hold h
                  on h.user_id = a.user_id and h.status = 'HELD'
                group by a.user_id, a.held_balance
                having a.held_balance <> coalesce(sum(h.amount), 0)
                """));
        rows.addAll(query("""
                select 'ACTIVE_HOLD_ART_REFERENCE_MISMATCH', 'HOLD', concat('', h.hold_id),
                       '활성 예치가 같은 작품의 active_point_hold_id로 참조되지 않습니다'
                from point_hold h
                join art a on a.art_id = h.art_id
                where h.status = 'HELD'
                  and (a.active_point_hold_id is null
                       or a.active_point_hold_id <> h.hold_id)
                """));
        rows.addAll(query("""
                select 'ART_ACTIVE_HOLD_MISMATCH', 'ART', concat('', a.art_id),
                       '작품의 활성 예치 참조가 예치의 소유 작품과 일치하지 않습니다'
                from art a
                left join point_hold h on h.hold_id = a.active_point_hold_id
                where a.active_point_hold_id is not null
                  and (h.hold_id is null or h.art_id <> a.art_id)
                """));
        rows.addAll(query("""
                select 'ORDER_COMMIT_MISMATCH', 'ORDER', concat('', o.order_id),
                       '주문 상태와 COMMIT 원장 거래가 일치하지 않습니다'
                from orders o
                where o.payment_method = 'INTERNAL_POINT'
                  and (
                    (o.status in ('PAID', 'PREPARING', 'SHIPPED', 'DELIVERED', 'CONFIRMED', 'REFUNDED')
                     and (select count(*) from point_transaction t
                          where t.reference_type = 'ORDER'
                            and t.reference_id = concat('', o.order_id)
                            and t.type = 'COMMIT') <> 1)
                    or
                    (o.status in ('PAYMENT_PENDING', 'CANCELED')
                     and exists (select 1 from point_transaction t
                                 where t.reference_type = 'ORDER'
                                   and t.reference_id = concat('', o.order_id)
                                   and t.type = 'COMMIT'))
                    or
                    exists (select 1 from point_transaction t
                            where t.reference_type = 'ORDER'
                              and t.reference_id = concat('', o.order_id)
                              and t.type = 'COMMIT'
                              and (t.user_id <> o.buyer_id
                                   or t.amount <> o.winning_price
                                   or t.available_delta <> 0
                                   or t.held_delta <> -o.winning_price))
                  )
                """));
        rows.addAll(query("""
                select 'ORDER_REFUND_MISMATCH', 'ORDER', concat('', o.order_id),
                       '주문 상태와 REFUND 원장 거래가 일치하지 않습니다'
                from orders o
                where o.payment_method = 'INTERNAL_POINT'
                  and (
                    (o.status = 'REFUNDED'
                     and (select count(*) from point_transaction t
                          where t.reference_type = 'ORDER'
                            and t.reference_id = concat('', o.order_id)
                            and t.type = 'REFUND') <> 1)
                    or
                    (o.status <> 'REFUNDED'
                     and exists (select 1 from point_transaction t
                                 where t.reference_type = 'ORDER'
                                   and t.reference_id = concat('', o.order_id)
                                   and t.type = 'REFUND'))
                    or
                    exists (select 1
                            from point_transaction refund
                            left join point_transaction original
                              on original.transaction_id = refund.reversal_of_transaction_id
                            where refund.reference_type = 'ORDER'
                              and refund.reference_id = concat('', o.order_id)
                              and refund.type = 'REFUND'
                              and (refund.user_id <> o.buyer_id
                                   or refund.amount <> o.winning_price
                                   or refund.available_delta <> o.winning_price
                                   or refund.held_delta <> 0
                                   or original.transaction_id is null
                                   or original.type <> 'COMMIT'
                                   or original.reference_type <> 'ORDER'
                                   or original.reference_id <> concat('', o.order_id)))
                  )
                """));
        rows.addAll(query("""
                select 'CHARGE_TRANSACTION_MISMATCH', 'CHARGE', concat('', c.charge_id),
                       '충전 상태와 CHARGE 원장 거래가 일치하지 않습니다'
                from point_charge c
                left join point_transaction t on t.transaction_id = c.charge_transaction_id
                where (c.status in ('PAID', 'REFUNDED') and
                       (t.transaction_id is null
                        or t.user_id <> c.user_id
                        or t.type <> case when c.provider = 'INTERNAL' then 'DEMO_CHARGE' else 'CHARGE' end
                        or t.reference_type <> 'CHARGE'
                        or t.reference_id <> concat('', c.charge_id)
                        or t.amount <> c.paid_amount
                        or t.available_delta <> c.paid_amount
                        or t.held_delta <> 0
                        or (select count(*) from point_transaction linked
                            where linked.reference_type = 'CHARGE'
                              and linked.reference_id = concat('', c.charge_id)
                              and linked.type in ('CHARGE', 'DEMO_CHARGE')) <> 1))
                   or (c.status in ('PENDING', 'FAILED', 'CANCELED')
                       and (c.charge_transaction_id is not null
                            or exists (select 1 from point_transaction linked
                                       where linked.reference_type = 'CHARGE'
                                         and linked.reference_id = concat('', c.charge_id)
                                         and linked.type in ('CHARGE', 'DEMO_CHARGE'))))
                """));
        rows.addAll(query("""
                select 'CHARGE_REFUND_MISMATCH', 'CHARGE', concat('', c.charge_id),
                       '충전 상태와 REFUND 원장 거래가 일치하지 않습니다'
                from point_charge c
                left join point_transaction refund on refund.transaction_id = c.refund_transaction_id
                where (c.status = 'REFUNDED' and
                       (refund.transaction_id is null
                        or refund.user_id <> c.user_id
                        or refund.type <> 'REFUND'
                        or refund.reference_type <> 'CHARGE'
                        or refund.reference_id <> concat('', c.charge_id)
                        or refund.amount <> c.paid_amount
                        or refund.available_delta <> -c.paid_amount
                        or refund.held_delta <> 0
                        or refund.reversal_of_transaction_id is null
                        or refund.reversal_of_transaction_id <> c.charge_transaction_id
                        or (select count(*) from point_transaction linked
                            where linked.reference_type = 'CHARGE'
                              and linked.reference_id = concat('', c.charge_id)
                              and linked.type = 'REFUND') <> 1))
                   or (c.status <> 'REFUNDED'
                       and (c.refund_transaction_id is not null
                            or exists (select 1 from point_transaction linked
                                       where linked.reference_type = 'CHARGE'
                                         and linked.reference_id = concat('', c.charge_id)
                                         and linked.type = 'REFUND')))
                """));
        return List.copyOf(rows);
    }

    private List<DiagnosticRow> query(String sql) {
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new DiagnosticRow(
                resultSet.getString(1),
                resultSet.getString(2),
                resultSet.getString(3),
                resultSet.getString(4)
        ));
    }

    public record DiagnosticRow(
            String type,
            String targetType,
            String targetId,
            String reason) {
    }
}
