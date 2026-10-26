package com.example.ecsite.reservation.entity;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "courses")
public class Course {
    /** 受付の締め切りは、第 1 回の開始のこの時間前まで。 */
    private static final Duration CUTOFF = Duration.ofHours(1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseKind kind;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private int capacity;

    /** 有効な予約の件数。条件付き UPDATE でだけ増減させる想定で、エンティティの保存では更新しない。 */
    @Column(name = "reserved_count", nullable = false, updatable = false)
    private int reservedCount;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sessionNo ASC")
    private List<CourseSession> sessions = new ArrayList<>();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public CourseKind getKind() { return kind; }
    public void setKind(CourseKind kind) { this.kind = kind; }
    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public int getReservedCount() { return reservedCount; }
    public void setReservedCount(int reservedCount) { this.reservedCount = reservedCount; }
    public List<CourseSession> getSessions() { return sessions; }

    public int remainingSeats() {
        return capacity - reservedCount;
    }

    /** 第 1 回（session_no = 1）の開始日時。 */
    public OffsetDateTime firstSessionStart() {
        return sessions.stream()
                .filter(s -> s.getSessionNo() == 1)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("第 1 回がない講座です"))
                .getStartsAt();
    }

    /** 残席が 1 以上で、かつ now が第 1 回の開始の 1 時間前以前（ちょうどを含む）なら受け付ける。 */
    public boolean isOpenFor(OffsetDateTime now) {
        return remainingSeats() >= 1 && !now.isAfter(firstSessionStart().minus(CUTOFF));
    }
}
