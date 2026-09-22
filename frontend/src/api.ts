export type Role = "ADMIN" | "TENANT" | "TECHNICIAN";
export type Priority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

export interface Session {
  token: string;
  email: string;
  role: Role;
  expiresAt: number;
}

export interface Property {
  id: string;
  name: string;
  addressLine: string;
  postalCode: string;
  city: string;
  active: boolean;
}

export interface WorkOrder {
  id: string;
  propertyId: string;
  propertyName: string;
  title: string;
  description: string;
  status: string;
  priority: Priority;
  createdAt: string;
  createdByName: string;
}

export interface NewWorkOrder {
  propertyId: string;
  title: string;
  description: string;
  priority: Priority;
}

export interface NewProperty {
  name: string;
  addressLine: string;
  postalCode: string;
  city: string;
}

export interface Tenant {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: "TENANT";
  active: boolean;
}

export interface PropertyMember {
  id: string;
  propertyId: string;
  userId: string;
}

export interface NewTenant {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  role: "TENANT";
}

const SESSION_KEY = "maintenix.session";

function decodeToken(
  token: string,
): Pick<Session, "email" | "role" | "expiresAt"> {
  const encoded = token.split(".")[1];
  if (!encoded) throw new Error("Ogiltigt svar från servern.");
  const payload = JSON.parse(
    atob(encoded.replace(/-/g, "+").replace(/_/g, "/")),
  );
  if (
    !["ADMIN", "TENANT", "TECHNICIAN"].includes(payload.role) ||
    typeof payload.email !== "string" ||
    typeof payload.exp !== "number"
  ) {
    throw new Error("Ogiltigt svar från servern.");
  }
  return {
    email: payload.email,
    role: payload.role,
    expiresAt: payload.exp * 1000,
  };
}

export function readSession(): Session | null {
  try {
    const saved = sessionStorage.getItem(SESSION_KEY);
    if (!saved) return null;
    const session = JSON.parse(saved) as Session;
    if (!session.token || session.expiresAt <= Date.now()) {
      clearSession();
      return null;
    }
    return session;
  } catch {
    clearSession();
    return null;
  }
}

export function clearSession() {
  sessionStorage.removeItem(SESSION_KEY);
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  token?: string,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...options.headers,
      },
    });
  } catch {
    throw new Error(
      "Det gick inte att nå servern. Kontrollera att backend körs.",
    );
  }

  if (!response.ok) {
    if (response.status === 401)
      throw new Error(
        path === "/auth/login"
          ? "Fel e-postadress eller lösenord."
          : "Din session har gått ut. Logga in igen.",
      );
    if (response.status === 403)
      throw new Error("Du saknar behörighet för den här åtgärden.");
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || `Något gick fel (${response.status}).`);
  }
  return response.json() as Promise<T>;
}

export async function login(email: string, password: string): Promise<Session> {
  const result = await request<{ accessToken: string }>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
  const session = {
    token: result.accessToken,
    ...decodeToken(result.accessToken),
  };
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
  return session;
}

export function getProperties(token: string) {
  return request<Property[]>("/properties", {}, token);
}

export function getWorkOrders(token: string) {
  return request<WorkOrder[]>("/work-orders", {}, token);
}

export function createWorkOrder(token: string, workOrder: NewWorkOrder) {
  return request<WorkOrder>(
    "/work-orders",
    {
      method: "POST",
      body: JSON.stringify(workOrder),
    },
    token,
  );
}

export function createProperty(token: string, property: NewProperty) {
  return request<Property>(
    "/properties",
    {
      method: "POST",
      body: JSON.stringify(property),
    },
    token,
  );
}

export function getTenants(token: string) {
  return request<Tenant[]>("/users?role=TENANT", {}, token);
}

export function createTenant(token: string, tenant: NewTenant) {
  return request<Tenant>(
    "/users",
    { method: "POST", body: JSON.stringify(tenant) },
    token,
  );
}

export function getPropertyMembers(token: string, propertyId: string) {
  return request<PropertyMember[]>(
    `/properties/${propertyId}/members`,
    {},
    token,
  );
}

export function addPropertyMember(
  token: string,
  propertyId: string,
  userId: string,
) {
  return request<PropertyMember>(
    `/properties/${propertyId}/members`,
    { method: "POST", body: JSON.stringify({ userId }) },
    token,
  );
}
