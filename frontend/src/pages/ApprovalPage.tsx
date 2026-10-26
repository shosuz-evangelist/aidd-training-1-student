import React, { useEffect, useState } from "react";
import axios, { isAxiosError } from "axios";

interface Employee {
  id: number;
  name: string;
  department: string;
  role: string;
}

interface PurchaseRequest {
  id: number;
  applicantId: number;
  totalAmount: number;
  status: string;
  pendingRole: string | null;
  canApprove: boolean;
  canReturn: boolean;
  canResubmit: boolean;
  canOrder: boolean;
}

interface History {
  id: number;
  actorId: number;
  onBehalfOfId: number | null;
  action: string;
  actedAt: string;
  reason: string | null;
}

const ROLE_LABELS: Record<string, string> = {
  STAFF: "担当者",
  SECTION_MANAGER: "課長",
  DEPARTMENT_MANAGER: "部長",
  EXECUTIVE: "役員",
  PURCHASING: "購買部",
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: "承認待ち",
  RETURNED: "差し戻し",
  APPROVED: "承認済み",
  ORDERED: "発注済み",
};

const ACTION_LABELS: Record<string, string> = {
  APPROVE: "承認",
  RETURN: "差し戻し",
  RESUBMIT: "出し直し",
  ORDER: "発注",
};

interface ItemRow {
  unitPrice: string;
  quantity: string;
}

const toNumberOrNull = (v: string): number | null => (v.trim() === "" ? null : Number(v));

const customerId = (): number | undefined => JSON.parse(localStorage.getItem("token") || "{}").id;

const errorMessage = (e: unknown, fallback: string): string =>
  isAxiosError(e) ? (e.response?.data?.message ?? fallback) : fallback;

