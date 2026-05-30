import type { DataProvider } from "react-admin";
import { httpClient } from "./httpClient";

const RESOURCE_URL: Record<string, string> = {
  users: "/api/admin/users",
  organizations: "/api/admin/organizations",
};

function urlFor(resource: string): string {
  const u = RESOURCE_URL[resource];
  if (!u) throw new Error(`Unknown resource: ${resource}`);
  return u;
}

export const dataProvider: DataProvider = {
  async getList(resource, params) {
    const { page, perPage } = params.pagination ?? { page: 1, perPage: 25 };
    const sort = params.sort;
    const filter = (params.filter ?? {}) as Record<string, unknown>;
    const search = new URLSearchParams();
    search.set("page", String(page - 1)); // RA is 1-based, backend is 0-based
    search.set("size", String(perPage));
    if (sort && sort.field) {
      search.set("sort", `${sort.field},${sort.order.toLowerCase()}`);
    }
    if (typeof filter.q === "string" && filter.q.length > 0) {
      search.set("q", filter.q);
    }
    const response = await httpClient<unknown[]>(`${urlFor(resource)}?${search}`);
    const total = response.headers.get("X-Total-Count");
    const data = response.json as { id: string | number }[];
    return { data: data as any, total: total !== null ? parseInt(total, 10) : data.length };
  },

  async getOne(_resource, _params) {
    throw new Error("getOne not supported by the admin API");
  },

  async getMany(_resource, _params) {
    throw new Error("getMany not supported by the admin API");
  },

  async getManyReference(_resource, _params) {
    throw new Error("getManyReference not supported by the admin API");
  },

  async create(_resource, _params) {
    throw new Error("create not supported by the admin API");
  },

  async update(_resource, _params) {
    throw new Error("update not supported by the admin API");
  },

  async updateMany(_resource, _params) {
    throw new Error("updateMany not supported by the admin API");
  },

  async delete(resource, params) {
    await httpClient(`${urlFor(resource)}/${params.id}`, { method: "DELETE" });
    return { data: (params.previousData ?? { id: params.id }) as any };
  },

  async deleteMany(resource, params) {
    for (const id of params.ids) {
      await httpClient(`${urlFor(resource)}/${id}`, { method: "DELETE" });
    }
    return { data: params.ids };
  },
};
