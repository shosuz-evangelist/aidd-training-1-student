import React, { useState } from "react";
import axios, { isAxiosError } from "axios";
import { Link } from "react-router";

interface SessionInput {
  startsAt: string;
  endsAt: string;
  room: string;
  instructor: string;
}

const emptySession = (): SessionInput => ({ startsAt: "", endsAt: "", room: "", instructor: "" });

const box: React.CSSProperties = { background: "#fff", borderRadius: 8, boxShadow: "0 2px 8px #0001", padding: 16, marginBottom: 16 };
const field: React.CSSProperties = { display: "flex", flexDirection: "column", gap: 4, fontSize: 14 };
const row: React.CSSProperties = { display: "flex", flexWrap: "wrap", gap: 16, marginBottom: 12 };

// datetime-local の値を、日本時間 (+09:00) の OffsetDateTime として送る文字列にする
const toOffset = (v: string): string => {
  if (!v) return v;
  return v.length === 16 ? `${v}:00+09:00` : `${v}+09:00`;
};

const CourseRegisterPage: React.FC = () => {
  const [name, setName] = useState("");
  const [category, setCategory] = useState("");
  const [kind, setKind] = useState<"SINGLE" | "SERIES">("SINGLE");
  const [price, setPrice] = useState("");
  const [capacity, setCapacity] = useState("");
  const [sessions, setSessions] = useState<SessionInput[]>([emptySession()]);
  const [success, setSuccess] = useState("");
  const [error, setError] = useState<{ code?: string; message: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const updateSession = (i: number, patch: Partial<SessionInput>) =>
    setSessions(sessions.map((s, idx) => (idx === i ? { ...s, ...patch } : s)));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setSuccess("");
    setError(null);
    try {
      await axios.post("/api/reservation/courses", {
        name,
        category,
        kind,
        price: Number(price),
        capacity: Number(capacity),
        sessions: sessions.map((s) => ({ ...s, startsAt: toOffset(s.startsAt), endsAt: toOffset(s.endsAt) })),
      });
      setSuccess(`「${name}」を登録しました`);
      setName("");
      setCategory("");
      setKind("SINGLE");
      setPrice("");
      setCapacity("");
      setSessions([emptySession()]);
    } catch (err) {
      if (isAxiosError(err) && err.response?.data?.message) {
        setError({ code: err.response.data.code, message: err.response.data.message });
      } else {
        setError({ message: "講座を登録できませんでした" });
      }
    }
    setBusy(false);
  };

  return (
    <div style={{ maxWidth: 900, margin: "40px auto" }}>
      <h2>講座登録</h2>
      <div style={{ marginBottom: 16 }}>
        <Link to="/courses">講座一覧へ戻る</Link>
      </div>
      {success && <div style={{ marginBottom: 16, color: "#2e7d32" }}>{success}</div>}
      {error && (
        <div style={{ marginBottom: 16, color: "#d32f2f" }}>
          {error.code && <strong>{error.code}</strong>} {error.message}
        </div>
      )}
      <form onSubmit={submit}>
        <div style={box}>
          <div style={row}>
            <label style={field}>
              講座名
              <input value={name} onChange={(e) => setName(e.target.value)} />
            </label>
            <label style={field}>
              カテゴリ
              <input value={category} onChange={(e) => setCategory(e.target.value)} />
            </label>
            <label style={field}>
              種類
              <select value={kind} onChange={(e) => setKind(e.target.value as "SINGLE" | "SERIES")}>
                <option value="SINGLE">単発講座</option>
                <option value="SERIES">連続講座</option>
              </select>
            </label>
            <label style={field}>
              受講料（円）
              <input type="number" value={price} onChange={(e) => setPrice(e.target.value)} />
            </label>
            <label style={field}>
              定員
              <input type="number" value={capacity} onChange={(e) => setCapacity(e.target.value)} />
            </label>
          </div>
        </div>
        {sessions.map((s, i) => (
          <div key={i} style={box}>
            <div style={{ fontWeight: 700, marginBottom: 8 }}>第 {i + 1} 回</div>
            <div style={row}>
              <label style={field}>
                開始日時
                <input type="datetime-local" value={s.startsAt} onChange={(e) => updateSession(i, { startsAt: e.target.value })} />
              </label>
              <label style={field}>
                終了日時
                <input type="datetime-local" value={s.endsAt} onChange={(e) => updateSession(i, { endsAt: e.target.value })} />
              </label>
              <label style={field}>
                教室
                <input value={s.room} onChange={(e) => updateSession(i, { room: e.target.value })} />
              </label>
              <label style={field}>
                講師
                <input value={s.instructor} onChange={(e) => updateSession(i, { instructor: e.target.value })} />
              </label>
            </div>
            <button type="button" onClick={() => setSessions(sessions.filter((_, idx) => idx !== i))}>
              この回を削除
            </button>
          </div>
        ))}
        <div style={{ display: "flex", gap: 16 }}>
          <button type="button" onClick={() => setSessions([...sessions, emptySession()])}>
            回を追加
          </button>
          <button type="submit" disabled={busy}>
            登録する
          </button>
        </div>
      </form>
    </div>
  );
};

export default CourseRegisterPage;
