package com.example.ecsite.reservation;

import com.example.ecsite.reservation.entity.Course;
import com.example.ecsite.reservation.entity.ReservationStatus;
import com.example.ecsite.reservation.exception.ReservationError;
import com.example.ecsite.reservation.exception.ReservationException;
import com.example.ecsite.reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 要件 4.3 - 同時に予約しても定員を超えない */
class ReservationConcurrencyTest extends ReservationTestBase {
    private static final int THREADS = 10;
    private static final int CAPACITY = 5;

    @Autowired ReservationService service;

    @Test
    void 要件4_3_残席1の講座を同時に予約すると成功は1件でほかはR01になり定員を超えない() throws Exception {
        // 残席 1（定員 5、確保済み 4）。確保済みの 4 席に対応する有効な予約も作り、reserved_count と件数を一致させておく
        Course course = createCourse("同時予約", 3000, CAPACITY, CAPACITY - 1,
                session(1, "2026-11-01T10:00", "A教室", "山田"));
        for (int i = 0; i < CAPACITY - 1; i++) {
            long existing = data.createCustomer("existing" + i + "@example.com");
            createReservation(existing, course, ReservationStatus.ACTIVE);
        }
        // 同じお客様の同じ講座は R03 になるため、スレッドごとに別のお客様を使う
        List<Long> customers = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            customers.add(data.createCustomer("customer" + i + "@example.com"));
        }

        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (long customerId : customers) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("開始の合図が届かなかった");
                    }
                    return service.reserve(customerId, course.getId());
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("全スレッドが待機に入る").isTrue();
            start.countDown();

            int succeeded = 0;
            List<ReservationError> failures = new ArrayList<>();
            List<Throwable> unexpected = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof ReservationException re) {
                        failures.add(re.getError());
                    } else {
                        unexpected.add(e.getCause());
                    }
                }
            }

            assertThat(unexpected).as("ReservationException 以外の例外").isEmpty();
            assertThat(succeeded).isEqualTo(1);
            assertThat(failures).hasSize(THREADS - 1).containsOnly(ReservationError.R01);
        } finally {
            executor.shutdownNow();
        }

        long active = reservationRepository.findAll().stream()
                .filter(r -> r.getCourse().getId().equals(course.getId()) && r.getStatus() == ReservationStatus.ACTIVE)
                .count();
        int reservedCount = reload(course).getReservedCount();
        assertThat(reservedCount).isEqualTo(CAPACITY);
        assertThat(active).isEqualTo(CAPACITY).isEqualTo(reservedCount);
    }
}
