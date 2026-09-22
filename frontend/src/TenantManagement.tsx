import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from "react";
import {
  addPropertyMember,
  createTenant,
  getPropertyMembers,
  getTenants,
  type NewTenant,
  type Property,
  type Tenant,
} from "./api";

interface Props {
  token: string;
  properties: Property[];
  onSessionExpired: () => void;
}

export default function TenantManagement({
  token,
  properties,
  onSessionExpired,
}: Props) {
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [memberships, setMemberships] = useState<Record<string, string[]>>({});
  const [selectedProperties, setSelectedProperties] = useState<
    Record<string, string>
  >({});
  const [loading, setLoading] = useState(true);
  const [busyTenant, setBusyTenant] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const latestLoad = useRef(0);

  const handleError = useCallback(
    (cause: unknown) => {
      const message =
        cause instanceof Error ? cause.message : "Något gick fel.";
      if (message.includes("session har gått ut")) onSessionExpired();
      else setError(message);
    },
    [onSessionExpired],
  );

  const load = useCallback(async () => {
    const loadId = ++latestLoad.current;
    setLoading(true);
    setError("");
    try {
      const [users, propertyMembers] = await Promise.all([
        getTenants(token),
        Promise.all(
          properties.map((property) => getPropertyMembers(token, property.id)),
        ),
      ]);
      const byTenant: Record<string, string[]> = {};
      propertyMembers.forEach((members, index) => {
        members.forEach((member) => {
          (byTenant[member.userId] ??= []).push(properties[index].id);
        });
      });
      if (loadId === latestLoad.current) {
        setTenants(users);
        setMemberships(byTenant);
      }
    } catch (cause) {
      if (loadId === latestLoad.current) handleError(cause);
    } finally {
      if (loadId === latestLoad.current) setLoading(false);
    }
  }, [token, properties, handleError]);

  useEffect(() => {
    void load();
  }, [load]);

  async function linkTenant(tenant: Tenant) {
    const propertyId = selectedProperties[tenant.id];
    if (!propertyId) return;
    setBusyTenant(tenant.id);
    setError("");
    setNotice("");
    try {
      await addPropertyMember(token, propertyId, tenant.id);
      setSelectedProperties((previous) => ({ ...previous, [tenant.id]: "" }));
      setNotice(
        `${tenant.firstName} ${tenant.lastName} är kopplad till fastigheten.`,
      );
      await load();
    } catch (cause) {
      handleError(cause);
    } finally {
      setBusyTenant("");
    }
  }

  async function onCreated() {
    setShowCreate(false);
    setNotice("Hyresgästen har skapats och kopplats till fastigheten.");
    await load();
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">ADMINISTRATION</div>
          <h1>Hyresgäster</h1>
          <p>Skapa konton och koppla hyresgäster till fastigheter.</p>
        </div>
        <button className="button primary" onClick={() => setShowCreate(true)}>
          ＋ Ny hyresgäst
        </button>
      </div>
      {error && (
        <div className="alert error" role="alert">
          {error}
          <button onClick={() => void load()}>Försök igen</button>
        </div>
      )}
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
            <h2>Alla hyresgäster</h2>
            <p>Välj en fastighet för att lägga till en befintlig hyresgäst.</p>
          </div>
        </div>
        {loading ? (
          <div className="empty">Laddar hyresgäster…</div>
        ) : !tenants.length ? (
          <div className="empty">
            Inga hyresgäster ännu. Skapa den första med knappen ovan.
          </div>
        ) : (
          <div className="tenant-list">
            {tenants.map((tenant) => {
              const linkedIds = memberships[tenant.id] ?? [];
              const available = properties.filter(
                (property) =>
                  property.active && !linkedIds.includes(property.id),
              );
              return (
                <article className="tenant-row" key={tenant.id}>
                  <div className="tenant-avatar">
                    {tenant.firstName[0]?.toUpperCase()}
                    {tenant.lastName[0]?.toUpperCase()}
                  </div>
                  <div className="tenant-details">
                    <strong>
                      {tenant.firstName} {tenant.lastName}
                    </strong>
                    <span>
                      {tenant.email}
                      {!tenant.active && " · Inaktiv"}
                    </span>
                    <div className="tenant-properties">
                      {linkedIds.length ? (
                        linkedIds.map((id) => (
                          <span className="tenant-property" key={id}>
                            {properties.find((property) => property.id === id)
                              ?.name ?? "Okänd fastighet"}
                          </span>
                        ))
                      ) : (
                        <span className="tenant-unlinked">
                          Ingen fastighet kopplad
                        </span>
                      )}
                    </div>
                  </div>
                  {tenant.active && available.length > 0 && (
                    <div className="tenant-link">
                      <select
                        aria-label={`Fastighet för ${tenant.firstName} ${tenant.lastName}`}
                        value={selectedProperties[tenant.id] || ""}
                        onChange={(event) =>
                          setSelectedProperties((previous) => ({
                            ...previous,
                            [tenant.id]: event.target.value,
                          }))
                        }
                      >
                        <option value="">Välj fastighet</option>
                        {available.map((property) => (
                          <option value={property.id} key={property.id}>
                            {property.name}
                          </option>
                        ))}
                      </select>
                      <button
                        className="button secondary"
                        onClick={() => void linkTenant(tenant)}
                        disabled={
                          !selectedProperties[tenant.id] ||
                          busyTenant === tenant.id
                        }
                      >
                        {busyTenant === tenant.id ? "Kopplar…" : "Koppla"}
                      </button>
                    </div>
                  )}
                </article>
              );
            })}
          </div>
        )}
      </section>
      {showCreate && (
        <CreateTenantDialog
          token={token}
          properties={properties.filter((property) => property.active)}
          onClose={() => setShowCreate(false)}
          onCreated={onCreated}
          onSessionExpired={onSessionExpired}
        />
      )}
    </>
  );
}

