import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";
import { useAuthStore } from "../../stores/authStore";
import { StudentAppLayout } from "./StudentAppLayout";

describe("student application navigation", () => {
  beforeEach(() => {
    useAuthStore.getState().signOut();
    useAuthStore.setState({
      accessToken: "test-token",
      user: {
        id: 8,
        name: "Student Example",
        email: "student@college.edu",
        role: "STUDENT",
        srn: "02FE24BCS141",
      },
    });
  });

  it("provides shared navigation and clears the session on logout", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/app/profile"]}>
        <Routes>
          <Route element={<StudentAppLayout />} path="/app">
            <Route element={<h1>Profile page</h1>} path="profile" />
          </Route>
          <Route element={<h1>Login page</h1>} path="/login" />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole("navigation", { name: "Student navigation" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "My Profile" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("heading", { name: "Profile page" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Logout" }));

    expect(await screen.findByRole("heading", { name: "Login page" })).toBeInTheDocument();
    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
  });
});
