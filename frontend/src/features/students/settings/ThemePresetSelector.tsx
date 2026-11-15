import { Check } from "lucide-react";
import {
  useThemeStore,
  type ColorMode,
  type ThemeName,
} from "../../../stores/themeStore";

const presets: {
  id: string;
  theme: ThemeName;
  mode: ColorMode;
  label: string;
}[] = [
  { id: "aurora-light", theme: "aurora", mode: "light", label: "Aurora Light" },
  { id: "aurora-dark", theme: "aurora", mode: "dark", label: "Aurora Dark" },
  {
    id: "glass-horizon-light",
    theme: "glass-horizon",
    mode: "light",
    label: "Glass Horizon Light",
  },
  {
    id: "glass-horizon-dark",
    theme: "glass-horizon",
    mode: "dark",
    label: "Glass Horizon Dark",
  },
];

export function ThemePresetSelector() {
  const theme = useThemeStore((state) => state.theme);
  const mode = useThemeStore((state) => state.mode);
  const setTheme = useThemeStore((state) => state.setTheme);
  const setMode = useThemeStore((state) => state.setMode);

  return (
    <div aria-label="Theme presets" className="theme-presets" role="group">
      {presets.map((preset) => {
        const selected = theme === preset.theme && mode === preset.mode;

        return (
          <button
            aria-pressed={selected}
            className={`theme-preset${selected ? " theme-preset--active" : ""}`}
            key={preset.id}
            onClick={() => {
              setTheme(preset.theme);
              setMode(preset.mode);
            }}
            type="button"
          >
            <span aria-hidden="true" className={`theme-preset__swatch theme-preset__swatch--${preset.theme} theme-preset__swatch--${preset.mode}`} />
            <span>{preset.label}</span>
            {selected && <Check aria-hidden="true" className="theme-preset__check" size={13} />}
          </button>
        );
      })}
    </div>
  );
}
