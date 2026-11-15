import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LoginPage } from "./LoginPage";
import { authService } from "../../../services/auth/authService";
import { useAuthStore } from "../../../stores/authStore";
import type { CurrentUser, LoginResponse } from "../../../types/auth";

vi.mock("../../../services/auth/authService", () => ({
  authService: {
    login: vi.fn(),
    me: vi.fn(),
    register: vi.fn(),
    resendVerification: vi.fn(),
    verifyEmail: vi.fn(),
  },
}));

const loginResponse: LoginResponse = {
  accessToken: "test-access-token",
  tokenType: "Bearer",
  expiresInSeconds: 3600,
};

const currentUser: CurrentUser = {
  id: 8,
  name: "Student Example",
  email: "student@college.edu",
  role: "STUDENT",
  srn: "02FE24BCS141",
};

function renderLogin() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <LoginPage />
        <CurrentPath />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function CurrentPath() {
  const location = useLocation();
  return <output aria-label="Current path">{location.pathname}</output>;
}

describe("login form", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.getState().signOut();
  });

  it("shows inline required and email format errors", async () => {
    const user = userEvent.setup();
    renderLogin();
    await user.click(screen.getByRole("button", { name: "Sign In" }));
    expect(await screen.findByText("Enter your email address.")).toBeInTheDocument();
    expect(screen.getByText("Enter your password.")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Email address"), "not-an-email");
    await user.type(screen.getByLabelText("Password"), "password");
    await user.click(screen.getByRole("button", { name: "Sign In" }));
    expect(await screen.findByText("Enter a valid email address.")).toBeInTheDocument();
    expect(authService.login).not.toHaveBeenCalled();
  });

  it("toggles password visibility accessibly", async () => {
    const user = userEvent.setup();
    renderLogin();
    const password = screen.getByLabelText("Password");
    expect(password).toHaveAttribute("type", "password");
    await user.click(screen.getByRole("button", { name: "Show password" }));
    expect(password).toHaveAttribute("type", "text");
    await user.click(screen.getByRole("button", { name: "Hide password" }));
    expect(password).toHaveAttribute("type", "password");
  });

  it("establishes a session after login and current-user lookup succeed", async () => {
    vi.mocked(authService.login).mockResolvedValue(loginResponse);
    vi.mocked(authService.me).mockResolvedValue(currentUser);
    renderLogin();

    fireEvent.change(screen.getByLabelText("Email address"), {
      target: { value: "student@college.edu" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "correct-horse" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Sign In" }));

    await waitFor(() => {
      expect(useAuthStore.getState().user).toEqual(currentUser);
    });
    expect(await screen.findByLabelText("Current path")).toHaveTextContent("/app");
    expect(authService.login).toHaveBeenCalledWith({
      email: "student@college.edu",
      password: "correct-horse",
    });
    expect(authService.me).toHaveBeenCalledOnce();
    expect(localStorage.getItem("skillmatch.session")).not.toBeNull();
    expect(sessionStorage.getItem("skillmatch.session")).toBeNull();
  });

  it("redirects an authenticated administrator to the admin workspace", async () => {
    vi.mocked(authService.login).mockResolvedValue(loginResponse);
    vi.mocked(authService.me).mockResolvedValue({
      ...currentUser,
      role: "ADMIN",
      srn: null,
    });
    renderLogin();

    fireEvent.change(screen.getByLabelText("Email address"), {
      target: { value: "admin@college.edu" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "correct-horse" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Sign In" }));

    await waitFor(() => {
      expect(useAuthStore.getState().user?.role).toBe("ADMIN");
      expect(screen.getByLabelText("Current path")).toHaveTextContent("/admin");
    });
  });

  it("keeps an unchecked remember-me session in memory only", async () => {
    vi.mocked(authService.login).mockResolvedValue(loginResponse);
    vi.mocked(authService.me).mockResolvedValue(currentUser);
    renderLogin();

    fireEvent.change(screen.getByLabelText("Email address"), {
      target: { value: "student@college.edu" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "correct-horse" },
    });
    fireEvent.click(screen.getByRole("checkbox", { name: "Remember me" }));
    fireEvent.click(screen.getByRole("button", { name: "Sign In" }));

    await waitFor(() => {
      expect(useAuthStore.getState().user).toEqual(currentUser);
    });
    expect(sessionStorage.getItem("skillmatch.session")).toBeNull();
    expect(localStorage.getItem("skillmatch.session")).toBeNull();
    expect(useAuthStore.getState().accessToken).toBe(loginResponse.accessToken);
  });

  it("displays backend failures without storing an authenticated session", async () => {
    vi.mocked(authService.login).mockRejectedValue({
      isAxiosError: true,
      response: { data: { message: "Invalid email or password" } },
    });
    renderLogin();

    fireEvent.change(screen.getByLabelText("Email address"), {
      target: { value: "student@college.edu" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "incorrect-password" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Sign In" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password");
    expect(useAuthStore.getState().user).toBeNull();
  });
});
