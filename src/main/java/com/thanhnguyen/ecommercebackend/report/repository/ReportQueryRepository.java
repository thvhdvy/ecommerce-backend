package com.thanhnguyen.ecommercebackend.report.repository;

import com.thanhnguyen.ecommercebackend.order.entity.Order;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Chi doc, khong CRUD - khong extend JpaRepository. Ngoai le duoc phep JOIN thang qua nhieu bang/module
 * (orders, order_status_history, refunds, order_items, products, categories) cho muc dich reporting,
 * theo doc "Module: Report" (khong ap dung rule "khong JOIN cheo module" vi day la read-only aggregation).
 */
@org.springframework.stereotype.Repository
public interface ReportQueryRepository extends org.springframework.data.repository.Repository<Order, Long> {

    @Query(value = """
            SELECT osh.confirmed_date AS day,
                   SUM(o.total_amount) AS gross,
                   COALESCE(SUM(r.total_refund), 0) AS refund
            FROM orders o
            JOIN (SELECT order_id, CAST(created_at AS date) AS confirmed_date
                  FROM order_status_history
                  WHERE to_status = 'CONFIRMED') osh ON osh.order_id = o.id
            LEFT JOIN (SELECT order_id, SUM(amount) AS total_refund
                       FROM refunds
                       WHERE status = 'REFUNDED'
                       GROUP BY order_id) r ON r.order_id = o.id
            WHERE (CAST(:from AS date) IS NULL OR osh.confirmed_date >= :from)
              AND (CAST(:to AS date) IS NULL OR osh.confirmed_date <= :to)
            GROUP BY osh.confirmed_date
            ORDER BY osh.confirmed_date
            """, nativeQuery = true)
    List<RevenueByDayProjection> revenueByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = """
            SELECT p.category_id AS categoryId,
                   c.name AS categoryName,
                   SUM(oi.unit_price_snapshot * oi.quantity) AS grossRevenue
            FROM order_items oi
            JOIN products p ON p.id = oi.product_id
            JOIN categories c ON c.id = p.category_id
            WHERE oi.order_id IN (
                SELECT order_id FROM order_status_history
                WHERE to_status = 'CONFIRMED'
                  AND (CAST(:from AS date) IS NULL OR CAST(created_at AS date) >= :from)
                  AND (CAST(:to AS date) IS NULL OR CAST(created_at AS date) <= :to)
            )
            GROUP BY p.category_id, c.name
            ORDER BY grossRevenue DESC
            """, nativeQuery = true)
    List<RevenueByCategoryProjection> revenueByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = """
            SELECT oi.product_id AS productId,
                   p.name AS productName,
                   SUM(oi.quantity) AS quantitySold,
                   SUM(oi.unit_price_snapshot * oi.quantity) AS grossRevenue
            FROM order_items oi
            JOIN products p ON p.id = oi.product_id
            WHERE oi.order_id IN (
                SELECT order_id FROM order_status_history
                WHERE to_status = 'CONFIRMED'
                  AND (CAST(:from AS date) IS NULL OR CAST(created_at AS date) >= :from)
                  AND (CAST(:to AS date) IS NULL OR CAST(created_at AS date) <= :to)
            )
            GROUP BY oi.product_id, p.name
            ORDER BY grossRevenue DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TopProductProjection> topProducts(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("limit") int limit);
}
