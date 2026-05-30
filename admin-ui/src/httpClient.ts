import { TOKEN_KEY } from "./types";

export { TOKEN_KEY };

export class HttpError extends Error {
  status: number;
  body: unknown;
  constructor(status: number, message: string, body?: unknown) {
    super(message);
    this.name = "HttpError";
    this.status = status;
    this.body = body;
  }
}

export interface HttpResponse<T> {
  status: number;
  headers: Headers;
  json: T;
}

export async function httpClient<T = unknown>(
  path: string,
  init: RequestInit = {}
): Promise<HttpResponse<T>> {
  const headers = new Headers(init.headers);
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) headers.set("X-Admin-Token", token);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  let response: Response;
  try {
    response = await fetch(path, { ...init, headers });
  } catch {
    throw new HttpError(0, "Network error");
  }
  const text = await response.text();
  let parsed: unknown = undefined;
  if (text.length > 0) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = text;
    }
  }
  if (!response.ok) {
    const message =
      typeof parsed === "object" && parsed && "message" in parsed
        ? String((parsed as Record<string, unknown>).message)
        : response.statusText || `HTTP ${response.status}`;
    throw new HttpError(response.status, message, parsed);
  }
  return { status: response.status, headers: response.headers, json: parsed as T };
}
