import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AdminProtectedRoute, MentorProtectedRoute, ProtectedRoute } from "./ProtectedRoute";
import { authService } from "../../services/auth/authService";
import { useAuthStore } from "../../stores/authStore";
import type { CurrentUser } from "../../types/auth";

vi.mock("../../services/auth/authService", () => ({
  authService: {
    me: vi.fn(),
  },
}));

const user: CurrentUser = {
  id: 8,
  name: "Student Example",
  email: "student@college.edu",
  role: "STUDENT",
  srn: "02FE24BCS141",
};

function renderProtectedRoute() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/private"]}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route element={<h1>Protected content</h1>} path="/private" />
          </Route>
          <Route element={<h1>Sign in page</h1>} path="/login" />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function renderRoleGuard(
  requiredRole: "STUDENT" | "MENTOR" | "ADMIN",
  actualRole: CurrentUser["role"],
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const Guard = {
    STUDENT: ProtectedRoute,
    MENTOR: MentorProtectedRoute,
    ADMIN: AdminProtectedRoute,
  }[requiredRole];
  vi.mocked(authService.me).mockResolvedValue({
    ...user,
    role: actualRole,
    srn: actualRole === "STUDENT" ? user.srn : null,
  });
  useAuthStore.setState({ accessToken: "valid-token", user: null });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/restricted"]}>
        <Routes>
          <Route element={<Guard />}>
            <Route element={<h1>Protected content</h1>} path="/restricted" />
          </Route>
          <Route element={<h1>Student workspace</h1>} path="/app" />
          <Route element={<h1>Mentor workspace</h1>} path="/mentor" />
          <Route element={<h1>Admin workspace</h1>} path="/admin" />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("protected route", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.getState().signOut();
  });

  it("redirects unauthenticated visitors to sign in", () => {
    renderProtectedRoute();
    expect(screen.getByRole("heading", { name: "Sign in page" })).toBeInTheDocument();
    expect(authService.me).not.toHaveBeenCalled();
  });

  it("checks the current user before rendering protected content", async () => {
    vi.mocked(authService.me).mockResolvedValue(user);
    useAuthStore.setState({ accessToken: "valid-token", user: null });
    renderProtectedRoute();

    expect(await screen.findByRole("heading", { name: "Protected content" })).toBeInTheDocument();
    expect(authService.me).toHaveBeenCalledOnce();
  });

  it("rejects expired or invalid sessions and clears the token", async () => {
    vi.mocked(authService.me).mockRejectedValue(new Error("Unauthorized"));
    useAuthStore.setState({ accessToken: "invalid-token", user });
    renderProtectedRoute();

    expect(await screen.findByRole("heading", { name: "Sign in page" })).toBeInTheDocument();
    await waitFor(() => {
      expect(useAuthStore.getState().accessToken).toBeNull();
    });
  });

  it.each([
    ["STUDENT", "MENTOR", "Student workspace"],
    ["STUDENT", "ADMIN", "Student workspace"],
    ["MENTOR", "STUDENT", "Mentor workspace"],
    ["MENTOR", "ADMIN", "Mentor workspace"],
    ["ADMIN", "STUDENT", "Admin workspace"],
    ["ADMIN", "MENTOR", "Admin workspace"],
  ] as const)(
    "keeps %s accounts out of the %s route and redirects to their own workspace",
    async (requiredRole, actualRole, ownWorkspace) => {
      renderRoleGuard(actualRole, requiredRole);
      expect(await screen.findByRole("heading", { name: ownWorkspace })).toBeInTheDocument();
      expect(screen.queryByRole("heading", { name: "Protected content" })).not.toBeInTheDocument();
    },
  );
});
