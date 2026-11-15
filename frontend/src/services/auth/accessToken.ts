const STORAGE_KEY = "skillmatch.session";

interface StoredSession {
  accessToken: string;
  expiresAt: number;
}

let accessToken = readSession()?.accessToken ?? null;

function readStoredSession(storage: Storage): StoredSession | null {
  const rawSession = storage.getItem(STORAGE_KEY);
  if (!rawSession) return null;

  let sessionValue: unknown;
  try {
    sessionValue = JSON.parse(rawSession);
  } catch (error) {
    if (!(error instanceof SyntaxError)) throw error;
    storage.removeItem(STORAGE_KEY);
    return null;
  }

  if (
    typeof sessionValue !== "object" ||
    sessionValue === null ||
    Array.isArray(sessionValue)
  ) {
    storage.removeItem(STORAGE_KEY);
    return null;
  }
  const session = sessionValue as Record<string, unknown>;
  if (
    typeof session.accessToken !== "string" ||
    typeof session.expiresAt !== "number" ||
    session.expiresAt <= Date.now()
  ) {
    storage.removeItem(STORAGE_KEY);
    return null;
  }

  return {
    accessToken: session.accessToken,
    expiresAt: session.expiresAt,
  };
}

function readSession(): StoredSession | null {
  return readStoredSession(window.localStorage) ?? readStoredSession(window.sessionStorage);
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(
  token: string,
  expiresInSeconds: number,
  rememberMe: boolean,
): void {
  accessToken = token;
  window.localStorage.removeItem(STORAGE_KEY);
  window.sessionStorage.removeItem(STORAGE_KEY);

  const session: StoredSession = {
    accessToken: token,
    expiresAt: Date.now() + expiresInSeconds * 1000,
  };
  if (rememberMe) {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }
}

export function clearAccessToken(): void {
  accessToken = null;
  window.localStorage.removeItem(STORAGE_KEY);
  window.sessionStorage.removeItem(STORAGE_KEY);
}