const ApprovalPage: React.FC = () => {
  const [employee, setEmployee] = useState<Employee | null>(null);
  const [items, setItems] = useState<PurchaseRequest[]>([]);
  const [histories, setHistories] = useState<Record<number, History[]>>({});
  const [openId, setOpenId] = useState<number | null>(null);
  const [message, setMessage] = useState("");
  const [rows, setRows] = useState<ItemRow[]>([{ unitPrice: "", quantity: "" }]);
  const [delegateId, setDelegateId] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");

  const load = async () => {
    try {
      const res = await axios.get(`/api/approval/requests?viewerId=${customerId()}`);
      setItems(res.data);
    } catch (e) {
      setItems([]);
      setMessage(errorMessage(e, "承認一覧を取得できませんでした"));
    }
  };

  const loadHistory = async (id: number) => {
    try {
      const res = await axios.get(`/api/approval/requests/${id}/history`);
      setHistories((prev) => ({ ...prev, [id]: res.data }));
    } catch (e) {
      setMessage(errorMessage(e, "履歴を取得できませんでした"));
    }
  };

  useEffect(() => {
    axios
      .get(`/api/approval/employees/${customerId()}`)
      .then((res) => setEmployee(res.data))
      .catch((e) => setMessage(errorMessage(e, "社員の情報を取得できませんでした")));
    load();
  }, []);

  const operate = async (id: number, operation: string, body: object, doneMessage: string) => {
    try {
      await axios.post(`/api/approval/requests/${id}/${operation}`, body);
      setMessage(doneMessage);
    } catch (e) {
      setMessage(errorMessage(e, "操作できませんでした"));
    }
    await load();
    if (openId === id) await loadHistory(id);
  };

  const updateRow = (index: number, key: keyof ItemRow, value: string) =>
    setRows((prev) => prev.map((row, i) => (i === index ? { ...row, [key]: value } : row)));

  const submitRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await axios.post("/api/approval/requests", {
        applicantId: customerId(),
        items: rows.map((row) => ({ unitPrice: toNumberOrNull(row.unitPrice), quantity: toNumberOrNull(row.quantity) })),
      });
      setMessage(`申請 ${res.data.id} を出しました`);
      setRows([{ unitPrice: "", quantity: "" }]);
      await load();
    } catch (err) {
      setMessage(errorMessage(err, "申請できませんでした"));
    }
  };

  const submitDelegation = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await axios.post("/api/approval/delegations", {
        delegatorId: customerId(),
        delegateId: toNumberOrNull(delegateId),
        startDate: startDate || null,
        endDate: endDate || null,
      });
      setMessage("代理を設定しました");
      setDelegateId("");
      setStartDate("");
      setEndDate("");
    } catch (err) {
      setMessage(errorMessage(err, "代理を設定できませんでした"));
    }
  };

  const handleReturn = (r: PurchaseRequest) => {
    const reason = window.prompt("差し戻しの理由を入力してください");
    if (reason === null) return;
    operate(r.id, "return", { actorId: customerId(), reason }, `申請 ${r.id} を差し戻しました`);
  };

  const toggleHistory = (id: number) => {
    if (openId === id) {
      setOpenId(null);
      return;
    }
    setOpenId(id);
    loadHistory(id);
  };

  return (
    <div
      style={{ maxWidth: 960, margin: "40px auto", background: "#fff", padding: 32, borderRadius: 8, boxShadow: "0 2px 8px #0001" }}
    >
      <h2>承認一覧</h2>
      {employee && (
        <div style={{ marginBottom: 16 }}>
          {employee.name}（{employee.department} {ROLE_LABELS[employee.role] ?? employee.role}）
        </div>
      )}
      {message && <div style={{ marginBottom: 16 }}>{message}</div>}
      <form onSubmit={submitRequest} style={{ marginBottom: 24 }}>
        <h3>購買申請</h3>
        {rows.map((row, i) => (
          <div key={i} style={{ marginBottom: 8 }}>
            <input
              type="number"
              placeholder="単価"
              aria-label={`明細 ${i + 1} の単価`}
              value={row.unitPrice}
              onChange={(e) => updateRow(i, "unitPrice", e.target.value)}
            />{" "}
            <input
              type="number"
              placeholder="数量"
              aria-label={`明細 ${i + 1} の数量`}
              value={row.quantity}
              onChange={(e) => updateRow(i, "quantity", e.target.value)}
            />{" "}
            <button type="button" onClick={() => setRows((prev) => prev.filter((_, j) => j !== i))}>
              行を削除
            </button>
          </div>
        ))}
        <button type="button" onClick={() => setRows((prev) => [...prev, { unitPrice: "", quantity: "" }])}>
          行を追加
        </button>{" "}
        <button type="submit">申請する</button>
      </form>
      <form onSubmit={submitDelegation} style={{ marginBottom: 24 }}>
        <h3>代理の設定</h3>
        <input
          type="number"
          placeholder="代理人の ID"
          aria-label="代理人の ID"
          value={delegateId}
          onChange={(e) => setDelegateId(e.target.value)}
        />{" "}
        <label>
          開始日 <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        </label>{" "}
        <label>
          終了日 <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
        </label>{" "}
        <button type="submit">代理を設定する</button>
      </form>
      {items.length === 0 ? (
        <div>申請はありません</div>
      ) : (
        <table style={{ width: "100%" }}>
          <thead>
            <tr>
              <th>ID</th>
              <th>申請者</th>
              <th>金額</th>
              <th>状態</th>
              <th>承認待ちの役割</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {items.map((r) => (
              <React.Fragment key={r.id}>
                <tr>
                  <td>{r.id}</td>
                  <td>{r.applicantId}</td>
                  <td>{r.totalAmount.toLocaleString()}円</td>
                  <td>{STATUS_LABELS[r.status] ?? r.status}</td>
                  <td>{r.pendingRole ? (ROLE_LABELS[r.pendingRole] ?? r.pendingRole) : ""}</td>
                  <td>
                    <button
                      disabled={!r.canApprove}
                      onClick={() => operate(r.id, "approve", { actorId: customerId() }, `申請 ${r.id} を承認しました`)}
                    >
                      承認
                    </button>{" "}
                    <button disabled={!r.canReturn} onClick={() => handleReturn(r)}>
                      差し戻し
                    </button>{" "}
                    <button
                      disabled={!r.canResubmit}
                      onClick={() => operate(r.id, "resubmit", { actorId: customerId() }, `申請 ${r.id} を出し直しました`)}
                    >
                      出し直し
                    </button>{" "}
                    <button
                      disabled={!r.canOrder}
                      onClick={() => operate(r.id, "order", { actorId: customerId() }, `申請 ${r.id} を発注しました`)}
                    >
                      発注
                    </button>{" "}
                    <button onClick={() => toggleHistory(r.id)}>{openId === r.id ? "履歴を閉じる" : "履歴"}</button>
                  </td>
                </tr>
                {openId === r.id && (
                  <tr>
                    <td colSpan={6} style={{ background: "#f5f5f5", padding: 12 }}>
                      {(histories[r.id] ?? []).length === 0 ? (
                        <div>履歴はありません</div>
                      ) : (
                        <ul style={{ margin: 0, paddingLeft: 20 }}>
                          {histories[r.id].map((h) => (
                            <li key={h.id}>
                              {new Date(h.actedAt).toLocaleString("ja-JP")} {ACTION_LABELS[h.action] ?? h.action}（操作者 {h.actorId}
                              {h.onBehalfOfId !== null && `、代理元 ${h.onBehalfOfId}`}）{h.reason && ` 理由: ${h.reason}`}
                            </li>
                          ))}
                        </ul>
                      )}
                    </td>
                  </tr>
                )}
              </React.Fragment>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default ApprovalPage;
