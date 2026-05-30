import type { AuthProvider } from "react-admin";
import { TOKEN_KEY } from "./types";

interface LoginParams {
  token: string;
}

export const authProvider: AuthProvider = {
  async login(params: LoginParams | unknown) {
    const token = (params as LoginParams).token;
    if (!token) throw new Error("Token required");
    let response: Response;
    try {
      response = await fetch("/api/admin/diagnostics", {
        method: "GET",
        headers: { "X-Admin-Token": token },
      });
    } catch {
      throw new Error("Network error");
    }
    if (!response.ok) {
      throw new Error("Invalid admin token");
    }
    sessionStorage.setItem(TOKEN_KEY, token);
  },

  async checkAuth() {
    if (!sessionStorage.getItem(TOKEN_KEY)) {
      throw new Error("Not authenticated");
    }
  },

  async checkError(error: { status?: number }) {
    if (error?.status === 401 || error?.status === 403) {
      sessionStorage.removeItem(TOKEN_KEY);
      throw new Error("Not authenticated");
    }
  },

  async logout() {
    sessionStorage.removeItem(TOKEN_KEY);
  },

  async getIdentity() {
    return { id: "admin", fullName: "Admin" };
  },

  async getPermissions() {
    return undefined;
  },
};
