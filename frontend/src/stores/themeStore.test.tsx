import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { AppProviders } from "../app/providers";
import { ThemeControls } from "../components/ThemeControls";
import { useThemeStore } from "./themeStore";

describe("appearance controls", () => {
  beforeEach(() => {
    localStorage.clear();
    useThemeStore.setState({ theme: "aurora", mode: "light" });
    document.documentElement.dataset.theme = "aurora";
    document.documentElement.dataset.mode = "light";
  });

  it("supports both themes in both color modes and persists the selection", async () => {
    render(
      <AppProviders>
        <ThemeControls />
      </AppProviders>,
    );

    expect(document.documentElement).toHaveAttribute("data-theme", "aurora");
    expect(document.documentElement).toHaveAttribute("data-mode", "light");

    fireEvent.click(screen.getByRole("button", { name: "Dark" }));
    expect(document.documentElement).toHaveAttribute("data-theme", "aurora");
    expect(document.documentElement).toHaveAttribute("data-mode", "dark");

    fireEvent.click(screen.getByRole("button", { name: /choose theme/i }));
    fireEvent.click(screen.getByRole("button", { name: /glass horizon/i }));
    expect(document.documentElement).toHaveAttribute("data-theme", "glass-horizon");
    expect(document.documentElement).toHaveAttribute("data-mode", "dark");

    fireEvent.click(screen.getByRole("button", { name: "Light" }));
    expect(document.documentElement).toHaveAttribute("data-theme", "glass-horizon");
    expect(document.documentElement).toHaveAttribute("data-mode", "light");

    fireEvent.click(screen.getByRole("button", { name: /choose theme/i }));
    fireEvent.click(screen.getByRole("button", { name: "Aurora" }));
    expect(document.documentElement).toHaveAttribute("data-theme", "aurora");
    expect(document.documentElement).toHaveAttribute("data-mode", "light");
    expect(localStorage.getItem("skillmatch.appearance")).toContain('"theme":"aurora"');
  });
});
