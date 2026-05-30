import { describe, it, expect, beforeEach, vi } from "vitest";
import { authProvider } from "../src/authProvider";
import { TOKEN_KEY } from "../src/types";

describe("authProvider", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it("login stores token on 200 diagnostics response", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ version: "1.0.0" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    await authProvider.login({ token: "valid-token" });

    expect(sessionStorage.getItem(TOKEN_KEY)).toBe("valid-token");
  });

  it("login sends X-Admin-Token header to diagnostics", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } })
    );

    await authProvider.login({ token: "abc" });

    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get("X-Admin-Token")).toBe("abc");
    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/diagnostics");
  });

  it("login rejects and does not store on non-2xx", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("unauthorized", { status: 401 })
    );

    await expect(authProvider.login({ token: "bad" })).rejects.toThrow(/invalid/i);
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it("login rejects on network failure", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValueOnce(new TypeError("fetch failed"));

    await expect(authProvider.login({ token: "x" })).rejects.toThrow();
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it("checkAuth resolves when token present", async () => {
    sessionStorage.setItem(TOKEN_KEY, "stored");
    await expect(authProvider.checkAuth({})).resolves.toBeUndefined();
  });

  it("checkAuth rejects when token absent", async () => {
    await expect(authProvider.checkAuth({})).rejects.toBeDefined();
  });

  it("logout clears storage", async () => {
    sessionStorage.setItem(TOKEN_KEY, "x");
    await authProvider.logout({});
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it("checkError rejects on 401", async () => {
    await expect(authProvider.checkError({ status: 401 } as any)).rejects.toBeDefined();
  });

  it("checkError rejects on 403", async () => {
    await expect(authProvider.checkError({ status: 403 } as any)).rejects.toBeDefined();
  });

  it("checkError resolves on 500", async () => {
    await expect(authProvider.checkError({ status: 500 } as any)).resolves.toBeUndefined();
  });

  it("getIdentity returns admin identity", async () => {
    const id = await authProvider.getIdentity!();
    expect(id).toMatchObject({ id: "admin" });
  });
});
