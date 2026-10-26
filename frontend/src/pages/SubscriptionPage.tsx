import React, { useEffect, useState } from "react";
import axios, { isAxiosError } from "axios";

interface Plan {
  code: string;
  name: string;
  monthlyFee: number;
}

interface Period {
  planCode: string;
  planName: string;
  startsOn: string;
  endsOn: string | null;
}

interface Subscription {
  id: number;
  startedOn: string;
  annual: boolean;
  coupon: boolean;
  cancelledOn: string | null;
  periods: Period[];
}

interface Line {
  planCode: string;
  fromDate: string;
  toDate: string;
  days: number;
  amount: number;
}

interface Invoice {
  billingMonth: string;
  lines: Line[];
  subtotal: number;
  annualDiscount: number;
  couponDiscount: number;
  afterDiscount: number;
  tax: number;
  total: number;
}

const box: React.CSSProperties = { background: "#fff", borderRadius: 8, boxShadow: "0 2px 8px #0001", padding: 16, marginBottom: 16 };
const yen = (n: number) => `${n.toLocaleString()}円`;
const customerId = (): number | undefined => JSON.parse(localStorage.getItem("token") || "{}").id;

const SubscriptionPage: React.FC = () => {
  const [plans, setPlans] = useState<Plan[]>([]);
  const [subs, setSubs] = useState<Subscription[]>([]);
  const [invoices, setInvoices] = useState<Record<number, Invoice[]>>({});
  const [message, setMessage] = useState("");
  const [form, setForm] = useState({ planCode: "STANDARD", startedOn: "", annual: false, coupon: false });

  const load = async () => {
    const res = await axios.get(`/api/subscriptions?customerId=${customerId()}`);
    setSubs(res.data);
    const all: Record<number, Invoice[]> = {};
    for (const s of res.data as Subscription[]) {
      all[s.id] = (await axios.get(`/api/subscriptions/${s.id}/invoices`)).data;
    }
    setInvoices(all);
  };

  useEffect(() => {
    axios.get("/api/billing-plans").then((res) => setPlans(res.data));
    load();
  }, []);

  const run = async (call: () => Promise<unknown>, done: string) => {
    try {
      await call();
      setMessage(done);
      await load();
    } catch (e) {
      setMessage(isAxiosError(e) ? (e.response?.data?.message ?? "操作できませんでした") : "操作できませんでした");
    }
  };

  return (
    <div style={{ maxWidth: 900, margin: "40px auto" }}>
      <h2>定期便</h2>
      {message && <div style={{ marginBottom: 16 }}>{message}</div>}

      <div style={box}>
        <h3>申し込む</h3>
        <select value={form.planCode} onChange={(e) => setForm({ ...form, planCode: e.target.value })}>
          {plans.map((p) => (
            <option key={p.code} value={p.code}>
              {p.name}（月額 {yen(p.monthlyFee)}・税抜き）
            </option>
          ))}
        </select>{" "}
        申し込む日 <input type="date" value={form.startedOn} onChange={(e) => setForm({ ...form, startedOn: e.target.value })} />{" "}
        <label>
          <input type="checkbox" checked={form.annual} onChange={(e) => setForm({ ...form, annual: e.target.checked })} /> 年契約（10% 割引）
        </label>{" "}
        <label>
          <input type="checkbox" checked={form.coupon} onChange={(e) => setForm({ ...form, coupon: e.target.checked })} /> 初月クーポン（500 円）
        </label>{" "}
        <button onClick={() => run(() => axios.post("/api/subscriptions", { customerId: customerId(), ...form }), "申し込みました")}>
          申し込む
        </button>
      </div>

      {subs.map((s) => (
        <div key={s.id} style={box}>
          <div style={{ fontWeight: 700 }}>
            契約 #{s.id}（{s.startedOn} から{s.annual ? "・年契約" : ""}
            {s.coupon ? "・クーポン" : ""}
            {s.cancelledOn ? `・${s.cancelledOn} に解約` : ""}）
          </div>
          <ul>
            {s.periods.map((p, i) => (
              <li key={i}>
                {p.planName}: {p.startsOn} 〜 {p.endsOn ?? ""}
              </li>
            ))}
          </ul>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button
              onClick={() => {
                const planCode = window.prompt("変更後のプラン（LIGHT / STANDARD / PREMIUM）") ?? "";
                const effectiveOn = window.prompt("変更日（例: 2026-11-16）") ?? "";
                run(() => axios.post(`/api/subscriptions/${s.id}/change-plan`, { planCode, effectiveOn }), "プランを変えました");
              }}
            >
              プランを変える
            </button>
            <button
              onClick={() => {
                const cancelledOn = window.prompt("解約日（例: 2026-11-10）") ?? "";
                run(() => axios.post(`/api/subscriptions/${s.id}/cancel`, { cancelledOn }), "解約しました");
              }}
            >
              解約する
            </button>
            <button
              onClick={() => {
                const billingMonth = window.prompt("請求する月（例: 2026-10）") ?? "";
                run(() => axios.post(`/api/subscriptions/${s.id}/invoices`, { billingMonth }), "請求書を作りました");
              }}
            >
              請求書を作る（担当者）
            </button>
          </div>
          {(invoices[s.id] ?? []).map((inv) => (
            <table key={inv.billingMonth} style={{ width: "100%", marginTop: 12, borderTop: "1px solid #ddd" }}>
              <tbody>
                <tr>
                  <td colSpan={2} style={{ fontWeight: 700 }}>
                    {inv.billingMonth} の請求書
                  </td>
                </tr>
                {inv.lines.map((l, i) => (
                  <tr key={i}>
                    <td>
                      {l.planCode} {l.fromDate} 〜 {l.toDate}（{l.days} 日）
                    </td>
                    <td style={{ textAlign: "right" }}>{yen(l.amount)}</td>
                  </tr>
                ))}
                <tr><td>小計</td><td style={{ textAlign: "right" }}>{yen(inv.subtotal)}</td></tr>
                <tr><td>年契約の割引</td><td style={{ textAlign: "right" }}>-{yen(inv.annualDiscount)}</td></tr>
                <tr><td>クーポン</td><td style={{ textAlign: "right" }}>-{yen(inv.couponDiscount)}</td></tr>
                <tr><td>消費税（10%）</td><td style={{ textAlign: "right" }}>{yen(inv.tax)}</td></tr>
                <tr><td style={{ fontWeight: 700 }}>請求額</td><td style={{ textAlign: "right", fontWeight: 700 }}>{yen(inv.total)}</td></tr>
              </tbody>
            </table>
          ))}
        </div>
      ))}
    </div>
  );
};

export default SubscriptionPage;
