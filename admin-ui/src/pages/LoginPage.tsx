import { useState, FormEvent } from "react";
import { useLogin, useNotify } from "react-admin";

export function LoginPage() {
  const [token, setToken] = useState("");
  const [error, setError] = useState<string | null>(null);
  const login = useLogin();
  const notify = useNotify();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await login({ token });
    } catch (err) {
      const msg = (err as Error).message || "Invalid admin token";
      setError(msg);
      notify(msg, { type: "error" });
    }
  }

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        minHeight: "100vh",
        background: "#f5f5f5",
      }}
    >
      <form
        onSubmit={onSubmit}
        style={{
          background: "white",
          padding: "2rem",
          borderRadius: "8px",
          boxShadow: "0 2px 8px rgba(0,0,0,0.1)",
          width: "320px",
        }}
      >
        <h1 style={{ marginTop: 0, fontSize: "1.25rem" }}>VaultGuard Admin</h1>
        <label htmlFor="admin-token" style={{ display: "block", marginBottom: "0.5rem" }}>
          Admin token
        </label>
        <input
          id="admin-token"
          type="password"
          value={token}
          onChange={(e) => setToken(e.target.value)}
          autoFocus
          required
          style={{
            width: "100%",
            padding: "0.5rem",
            marginBottom: "1rem",
            border: "1px solid #ccc",
            borderRadius: "4px",
            boxSizing: "border-box",
          }}
        />
        {error && (
          <div role="alert" style={{ color: "#b00020", marginBottom: "1rem" }}>
            {error}
          </div>
        )}
        <button
          type="submit"
          style={{
            width: "100%",
            padding: "0.5rem",
            background: "#1976d2",
            color: "white",
            border: "none",
            borderRadius: "4px",
            cursor: "pointer",
          }}
        >
          Sign in
        </button>
      </form>
    </div>
  );
}
