import { Check, Moon, Palette, Sun } from "lucide-react";
import { useState } from "react";
import { useThemeStore, type ThemeName } from "../stores/themeStore";

const themes: { id: ThemeName; label: string }[] = [
  { id: "aurora", label: "Aurora" },
  { id: "glass-horizon", label: "Glass Horizon" },
];

export function ThemeControls() {
  const [isThemeMenuOpen, setIsThemeMenuOpen] = useState(false);
  const theme = useThemeStore((state) => state.theme);
  const mode = useThemeStore((state) => state.mode);
  const setTheme = useThemeStore((state) => state.setTheme);
  const setMode = useThemeStore((state) => state.setMode);

  return (
    <div className="theme-controls" aria-label="Appearance settings">
      <div className="theme-menu-wrap">
        <button
          aria-expanded={isThemeMenuOpen}
          aria-label={`Choose theme. Current theme: ${themes.find((item) => item.id === theme)?.label}`}
          className="theme-menu-trigger"
          onClick={() => setIsThemeMenuOpen((open) => !open)}
          title="Choose a theme"
          type="button"
        >
          <Palette aria-hidden="true" size={16} />
        </button>
        {isThemeMenuOpen && (
          <div className="theme-menu" role="group" aria-label="Theme">
            {themes.map((item) => (
              <button
                aria-pressed={theme === item.id}
                className="theme-menu__option"
                key={item.id}
                onClick={() => {
                  setTheme(item.id);
                  setIsThemeMenuOpen(false);
                }}
                type="button"
              >
                <span>{item.label}</span>
                {theme === item.id && <Check aria-hidden="true" size={15} />}
              </button>
            ))}
          </div>
        )}
      </div>
      <div aria-label="Color mode" className="mode-toggle" role="group">
        <button
          aria-pressed={mode === "light"}
          className={`mode-toggle__option${mode === "light" ? " is-active" : ""}`}
          onClick={() => setMode("light")}
          type="button"
        >
          <Sun aria-hidden="true" size={15} />
          <span>Light</span>
        </button>
        <button
          aria-pressed={mode === "dark"}
          className={`mode-toggle__option${mode === "dark" ? " is-active" : ""}`}
          onClick={() => setMode("dark")}
          type="button"
        >
          <Moon aria-hidden="true" size={15} />
          <span>Dark</span>
        </button>
      </div>
    </div>
  );
}