function CreateTenantDialog({
  token,
  properties,
  onClose,
  onCreated,
  onSessionExpired,
}: {
  token: string;
  properties: Property[];
  onClose: () => void;
  onCreated: () => void;
  onSessionExpired: () => void;
}) {
  const [form, setForm] = useState<NewTenant>({
    email: "",
    password: "",
    firstName: "",
    lastName: "",
    role: "TENANT",
  });
  const [propertyId, setPropertyId] = useState(properties[0]?.id || "");
  const [createdTenant, setCreatedTenant] = useState<Tenant | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    let accountCreated = createdTenant !== null;
    try {
      const tenant =
        createdTenant ??
        (await createTenant(token, {
          ...form,
          email: form.email.trim(),
          firstName: form.firstName.trim(),
          lastName: form.lastName.trim(),
        }));
      setCreatedTenant(tenant);
      accountCreated = true;
      await addPropertyMember(token, propertyId, tenant.id);
      onCreated();
    } catch (cause) {
      const message =
        cause instanceof Error
          ? cause.message
          : "Det gick inte att skapa hyresgästen.";
      if (message.includes("session har gått ut")) onSessionExpired();
      else
        setError(
          accountCreated
            ? `Kontot skapades, men kopplingen misslyckades: ${message}`
            : message,
        );
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
        aria-labelledby="create-tenant-title"
      >
        <div className="modal-head">
          <div>
            <div className="eyebrow">NY HYRESGÄST</div>
            <h2 id="create-tenant-title">Skapa hyresgäst</h2>
            <p>Skapa ett konto och välj vilken fastighet personen tillhör.</p>
          </div>
          <button className="close-button" onClick={onClose} aria-label="Stäng">
            ×
          </button>
        </div>
        <form onSubmit={submit}>
          <div className="form-grid">
            <div>
              <label htmlFor="tenant-first-name">Förnamn</label>
              <input
                id="tenant-first-name"
                maxLength={100}
                value={form.firstName}
                onChange={(event) =>
                  setForm({ ...form, firstName: event.target.value })
                }
                disabled={!!createdTenant}
                required
              />
            </div>
            <div>
              <label htmlFor="tenant-last-name">Efternamn</label>
              <input
                id="tenant-last-name"
                maxLength={100}
                value={form.lastName}
                onChange={(event) =>
                  setForm({ ...form, lastName: event.target.value })
                }
                disabled={!!createdTenant}
                required
              />
            </div>
          </div>
          <label htmlFor="tenant-email">E-postadress</label>
          <input
            id="tenant-email"
            type="email"
            maxLength={255}
            autoComplete="off"
            value={form.email}
            onChange={(event) =>
              setForm({ ...form, email: event.target.value })
            }
            disabled={!!createdTenant}
            required
          />
          <label htmlFor="tenant-password">Tillfälligt lösenord</label>
          <input
            id="tenant-password"
            type="password"
            minLength={8}
            maxLength={72}
            autoComplete="new-password"
            value={form.password}
            onChange={(event) =>
              setForm({ ...form, password: event.target.value })
            }
            disabled={!!createdTenant}
            required
          />
          <p className="field-hint">
            Lämna lösenordet till hyresgästen på ett säkert sätt.
          </p>
          <label htmlFor="tenant-property">Fastighet</label>
          <select
            id="tenant-property"
            value={propertyId}
            onChange={(event) => setPropertyId(event.target.value)}
            required
          >
            <option value="" disabled>
              Välj fastighet
            </option>
            {properties.map((property) => (
              <option key={property.id} value={property.id}>
                {property.name}
              </option>
            ))}
          </select>
          {!properties.length && (
            <p className="field-hint">
              Skapa en aktiv fastighet innan du lägger till en hyresgäst.
            </p>
          )}
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
            <button
              className="button primary"
              type="submit"
              disabled={busy || !properties.length}
            >
              {busy
                ? "Sparar…"
                : createdTenant
                  ? "Försök koppla igen"
                  : "Skapa och koppla"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
