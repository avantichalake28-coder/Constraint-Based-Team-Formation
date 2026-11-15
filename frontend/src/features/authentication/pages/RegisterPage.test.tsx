import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RegisterPage } from "./RegisterPage";
import { authService } from "../../../services/auth/authService";

vi.mock("../../../services/auth/authService", () => ({
  authService: {
    register: vi.fn(),
  },
}));

function renderRegister() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("registration form", () => {
  beforeEach(() => vi.clearAllMocks());

  it("rejects email addresses outside the configured college domain", async () => {
    renderRegister();

    fireEvent.change(screen.getByLabelText("Email address"), {
      target: { value: "student@gmail.com" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Sign Up" }));

    expect(await screen.findByText("Use an @kletech.ac.in email address.")).toBeInTheDocument();
    expect(authService.register).not.toHaveBeenCalled();
  });

  it("submits only the fields supported by the backend and reports verification", async () => {
    vi.mocked(authService.register).mockResolvedValue({
      id: 4,
      email: "student@kletech.ac.in",
    });
    renderRegister();

    expect(screen.getByText("Use your @kletech.ac.in college email address.")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Full Name"), {
      target: { value: "Student Example" },
    });
    fireEvent.change(screen.getByLabelText("Email address"), {
      target: { value: "student@kletech.ac.in" },
    });
    fireEvent.change(screen.getByLabelText("Student registration number"), {
      target: { value: "02FE24BCS141" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "a-secure-password" },
    });
    fireEvent.change(screen.getByLabelText("Confirm Password"), {
      target: { value: "a-secure-password" },
    });
    fireEvent.click(screen.getByRole("checkbox", { name: /terms/i }));
    fireEvent.click(screen.getByRole("button", { name: "Sign Up" }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Check your college email to verify it",
    );
    expect(authService.register).toHaveBeenCalledWith({
      fullName: "Student Example",
      email: "student@kletech.ac.in",
      password: "a-secure-password",
      srn: "02FE24BCS141",
    });
  });
});
