import React, { useEffect, useState } from "react";
import axios, { isAxiosError } from "axios";

interface Session {
  startsAt: string;
  endsAt: string;
  room: string;
  instructor: string;
}

interface Reservation {
  id: number;
  courseId: number;
  courseName: string;
  sessions: Session[];
  price: number;
  cancelFee: number;
  cancellable: boolean;
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

const ReservationListPage: React.FC = () => {
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [message, setMessage] = useState("");
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = async () => {
    const res = await axios.get("/api/reservation/reservations", { params: { customerId: customerId() } });
    setReservations(res.data);
  };

  useEffect(() => {
    load().catch(() => setMessage("予約を取得できませんでした"));
  }, []);

  const cancel = async (r: Reservation) => {
    if (!window.confirm(`「${r.courseName}」の予約をキャンセルします。キャンセル料は ${yen(r.cancelFee)} です。よろしいですか？`)) {
      return;
    }
    setBusyId(r.id);
    try {
      const res = await axios.post(`/api/reservation/reservations/${r.id}/cancel`, { customerId: customerId() });
      setMessage(`「${r.courseName}」をキャンセルしました（キャンセル料 ${yen(res.data.cancelFee)}）`);
    } catch (e) {
      setMessage(isAxiosError(e) ? (e.response?.data?.message ?? "キャンセルできませんでした") : "キャンセルできませんでした");
    }
    try {
      await load();
    } catch {
      setMessage("予約を取得できませんでした");
    }
    setBusyId(null);
  };

  return (
    <div style={{ maxWidth: 900, margin: "40px auto" }}>
      <h2>予約一覧</h2>
      {message && <div style={{ marginBottom: 16 }}>{message}</div>}
      {reservations.map((r) => (
        <div key={r.id} style={box}>
          <div style={{ fontWeight: 700 }}>{r.courseName}</div>
          <ul>
            {r.sessions.map((s, i) => (
              <li key={i}>
                {r.sessions.length > 1 ? `第 ${i + 1} 回 ` : ""}
                {period(s)}（{s.room}・講師 {s.instructor}）
              </li>
            ))}
          </ul>
          <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
            <span style={{ color: "#1976d2", fontWeight: 700 }}>{yen(r.price)}</span>
            <span>キャンセル料 {yen(r.cancelFee)}</span>
            {r.cancellable ? (
              <button disabled={busyId !== null} onClick={() => cancel(r)}>
                キャンセルする
              </button>
            ) : (
              <span style={{ color: "#d32f2f" }}>開始後はキャンセルできません</span>
            )}
          </div>
        </div>
      ))}
      {reservations.length === 0 && <div>予約はありません</div>}
    </div>
  );
};

export default ReservationListPage;
