import React, { useEffect, useState } from "react";
import axios, { isAxiosError } from "axios";
import { Link } from "react-router";

interface Session {
  startsAt: string;
  endsAt: string;
  room: string;
  instructor: string;
}

interface Course {
  id: number;
  name: string;
  category: string;
  kind: "SINGLE" | "SERIES";
  sessions: Session[];
  price: number;
  remaining: number;
  seatLabel: string;
  reservable: boolean;
}

const box: React.CSSProperties = { background: "#fff", borderRadius: 8, boxShadow: "0 2px 8px #0001", padding: 16, marginBottom: 16 };
const yen = (n: number) => `${n.toLocaleString()}円`;
const customerId = (): number | undefined => JSON.parse(localStorage.getItem("token") || "{}").id;

// +09:00 の OffsetDateTime を日本時間の表示にする
const dateTime = new Intl.DateTimeFormat("ja-JP", {
  timeZone: "Asia/Tokyo",
  month: "numeric",
  day: "numeric",
  weekday: "short",
  hour: "2-digit",
  minute: "2-digit",
});
const timeOnly = new Intl.DateTimeFormat("ja-JP", {
  timeZone: "Asia/Tokyo",
  hour: "2-digit",
  minute: "2-digit",
});
const period = (s: Session) => `${dateTime.format(new Date(s.startsAt))} 〜 ${timeOnly.format(new Date(s.endsAt))}`;

const CourseListPage: React.FC = () => {
  const [courses, setCourses] = useState<Course[]>([]);
  const [message, setMessage] = useState("");
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = async () => {
    const res = await axios.get("/api/reservation/courses");
    setCourses(res.data);
  };

  useEffect(() => {
    load().catch(() => setMessage("講座を取得できませんでした"));
  }, []);

  const reserve = async (course: Course) => {
    setBusyId(course.id);
    try {
      await axios.post("/api/reservation/reservations", { customerId: customerId(), courseId: course.id });
      setMessage(`「${course.name}」を予約しました`);
    } catch (e) {
      setMessage(isAxiosError(e) ? (e.response?.data?.message ?? "予約できませんでした") : "予約できませんでした");
    }
    try {
      await load();
    } catch {
      setMessage("講座を取得できませんでした");
    }
    setBusyId(null);
  };

  return (
    <div style={{ maxWidth: 900, margin: "40px auto" }}>
      <h2>講座一覧</h2>
      <div style={{ marginBottom: 16 }}>
        <Link to="/courses/new">講座を登録する（担当者向け）</Link>
      </div>
      {message && <div style={{ marginBottom: 16 }}>{message}</div>}
      {courses.map((course) => (
        <div key={course.id} style={box}>
          <div style={{ fontWeight: 700 }}>
            {course.name}
            <span style={{ marginLeft: 8, fontSize: 13, color: "#555", fontWeight: 400 }}>
              {course.category}・{course.kind === "SERIES" ? "連続講座" : "単発講座"}
            </span>
          </div>
          <ul>
            {course.sessions.map((s, i) => (
              <li key={i}>
                {course.kind === "SERIES" ? `第 ${i + 1} 回 ` : ""}
                {period(s)}（{s.room}・講師 {s.instructor}）
              </li>
            ))}
          </ul>
          <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
            <span style={{ color: "#1976d2", fontWeight: 700 }}>{yen(course.price)}</span>
            <span style={{ color: course.remaining > 0 ? "#555" : "#d32f2f" }}>{course.seatLabel}</span>
            <button disabled={!course.reservable || busyId !== null} onClick={() => reserve(course)}>
              予約する
            </button>
          </div>
        </div>
      ))}
    </div>
  );
};

export default CourseListPage;
