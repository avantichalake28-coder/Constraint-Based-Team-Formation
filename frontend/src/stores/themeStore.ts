import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

export type ThemeName = "aurora" | "glass-horizon";
export type ColorMode = "light" | "dark";

interface ThemeState {
  theme: ThemeName;
  mode: ColorMode;
  setTheme: (theme: ThemeName) => void;
  setMode: (mode: ColorMode) => void;
}

function normalizeThemeState(value: Partial<ThemeState> | undefined): Pick<ThemeState, "theme" | "mode"> {
  const theme = value?.theme === "glass-horizon" ? "glass-horizon" : "aurora";
  const mode = value?.mode === "dark" ? "dark" : "light";
  return { theme, mode };
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      theme: "aurora",
      mode: "light",
      setTheme: (theme) => set({ theme }),
      setMode: (mode) => set({ mode }),
    }),
    {
      name: "skillmatch.appearance",
      storage: createJSONStorage(() => localStorage),
      partialize: ({ theme, mode }) => ({ theme, mode }),
      merge: (persistedState, currentState) => {
        const normalized = normalizeThemeState(persistedState as Partial<ThemeState> | undefined);
        return {
          ...currentState,
          ...normalized,
        };
      },
    },
  ),
);
