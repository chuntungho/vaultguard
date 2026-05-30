import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthContext, NotificationContextProvider } from "react-admin";
import { MemoryRouter } from "react-router-dom";
import { LoginPage } from "../src/pages/LoginPage";
import { authProvider } from "../src/authProvider";
import { TOKEN_KEY } from "../src/types";

function renderLogin() {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthContext.Provider value={authProvider}>
        <NotificationContextProvider>
          <MemoryRouter>
            <LoginPage />
          </MemoryRouter>
        </NotificationContextProvider>
      </AuthContext.Provider>
    </QueryClientProvider>
  );
}

describe("LoginPage", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it("renders a token input and a submit button", () => {
    renderLogin();
    expect(screen.getByLabelText(/admin token/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /sign in/i })).toBeInTheDocument();
  });

  it("submits the token and stores it on success", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } })
    );

    renderLogin();

    await userEvent.type(screen.getByLabelText(/admin token/i), "good-token");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await new Promise((r) => setTimeout(r, 50));
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe("good-token");
  });

  it("shows an error message on invalid token", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response("nope", { status: 401 })
    );

    renderLogin();

    await userEvent.type(screen.getByLabelText(/admin token/i), "bad-token");
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByText(/invalid admin token/i)).toBeInTheDocument();
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });
});
