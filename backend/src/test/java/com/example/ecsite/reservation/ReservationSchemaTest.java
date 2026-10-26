package com.example.ecsite.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V2 のテーブルと制約が、テスト用データベースに適用されていることを確かめる。 */
@SpringBootTest
class ReservationSchemaTest {
    @Autowired
    JdbcTemplate jdbc;

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void tablesExist() {
        assertThat(count("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
                + "AND table_name IN ('courses', 'course_sessions', 'reservations')")).isEqualTo(3);
    }

    @Test
    void indexesExist() {
        assertThat(count("SELECT count(*) FROM pg_indexes WHERE tablename = 'course_sessions' "
                + "AND indexdef LIKE '%(room, starts_at)%'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM pg_indexes WHERE tablename = 'course_sessions' "
                + "AND indexdef LIKE '%(instructor, starts_at)%'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM pg_indexes WHERE tablename = 'reservations' "
                + "AND indexdef LIKE '%UNIQUE%(customer_id, course_id)%' AND indexdef LIKE '%ACTIVE%'")).isEqualTo(1);
    }

    @Test
    void courseChecksRejectInvalidRows() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO courses (name, category, kind, price, capacity, reserved_count) "
                + "VALUES ('x', 'c', 'OTHER', 0, 1, 0)")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO courses (name, category, kind, price, capacity, reserved_count) "
                + "VALUES ('x', 'c', 'SINGLE', 0, 0, 0)")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO courses (name, category, kind, price, capacity, reserved_count) "
                + "VALUES ('x', 'c', 'SINGLE', 0, 2, 3)")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void priceHasNoRangeCheck() {
        // price に範囲の制約は付けない（未決定）。負の値も入る
        Long id = jdbc.queryForObject("INSERT INTO courses (name, category, kind, price, capacity) "
                + "VALUES ('schema-test', 'c', 'SINGLE', -1, 1) RETURNING id", Long.class);
        try {
            assertThat(id).isNotNull();
        } finally {
            jdbc.update("DELETE FROM courses WHERE id = ?", id);
        }
    }
}
