import { useState, type FormEvent } from "react";
import { createTechnician, type NewTechnician, type Technician } from "./api";

interface Props {
  token: string;
  technicians: Technician[];
  loading: boolean;
  onCreated: () => void;
  onSessionExpired: () => void;
}

export default function TechnicianManagement({
  token,
  technicians,
  loading,
  onCreated,
  onSessionExpired,
}: Props) {
  const [showCreate, setShowCreate] = useState(false);
  const [notice, setNotice] = useState("");

  function created() {
    setShowCreate(false);
    setNotice("Teknikern har skapats och kan nu tilldelas arbetsorder.");
    onCreated();
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">ADMINISTRATION</div>
          <h1>Tekniker</h1>
          <p>Skapa tekniker och tilldela dem arbetsorder.</p>
        </div>
        <button className="button primary" onClick={() => setShowCreate(true)}>
          ＋ Ny tekniker
        </button>
      </div>
      {notice && (
        <div className="alert success" role="status">
          {notice}
          <button aria-label="Stäng meddelande" onClick={() => setNotice("")}>
            ×
          </button>
        </div>
      )}
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Alla tekniker</h2>
            <p>Aktiva tekniker kan tilldelas nya och pågående arbetsorder.</p>
          </div>
        </div>
        {loading ? (
          <div className="empty">Laddar tekniker…</div>
        ) : !technicians.length ? (
          <div className="empty">
            Inga tekniker ännu. Skapa den första med knappen ovan.
          </div>
        ) : (
          <div className="tenant-list">
            {technicians.map((technician) => (
              <article className="tenant-row" key={technician.id}>
                <div className="tenant-avatar">
                  {technician.firstName[0]?.toUpperCase()}
                  {technician.lastName[0]?.toUpperCase()}
                </div>
                <div className="tenant-details">
                  <strong>
                    {technician.firstName} {technician.lastName}
                  </strong>
                  <span>{technician.email}</span>
                </div>
                <span
                  className={
                    technician.active
                      ? "property-state"
                      : "property-state inactive"
                  }
                >
                  {technician.active ? "Aktiv" : "Inaktiv"}
                </span>
              </article>
            ))}
          </div>
        )}
      </section>
      {showCreate && (
        <CreateTechnicianDialog
          token={token}
          onClose={() => setShowCreate(false)}
          onCreated={created}
          onSessionExpired={onSessionExpired}
        />
      )}
    </>
  );
}

function CreateTechnicianDialog({
  token,
  onClose,
  onCreated,
  onSessionExpired,
}: {
  token: string;
  onClose: () => void;
  onCreated: () => void;
  onSessionExpired: () => void;
}) {
  const [form, setForm] = useState<NewTechnician>({
    email: "",
    password: "",
    firstName: "",
    lastName: "",
    role: "TECHNICIAN",
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await createTechnician(token, {
        ...form,
        email: form.email.trim(),
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
      });
      onCreated();
    } catch (cause) {
      const message =
        cause instanceof Error
          ? cause.message
          : "Det gick inte att skapa teknikern.";
      if (message.includes("session har gått ut")) onSessionExpired();
      else setError(message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      className="modal-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-technician-title"
      >
        <div className="modal-head">
          <div>
            <div className="eyebrow">NY TEKNIKER</div>
            <h2 id="create-technician-title">Skapa tekniker</h2>
            <p>Teknikern kan logga in och arbeta med tilldelade ärenden.</p>
          </div>
          <button className="close-button" onClick={onClose} aria-label="Stäng">
            ×
          </button>
        </div>
        <form onSubmit={submit}>
          <div className="form-grid">
            <div>
              <label htmlFor="technician-first-name">Förnamn</label>
              <input
                id="technician-first-name"
                maxLength={100}
                value={form.firstName}
                onChange={(event) =>
                  setForm({ ...form, firstName: event.target.value })
                }
                required
              />
            </div>
            <div>
              <label htmlFor="technician-last-name">Efternamn</label>
              <input
                id="technician-last-name"
                maxLength={100}
                value={form.lastName}
                onChange={(event) =>
                  setForm({ ...form, lastName: event.target.value })
                }
                required
              />
            </div>
          </div>
          <label htmlFor="technician-email">E-postadress</label>
          <input
            id="technician-email"
            type="email"
            maxLength={255}
            autoComplete="off"
            value={form.email}
            onChange={(event) =>
              setForm({ ...form, email: event.target.value })
            }
            required
          />
          <label htmlFor="technician-password">Tillfälligt lösenord</label>
          <input
            id="technician-password"
            type="password"
            minLength={8}
            maxLength={72}
            autoComplete="new-password"
            value={form.password}
            onChange={(event) =>
              setForm({ ...form, password: event.target.value })
            }
            required
          />
          <p className="field-hint">
            Lämna lösenordet till teknikern på ett säkert sätt.
          </p>
          {error && (
            <div className="form-error" role="alert">
              {error}
            </div>
          )}
          <div className="modal-actions">
            <button
              className="button secondary"
              type="button"
              onClick={onClose}
            >
              Avbryt
            </button>
            <button className="button primary" type="submit" disabled={busy}>
              {busy ? "Sparar…" : "Skapa tekniker"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
