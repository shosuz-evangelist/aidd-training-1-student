package com.example.ecsite;

import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.RequestStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/** テストごとにテーブルを空にし、必要なデータだけを作る。 */
@Component
public class TestDataHelper {
    private final JdbcTemplate jdbc;
    private final AtomicInteger seq = new AtomicInteger();

    public TestDataHelper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void reset() {
        jdbc.execute("TRUNCATE approval_history, delegation, purchase_request_item, purchase_request, employee RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE invoice_lines, invoices, plan_periods, subscriptions, reservations, course_sessions, courses, sale, cart_items, carts, inventories, products, customer RESTART IDENTITY CASCADE");
    }

    public long createCustomer(String email) {
        return jdbc.queryForObject(
                "INSERT INTO customer (name, email, password) VALUES (?, ?, 'pw') RETURNING id",
                Long.class, email, email);
    }

    /** 社員を作る。社員の ID は顧客の ID と同じ。役員の部署は null にする。 */
    public long createEmployee(long customerId, String department, EmployeeRole role) {
        jdbc.update("INSERT INTO employee (customer_id, department, role) VALUES (?, ?, ?)",
                customerId, department, role.name());
        return customerId;
    }

    /** 申請の明細。 */
    public record Item(long unitPrice, int quantity) {}

    /** 申請を作る。金額は明細の単価 x 数量の合計。承認待ちでないときの pendingRole は null にする。 */
    public long createRequest(long applicantId, RequestStatus status, EmployeeRole pendingRole, Item... items) {
        long total = 0;
        for (Item item : items) {
            total += item.unitPrice() * item.quantity();
        }
        long id = jdbc.queryForObject(
                "INSERT INTO purchase_request (applicant_id, total_amount, status, pending_role) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, applicantId, total, status.name(), pendingRole != null ? pendingRole.name() : null);
        for (Item item : items) {
            jdbc.update("INSERT INTO purchase_request_item (request_id, unit_price, quantity) VALUES (?, ?, ?)",
                    id, item.unitPrice(), item.quantity());
        }
        return id;
    }

    public long createProduct(String name, int price) {
        return jdbc.queryForObject(
                "INSERT INTO products (name, price, image_url) VALUES (?, ?, 'https://example.com/test.png') RETURNING id",
                Long.class, name, price);
    }

    public void createInventory(long productId, int stock, int reserved) {
        jdbc.update("INSERT INTO inventories (product_id, stock_quantity, reserved_quantity) VALUES (?, ?, ?)",
                productId, stock, reserved);
    }

    public int getReservedQuantity(long productId) {
        return jdbc.queryForObject("SELECT reserved_quantity FROM inventories WHERE product_id = ?",
                Integer.class, productId);
    }

    public int invoiceCount(long subscriptionId, String month) {
        return jdbc.queryForObject("SELECT count(*) FROM invoices WHERE subscription_id = ? AND billing_month = ?",
                Integer.class, subscriptionId, month);
    }
}
