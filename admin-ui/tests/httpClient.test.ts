import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import { httpClient, HttpError, TOKEN_KEY } from "../src/httpClient";

describe("httpClient", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it("attaches X-Admin-Token header when token is in sessionStorage", async () => {
    sessionStorage.setItem(TOKEN_KEY, "secret-token");
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    await httpClient("/api/admin/users");

    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get("X-Admin-Token")).toBe("secret-token");
  });

  it("omits X-Admin-Token header when no token is stored", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } })
    );

    await httpClient("/api/admin/users");

    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get("X-Admin-Token")).toBeNull();
  });

  it("parses JSON body for 2xx", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ a: 1 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const result = await httpClient("/api/admin/diagnostics");
    expect(result.json).toEqual({ a: 1 });
    expect(result.status).toBe(200);
  });

  it("exposes response headers", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("[]", {
        status: 200,
        headers: { "Content-Type": "application/json", "X-Total-Count": "42" },
      })
    );

    const result = await httpClient("/api/admin/users");
    expect(result.headers.get("X-Total-Count")).toBe("42");
  });

  it("throws HttpError with status and body for non-2xx", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("not found", { status: 404 })
    );

    await expect(httpClient("/api/admin/users/missing")).rejects.toMatchObject({
      status: 404,
    });
  });

  it("wraps network rejection as HttpError(0, 'Network error')", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValueOnce(new TypeError("fetch failed"));

    await expect(httpClient("/api/admin/users")).rejects.toMatchObject({
      status: 0,
      message: "Network error",
    });
  });

  it("auto-sets Content-Type: application/json when body is provided and CT is absent", async () => {
    sessionStorage.setItem(TOKEN_KEY, "tok");
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } })
    );

    await httpClient("/api/admin/settings", {
      method: "POST",
      body: JSON.stringify({ x: 1 }),
    });

    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    expect(new Headers(init.headers).get("Content-Type")).toBe("application/json");
  });

  it("does not override an explicit Content-Type", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } })
    );

    await httpClient("/api/admin/upload", {
      method: "POST",
      body: "binary",
      headers: { "Content-Type": "application/octet-stream" },
    });

    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    expect(new Headers(init.headers).get("Content-Type")).toBe("application/octet-stream");
  });

  it("HttpError is an instance of Error", () => {
    const e = new HttpError(401, "Unauthorized");
    expect(e).toBeInstanceOf(Error);
    expect(e.status).toBe(401);
  });
});
