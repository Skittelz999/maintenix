import {
  useCallback,
  useEffect,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import {
  clearSession,
  createProperty,
  createWorkOrder,
  getTechnicians,
  getProperties,
  getWorkOrders,
  login,
  readSession,
  assignTechnician,
  updateWorkOrderStatus,
  type NewProperty,
  type NewWorkOrder,
  type Priority,
  type Property,
  type Role,
  type Session,
  type Technician,
  type WorkOrder,
  type WorkOrderStatus,
} from "./api";
import TenantManagement from "./TenantManagement";
import TechnicianManagement from "./TechnicianManagement";

type View = "overview" | "properties" | "orders" | "tenants" | "technicians";

const priorityLabels: Record<Priority, string> = {
  LOW: "Låg",
  MEDIUM: "Normal",
  HIGH: "Hög",
  URGENT: "Akut",
};

const statusLabels: Record<WorkOrderStatus, string> = {
  NEW: "Ny",
  ASSIGNED: "Tilldelad",
  IN_PROGRESS: "Pågår",
  ON_HOLD: "Pausad",
  COMPLETED: "Färdig för granskning",
  CLOSED: "Stängd",
  CANCELLED: "Avbruten",
};

const dateFormat = new Intl.DateTimeFormat("sv-SE", {
  day: "numeric",
  month: "short",
  year: "numeric",
});

function displayDate(value: string) {
  return dateFormat.format(new Date(value));
}

function mark() {
  return (
    <span className="brand-mark" aria-hidden="true">
      <span />
      <span />
      <span />
      <span />
    </span>
  );
}

function LoginScreen({ onLogin }: { onLogin: (session: Session) => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      onLogin(await login(email.trim(), password));
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : "Inloggningen misslyckades.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login-page">
      <div className="login-top">
        <div className="brand">
          {mark()}
          <span>maintenix</span>
        </div>
        <span>Fastighetsunderhåll, samlat.</span>
      </div>
      <div className="login-layout">
        <section className="login-intro">
          <div className="eyebrow light">EN ENKLARE VARDAG</div>
          <h1>
            Ordning på varje <em>ärende.</em>
          </h1>
          <p>
            Från första felanmälan till färdigt arbete. Håll fastigheter och
            arbetsorder samlade på ett ställe.
          </p>
          <div className="intro-line">
            <span className="line-dot" /> Överblick som gör skillnad
          </div>
        </section>
        <section className="login-card" aria-labelledby="login-heading">
          <div className="card-accent" />
          <div className="eyebrow">VÄLKOMMEN TILLBAKA</div>
          <h2 id="login-heading">Logga in</h2>
          <p>Fyll i dina uppgifter för att komma till din översikt.</p>
          <form onSubmit={submit}>
            <label htmlFor="email">E-postadress</label>
            <input
              id="email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="namn@exempel.se"
              required
            />
            <label htmlFor="password">Lösenord</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Ditt lösenord"
              required
            />
            {error && (
              <div className="form-error" role="alert">
                {error}
              </div>
            )}
            <button
              className="button primary full"
              type="submit"
              disabled={busy}
            >
              {busy ? "Loggar in…" : "Logga in"}{" "}
              <span aria-hidden="true">→</span>
            </button>
          </form>
        </section>
      </div>
      <div className="login-footer">
        © {new Date().getFullYear()} Maintenix
      </div>
    </main>
  );
}

function App() {
  const [session, setSession] = useState<Session | null>(readSession);
  const [properties, setProperties] = useState<Property[]>([]);
  const [orders, setOrders] = useState<WorkOrder[]>([]);
  const [technicians, setTechnicians] = useState<Technician[]>([]);
  const [view, setView] = useState<View>("overview");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [showPropertyCreate, setShowPropertyCreate] = useState(false);

  const signOut = useCallback(() => {
    clearSession();
    setSession(null);
    setProperties([]);
    setOrders([]);
    setTechnicians([]);
    setError("");
    setNotice("");
    setView("overview");
  }, []);

  const refresh = useCallback(
    async (current: Session) => {
      setLoading(true);
      setError("");
      try {
        const [newProperties, newOrders, newTechnicians] = await Promise.all([
          current.role === "TECHNICIAN"
            ? Promise.resolve([])
            : getProperties(current.token),
          getWorkOrders(current.token),
          current.role === "ADMIN"
            ? getTechnicians(current.token)
            : Promise.resolve([]),
        ]);
        setProperties(newProperties);
        setOrders(newOrders);
        setTechnicians(newTechnicians);
      } catch (cause) {
        const message =
          cause instanceof Error
            ? cause.message
            : "Det gick inte att hämta data.";
        if (message.includes("session har gått ut")) signOut();
        else setError(message);
      } finally {
        setLoading(false);
      }
    },
    [signOut],
  );

  useEffect(() => {
    if (session) void refresh(session);
  }, [session, refresh]);

  if (!session) return <LoginScreen onLogin={setSession} />;

  const canCreate = session.role !== "TECHNICIAN";
  const visibleProperties =
    session.role === "TECHNICIAN"
      ? Array.from(
          new Map(
            orders.map((order) => [
              order.propertyId,
              {
                id: order.propertyId,
                name: order.propertyName,
                addressLine: "",
                postalCode: "",
                city: "",
                active: true,
              },
            ]),
          ).values(),
        )
      : properties;
  const recentOrders = [...orders].sort((a, b) =>
    b.createdAt.localeCompare(a.createdAt),
  );
  const openOrders = orders.filter(
    (order) => !["COMPLETED", "CLOSED", "CANCELLED"].includes(order.status),
  ).length;

  function onOrderUpdated(updated: WorkOrder) {
    setOrders((current) =>
      current.map((order) => (order.id === updated.id ? updated : order)),
    );
    setNotice("Arbetsordern har uppdaterats.");
  }

  async function onCreated() {
    setShowCreate(false);
    setNotice("Arbetsordern har skapats.");
    setView("orders");
    await refresh(session!);
  }

  async function onPropertyCreated() {
    setShowPropertyCreate(false);
    setNotice("Fastigheten har skapats.");
    setView("properties");
    await refresh(session!);
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          {mark()}
          <span>maintenix</span>
        </div>
        <div className="sidebar-section">ARBETSYTA</div>
        <nav aria-label="Huvudmeny">
          <button
            className={view === "overview" ? "nav-item active" : "nav-item"}
            onClick={() => setView("overview")}
          >
            <span aria-hidden="true">◫</span> Översikt
          </button>
          <button
            className={view === "properties" ? "nav-item active" : "nav-item"}
            onClick={() => setView("properties")}
          >
            <span aria-hidden="true">▤</span> Fastigheter
          </button>
          <button
            className={view === "orders" ? "nav-item active" : "nav-item"}
            onClick={() => setView("orders")}
          >
            <span aria-hidden="true">▦</span> Arbetsorder
          </button>
          {session.role === "ADMIN" && (
            <button
              className={view === "tenants" ? "nav-item active" : "nav-item"}
              onClick={() => setView("tenants")}
            >
              <span aria-hidden="true">♙</span> Hyresgäster
            </button>
          )}
          {session.role === "ADMIN" && (
            <button
              className={
                view === "technicians" ? "nav-item active" : "nav-item"
              }
              onClick={() => setView("technicians")}
            >
              <span aria-hidden="true">⚙</span> Tekniker
            </button>
          )}
        </nav>
        <div className="sidebar-bottom">
          <div className="account">
            <div className="avatar">{session.email[0]?.toUpperCase()}</div>
            <div>
              <strong>{session.email}</strong>
              <span>
                {session.role === "ADMIN"
                  ? "Administratör"
                  : session.role === "TENANT"
                    ? "Hyresgäst"
                    : "Tekniker"}
              </span>
            </div>
          </div>
          <button className="logout" onClick={signOut}>
            Logga ut <span aria-hidden="true">↗</span>
          </button>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <span>
            MAINTENIX /{" "}
            {view === "overview"
              ? "ÖVERSIKT"
              : view === "properties"
                ? "FASTIGHETER"
                : view === "orders"
                  ? "ARBETSORDER"
                  : view === "tenants"
                    ? "HYRESGÄSTER"
                    : "TEKNIKER"}
          </span>
          <span className="today">{dateFormat.format(new Date())}</span>
          <button className="mobile-logout" onClick={signOut}>
            Logga ut
          </button>
        </header>
        <div className="content-wrap">
          {error && (
            <div className="alert error" role="alert">
              {error}
              <button onClick={() => void refresh(session)}>Försök igen</button>
            </div>
          )}
          {notice && (
            <div className="alert success" role="status">
              {notice}
              <button
                aria-label="Stäng meddelande"
                onClick={() => setNotice("")}
              >
                ×
              </button>
            </div>
          )}
          {view === "overview" && (
            <>
              <div className="page-heading">
                <div>
                  <div className="eyebrow">DIN ARBETSYTA</div>
                  <h1>Översikt</h1>
                  <p>Det viktigaste för dina fastigheter, på ett ställe.</p>
                </div>
                {canCreate && (
                  <button
                    className="button primary"
                    onClick={() => setShowCreate(true)}
                  >
                    ＋ Ny arbetsorder
                  </button>
                )}
              </div>
              <div className="stats-grid">
                <div className="stat-card">
                  <span className="stat-icon blue">▤</span>
                  <span className="stat-label">Fastigheter</span>
                  <strong>{visibleProperties.length}</strong>
                  <span className="stat-foot">I din översikt</span>
                </div>
                <div className="stat-card">
                  <span className="stat-icon gold">▦</span>
                  <span className="stat-label">Aktiva arbetsorder</span>
                  <strong>{openOrders}</strong>
                  <span className="stat-foot">Pågående ärenden</span>
                </div>
                <div className="stat-card">
                  <span className="stat-icon green">✓</span>
                  <span className="stat-label">Totalt antal</span>
                  <strong>{orders.length}</strong>
                  <span className="stat-foot">Arbetsorder</span>
                </div>
              </div>
              <section className="panel">
                <div className="panel-heading">
                  <div>
                    <h2>Senaste arbetsorder</h2>
                    <p>En snabb blick på vad som händer.</p>
                  </div>
                  <button
                    className="text-button"
                    onClick={() => setView("orders")}
                  >
                    Visa alla <span aria-hidden="true">→</span>
                  </button>
                </div>
                <OrderList
                  orders={recentOrders.slice(0, 5)}
                  loading={loading}
                  role={session.role}
                />
              </section>
              <section className="panel property-preview">
                <div className="panel-heading">
                  <div>
                    <h2>Dina fastigheter</h2>
                    <p>Fastigheter kopplade till din översikt.</p>
                  </div>
                  <button
                    className="text-button"
                    onClick={() => setView("properties")}
                  >
                    Visa alla <span aria-hidden="true">→</span>
                  </button>
                </div>
                <PropertyList
                  properties={visibleProperties.slice(0, 3)}
                  loading={loading}
                />
              </section>
            </>
          )}
          {view === "properties" && (
            <>
              <div className="page-heading">
                <div>
                  <div className="eyebrow">FASTIGHETER</div>
                  <h1>Dina fastigheter</h1>
                  <p>En samlad vy över fastigheterna du har tillgång till.</p>
                </div>
                {session.role === "ADMIN" && (
                  <button
                    className="button primary"
                    onClick={() => setShowPropertyCreate(true)}
                  >
                    ＋ Ny fastighet
                  </button>
                )}
              </div>
              <section className="panel">
                <PropertyList
                  properties={visibleProperties}
                  loading={loading}
                />
              </section>
            </>
          )}
          {view === "orders" && (
            <>
              <div className="page-heading">
                <div>
                  <div className="eyebrow">ARBETSORDER</div>
                  <h1>Arbetsorder</h1>
                  <p>Följ ärenden från anmälan till klart arbete.</p>
                </div>
                {canCreate && (
                  <button
                    className="button primary"
                    onClick={() => setShowCreate(true)}
                  >
                    ＋ Ny arbetsorder
                  </button>
                )}
              </div>
              <section className="panel">
                <OrderList
                  orders={recentOrders}
                  loading={loading}
                  role={session.role}
                  token={session.token}
                  technicians={technicians}
                  onUpdated={onOrderUpdated}
                  onSessionExpired={signOut}
                  onNavigateTechnicians={() => setView("technicians")}
                />
              </section>
            </>
          )}
          {view === "tenants" && session.role === "ADMIN" && (
            <TenantManagement
              token={session.token}
              properties={properties}
              onSessionExpired={signOut}
            />
          )}
          {view === "technicians" && session.role === "ADMIN" && (
            <TechnicianManagement
              token={session.token}
              technicians={technicians}
              loading={loading}
              onCreated={() => void refresh(session)}
              onSessionExpired={signOut}
            />
          )}
        </div>
      </main>
      {showCreate && (
        <CreateDialog
          properties={properties.filter((property) => property.active)}
          token={session.token}
          onClose={() => setShowCreate(false)}
          onCreated={onCreated}
        />
      )}
      {showPropertyCreate && (
        <CreatePropertyDialog
          token={session.token}
          onClose={() => setShowPropertyCreate(false)}
          onCreated={onPropertyCreated}
        />
      )}
    </div>
  );
}

function CreatePropertyDialog({
  token,
  onClose,
  onCreated,
}: {
  token: string;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [form, setForm] = useState<NewProperty>({
    name: "",
    addressLine: "",
    postalCode: "",
    city: "",
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await createProperty(token, {
        name: form.name.trim(),
        addressLine: form.addressLine.trim(),
        postalCode: form.postalCode.trim(),
        city: form.city.trim(),
      });
      onCreated();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Det gick inte att skapa fastigheten.",
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
        aria-labelledby="create-property-title"
      >
        <div className="modal-head">
          <div>
            <div className="eyebrow">NY FASTIGHET</div>
            <h2 id="create-property-title">Skapa fastighet</h2>
            <p>Lägg till fastigheten innan du skapar en arbetsorder.</p>
          </div>
          <button className="close-button" onClick={onClose} aria-label="Stäng">
            ×
          </button>
        </div>
        <form onSubmit={submit}>
          <label htmlFor="property-name">Namn</label>
          <input
            id="property-name"
            maxLength={150}
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
            required
          />
          <label htmlFor="address-line">Gatuadress</label>
          <input
            id="address-line"
            maxLength={255}
            value={form.addressLine}
            onChange={(event) =>
              setForm({ ...form, addressLine: event.target.value })
            }
            required
          />
          <label htmlFor="postal-code">Postnummer</label>
          <input
            id="postal-code"
            maxLength={20}
            value={form.postalCode}
            onChange={(event) =>
              setForm({ ...form, postalCode: event.target.value })
            }
            required
          />
          <label htmlFor="city">Ort</label>
          <input
            id="city"
            maxLength={100}
            value={form.city}
            onChange={(event) => setForm({ ...form, city: event.target.value })}
            required
          />
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
              {busy ? "Skapar…" : "Skapa fastighet"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function PropertyList({
  properties,
  loading,
}: {
  properties: Property[];
  loading: boolean;
}) {
  if (loading) return <div className="empty">Laddar fastigheter…</div>;
  if (!properties.length)
    return <div className="empty">Inga fastigheter att visa ännu.</div>;
  return (
    <div className="property-list">
      {properties.map((property) => (
        <div className="property-row" key={property.id}>
          <span className="property-icon">▤</span>
          <div>
            <strong>{property.name}</strong>
            <span>
              {[property.addressLine, property.postalCode, property.city]
                .filter(Boolean)
                .join(", ") || "Fastighet kopplad till arbetsorder"}
            </span>
          </div>
          <span
            className={
              property.active ? "property-state" : "property-state inactive"
            }
          >
            {property.active ? "Aktiv" : "Inaktiv"}
          </span>
        </div>
      ))}
    </div>
  );
}

function OrderList({
  orders,
  loading,
  role,
  token,
  technicians = [],
  onUpdated,
  onSessionExpired,
  onNavigateTechnicians,
}: {
  orders: WorkOrder[];
  loading: boolean;
  role: Role;
  token?: string;
  technicians?: Technician[];
  onUpdated?: (order: WorkOrder) => void;
  onSessionExpired?: () => void;
  onNavigateTechnicians?: () => void;
}) {
  if (loading) return <div className="empty">Laddar arbetsorder…</div>;
  if (!orders.length)
    return <div className="empty">Inga arbetsorder att visa ännu.</div>;
  return (
    <div className="order-list">
      {orders.map((order) => (
        <article className="order-row" key={order.id}>
          <div className="order-main">
            <div className="order-title">
              <strong>{order.title}</strong>
              <span className={`status status-${order.status.toLowerCase()}`}>
                {statusLabels[order.status] || order.status}
              </span>
            </div>
            <p>{order.description}</p>
            <div className="order-meta">
              <span>▤ {order.propertyName}</span>
              <span>◷ {displayDate(order.createdAt)}</span>
              <span>Av {order.createdByName}</span>
              {order.assignedToUserId && (
                <span>
                  Tilldelad:{" "}
                  {technicians.find(
                    (technician) => technician.id === order.assignedToUserId,
                  )?.firstName ?? "tekniker"}
                </span>
              )}
            </div>
            {token && onUpdated && onSessionExpired && (
              <OrderActions
                order={order}
                role={role}
                token={token}
                technicians={technicians}
                onUpdated={onUpdated}
                onSessionExpired={onSessionExpired}
                onNavigateTechnicians={onNavigateTechnicians}
              />
            )}
          </div>
          <span className={`priority priority-${order.priority.toLowerCase()}`}>
            {priorityLabels[order.priority]}
          </span>
        </article>
      ))}
    </div>
  );
}

function OrderActions({
  order,
  role,
  token,
  technicians,
  onUpdated,
  onSessionExpired,
  onNavigateTechnicians,
}: {
  order: WorkOrder;
  role: Role;
  token: string;
  technicians: Technician[];
  onUpdated: (order: WorkOrder) => void;
  onSessionExpired: () => void;
  onNavigateTechnicians?: () => void;
}) {
  const [selectedTechnician, setSelectedTechnician] = useState(
    order.assignedToUserId || "",
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const activeTechnicians = technicians.filter(
    (technician) => technician.active,
  );

  async function perform(action: () => Promise<WorkOrder>) {
    setBusy(true);
    setError("");
    try {
      onUpdated(await action());
    } catch (cause) {
      const message =
        cause instanceof Error
          ? cause.message
          : "Det gick inte att uppdatera arbetsordern.";
      if (message.includes("session har gått ut")) onSessionExpired();
      else setError(message);
    } finally {
      setBusy(false);
    }
  }

  let actions: ReactNode = null;
  if (role === "ADMIN" && order.status === "COMPLETED") {
    actions = (
      <button
        className="button primary compact"
        disabled={busy}
        onClick={() =>
          void perform(() => updateWorkOrderStatus(token, order.id, "CLOSED"))
        }
      >
        {busy ? "Stänger…" : "Stäng ärende"}
      </button>
    );
  } else if (
    role === "ADMIN" &&
    !["COMPLETED", "CLOSED", "CANCELLED"].includes(order.status)
  ) {
    actions = activeTechnicians.length ? (
      <>
        <select
          aria-label={`Tilldela tekniker för ${order.title}`}
          value={selectedTechnician}
          onChange={(event) => setSelectedTechnician(event.target.value)}
        >
          <option value="">Välj tekniker</option>
          {activeTechnicians.map((technician) => (
            <option key={technician.id} value={technician.id}>
              {technician.firstName} {technician.lastName}
            </option>
          ))}
        </select>
        <button
          className="button secondary compact"
          disabled={
            busy ||
            !selectedTechnician ||
            selectedTechnician === order.assignedToUserId
          }
          onClick={() =>
            void perform(() =>
              assignTechnician(token, order.id, selectedTechnician),
            )
          }
        >
          {busy
            ? "Sparar…"
            : order.assignedToUserId
              ? "Byt tekniker"
              : "Tilldela"}
        </button>
      </>
    ) : (
      <button className="text-button" onClick={onNavigateTechnicians}>
        Skapa en tekniker →
      </button>
    );
  } else if (role === "TECHNICIAN") {
    const next: { status: WorkOrderStatus; label: string }[] =
      order.status === "ASSIGNED"
        ? [{ status: "IN_PROGRESS", label: "Starta arbete" }]
        : order.status === "IN_PROGRESS"
          ? [
              { status: "ON_HOLD", label: "Pausa" },
              { status: "COMPLETED", label: "Markera färdig" },
            ]
          : order.status === "ON_HOLD"
            ? [{ status: "IN_PROGRESS", label: "Återuppta" }]
            : [];
    actions = next.map((action) => (
      <button
        className="button secondary compact"
        key={action.status}
        disabled={busy}
        onClick={() =>
          void perform(() =>
            updateWorkOrderStatus(token, order.id, action.status),
          )
        }
      >
        {busy ? "Sparar…" : action.label}
      </button>
    ));
  }

  if (!actions && !error) return null;
  return (
    <div className="order-controls">
      {actions}
      {error && (
        <span className="order-action-error" role="alert">
          {error}
        </span>
      )}
    </div>
  );
}

function CreateDialog({
  properties,
  token,
  onClose,
  onCreated,
}: {
  properties: Property[];
  token: string;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [form, setForm] = useState<NewWorkOrder>({
    propertyId: properties[0]?.id || "",
    title: "",
    description: "",
    priority: "MEDIUM",
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    function onEscape(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onEscape);
    return () => window.removeEventListener("keydown", onEscape);
  }, [onClose]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      await createWorkOrder(token, {
        ...form,
        title: form.title.trim(),
        description: form.description.trim(),
      });
      onCreated();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Det gick inte att skapa arbetsordern.",
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
        aria-labelledby="create-title"
      >
        <div className="modal-head">
          <div>
            <div className="eyebrow">NYTT ÄRENDE</div>
            <h2 id="create-title">Skapa arbetsorder</h2>
            <p>Beskriv vad som behöver åtgärdas.</p>
          </div>
          <button className="close-button" onClick={onClose} aria-label="Stäng">
            ×
          </button>
        </div>
        <form onSubmit={submit}>
          <label htmlFor="property">Fastighet</label>
          <select
            id="property"
            value={form.propertyId}
            onChange={(event) =>
              setForm({ ...form, propertyId: event.target.value })
            }
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
              Du behöver tillgång till en aktiv fastighet för att skapa en
              arbetsorder.
            </p>
          )}
          <label htmlFor="title">Rubrik</label>
          <input
            id="title"
            maxLength={200}
            value={form.title}
            onChange={(event) =>
              setForm({ ...form, title: event.target.value })
            }
            placeholder="Till exempel: Läckande kran"
            required
          />
          <label htmlFor="description">Beskrivning</label>
          <textarea
            id="description"
            rows={5}
            value={form.description}
            onChange={(event) =>
              setForm({ ...form, description: event.target.value })
            }
            placeholder="Beskriv problemet så tydligt du kan…"
            required
          />
          <label htmlFor="priority">Prioritet</label>
          <select
            id="priority"
            value={form.priority}
            onChange={(event) =>
              setForm({ ...form, priority: event.target.value as Priority })
            }
          >
            {Object.entries(priorityLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
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
              {busy ? "Skapar…" : "Skapa arbetsorder"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

export default App;
