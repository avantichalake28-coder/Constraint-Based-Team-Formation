import { fireEvent, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";
import { AppProviders } from "../../../app/providers";
import { useThemeStore } from "../../../stores/themeStore";
import { AvatarSettingsPage } from "./AvatarSettingsPage";
import { studentAvatarRegistry, studentCharacterIds } from "../avatar/avatarRegistry";
import { useStudentAvatarStore } from "../avatar/avatarStore";

describe("avatar settings", () => {
  beforeEach(() => {
    localStorage.clear();
    useStudentAvatarStore.setState({ selectedCharacterId: "avatar-01" });
    useThemeStore.setState({ theme: "aurora", mode: "light" });
  });

  it("shows all 16 originals and saves the selected character in this browser", () => {
    render(
      <AppProviders>
        <MemoryRouter>
          <AvatarSettingsPage />
        </MemoryRouter>
      </AppProviders>,
    );

    const options = screen.getByRole("group", { name: "Student characters" });
    expect(within(options).getAllByRole("button")).toHaveLength(16);
    expect(within(options).getAllByRole("img")).toHaveLength(16);
    expect(screen.getByText("16 original characters")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Select Avatar 16" }));

    expect(useStudentAvatarStore.getState().selectedCharacterId).toBe("avatar-16");
    expect(localStorage.getItem("skillmatch.student-avatar")).toContain(
      '"selectedCharacterId":"avatar-16"',
    );
    expect(
      screen.getByRole("button", { name: "Select Avatar 16" }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(
      screen.getByRole("button", { name: "Select Avatar 1" }),
    ).toHaveAttribute("aria-pressed", "false");
    expect(studentAvatarRegistry[studentCharacterIds[15]!].filename).toBe(
      "Avatar 16.jpg",
    );

    fireEvent.click(screen.getByRole("button", { name: /choose theme/i }));
    fireEvent.click(screen.getByRole("button", { name: "Glass Horizon" }));
    fireEvent.click(screen.getByRole("button", { name: "Dark" }));
    expect(useStudentAvatarStore.getState().selectedCharacterId).toBe("avatar-16");
    expect(
      screen.getByRole("button", { name: "Select Avatar 16" }),
    ).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(screen.getByRole("button", { name: "Light" }));
    fireEvent.click(screen.getByRole("button", { name: /choose theme/i }));
    fireEvent.click(screen.getByRole("button", { name: "Aurora" }));
    expect(useStudentAvatarStore.getState().selectedCharacterId).toBe("avatar-16");
  });
});
