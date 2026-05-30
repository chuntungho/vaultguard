import { describe, it, expect, beforeEach, vi } from "vitest";
import { dataProvider, adminActions } from "../src/dataProvider";

function mockFetchOk(json: unknown, headers: Record<string, string> = {}) {
  return vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
    new Response(JSON.stringify(json), {
      status: 200,
      headers: { "Content-Type": "application/json", ...headers },
    })
  );
}

describe("dataProvider CRUD", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it("getList('users') sends page, size, sort, q query params", async () => {
    const fetchSpy = mockFetchOk([], { "X-Total-Count": "0" });

    await dataProvider.getList("users", {
      pagination: { page: 2, perPage: 25 },
      sort: { field: "email", order: "ASC" },
      filter: { q: "alice" },
    });

    const url = fetchSpy.mock.calls[0][0] as string;
    expect(url).toContain("/api/admin/users?");
    expect(url).toContain("page=1"); // RA pagination.page is 1-based; backend is 0-based
    expect(url).toContain("size=25");
    expect(url).toContain("sort=email%2Casc");
    expect(url).toContain("q=alice");
  });

  it("getList('users') returns { data, total } from X-Total-Count", async () => {
    mockFetchOk([{ id: "u1", email: "a@b.com" }], { "X-Total-Count": "100" });

    const result = await dataProvider.getList("users", {
      pagination: { page: 1, perPage: 25 },
      sort: { field: "email", order: "ASC" },
      filter: {},
    });

    expect(result).toEqual({ data: [{ id: "u1", email: "a@b.com" }], total: 100 });
  });

  it("getList falls back to data.length when X-Total-Count missing", async () => {
    mockFetchOk([{ id: "u1" }, { id: "u2" }]);

    const result = await dataProvider.getList("users", {
      pagination: { page: 1, perPage: 25 },
      sort: { field: "id", order: "ASC" },
      filter: {},
    });

    expect(result.total).toBe(2);
  });

  it("getList('organizations') uses the organizations URL", async () => {
    const fetchSpy = mockFetchOk([], { "X-Total-Count": "0" });

    await dataProvider.getList("organizations", {
      pagination: { page: 1, perPage: 25 },
      sort: { field: "name", order: "ASC" },
      filter: {},
    });

    expect(fetchSpy.mock.calls[0][0]).toContain("/api/admin/organizations?");
  });

  it("delete('users') hits DELETE /api/admin/users/{id}", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(null, { status: 204 })
    );

    const result = await dataProvider.delete("users", { id: "abc-123", previousData: { id: "abc-123" } });

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/users/abc-123");
    expect((fetchSpy.mock.calls[0][1] as RequestInit).method).toBe("DELETE");
    expect(result).toEqual({ data: { id: "abc-123" } });
  });

  it("delete('organizations') hits DELETE /api/admin/organizations/{id}", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(null, { status: 204 })
    );

    await dataProvider.delete("organizations", { id: "org-1", previousData: { id: "org-1" } });

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/organizations/org-1");
  });

  it("deleteMany('users') deletes each id in parallel", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    const result = await dataProvider.deleteMany("users", { ids: ["u1", "u2", "u3"] });

    expect(fetchSpy).toHaveBeenCalledTimes(3);
    const calledUrls = fetchSpy.mock.calls.map((c) => c[0] as string);
    expect(calledUrls).toContain("/api/admin/users/u1");
    expect(calledUrls).toContain("/api/admin/users/u2");
    expect(calledUrls).toContain("/api/admin/users/u3");
    expect(result).toEqual({ data: ["u1", "u2", "u3"] });
  });

  it("deleteMany aggregates errors and reports failures", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(null, { status: 204 })) // u1 ok
      .mockResolvedValueOnce(new Response("nope", { status: 500 })) // u2 fails
      .mockResolvedValueOnce(new Response(null, { status: 204 })); // u3 ok

    await expect(
      dataProvider.deleteMany("users", { ids: ["u1", "u2", "u3"] })
    ).rejects.toThrow(/1 of 3 deletes failed/);
  });

  it("getOne throws NotImplemented", async () => {
    await expect(dataProvider.getOne("users", { id: "u1" })).rejects.toThrow(/not supported/i);
  });

  it("getList with unknown resource throws", async () => {
    await expect(
      dataProvider.getList("ciphers", {
        pagination: { page: 1, perPage: 10 },
        sort: { field: "id", order: "ASC" },
        filter: {},
      })
    ).rejects.toThrow(/Unknown resource/);
  });
});

describe("dataProvider admin actions", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it("disableUser posts to /users/{id}/disable", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ id: "u1", enabled: false }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const result = await adminActions.disableUser("u1");

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/users/u1/disable");
    expect((fetchSpy.mock.calls[0][1] as RequestInit).method).toBe("POST");
    expect(result).toEqual({ id: "u1", enabled: false });
  });

  it("enableUser posts to /users/{id}/enable", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ id: "u1", enabled: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    await adminActions.enableUser("u1");

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/users/u1/enable");
    expect((fetchSpy.mock.calls[0][1] as RequestInit).method).toBe("POST");
  });

  it("deauthUser posts to /users/{id}/deauth", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(null, { status: 204 })
    );

    await adminActions.deauthUser("u1");

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/users/u1/deauth");
    expect((fetchSpy.mock.calls[0][1] as RequestInit).method).toBe("POST");
  });

  it("removeTwoFactor deletes /users/{id}/2fa", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(null, { status: 204 })
    );

    await adminActions.removeTwoFactor("u1");

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/users/u1/2fa");
    expect((fetchSpy.mock.calls[0][1] as RequestInit).method).toBe("DELETE");
  });

  it("getSettings GETs /api/admin/settings", async () => {
    const settings = {
      domain: "http://localhost:8080",
      signupsAllowed: true,
      invitationsAllowed: true,
      passwordIterations: 600000,
      mail: { from: "x", fromName: "y" },
    };
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify(settings), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const result = await adminActions.getSettings();

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/settings");
    expect(result).toEqual(settings);
  });

  it("saveSettings POSTs JSON to /api/admin/settings", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    await adminActions.saveSettings({ signupsAllowed: false } as any);

    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/settings");
    expect(init.method).toBe("POST");
    expect(new Headers(init.headers).get("Content-Type")).toBe("application/json");
    expect(init.body).toBe(JSON.stringify({ signupsAllowed: false }));
  });

  it("getDiagnostics GETs /api/admin/diagnostics", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ version: "1.0.0" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const result = await adminActions.getDiagnostics();

    expect(fetchSpy.mock.calls[0][0]).toBe("/api/admin/diagnostics");
    expect(result).toEqual({ version: "1.0.0" });
  });
});
