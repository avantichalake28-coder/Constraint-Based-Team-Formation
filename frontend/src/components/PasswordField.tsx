import { Eye, EyeOff, LockKeyhole } from "lucide-react";
import { useState } from "react";
import type { InputHTMLAttributes } from "react";
import type { UseFormRegisterReturn } from "react-hook-form";

interface PasswordFieldProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, "type"> {
  error?: string;
  label: string;
  name: string;
  registration: UseFormRegisterReturn;
}

export function PasswordField({
  error,
  label,
  name,
  registration,
  ...inputProps
}: PasswordFieldProps) {
  const [isVisible, setIsVisible] = useState(false);
  const errorId = `${name}-error`;

  return (
    <div className="form-field">
      <label className="sr-only" htmlFor={name}>
        {label}
      </label>
      <div className={`input-shell${error ? " input-shell--error" : ""}`}>
        <LockKeyhole aria-hidden="true" className="input-shell__icon" size={18} />
        <input
          {...registration}
          {...inputProps}
          aria-describedby={error ? errorId : undefined}
          aria-invalid={Boolean(error)}
          autoComplete={name === "password" ? "current-password" : "new-password"}
          className="input-shell__input"
          id={name}
          placeholder={label}
          type={isVisible ? "text" : "password"}
        />
        <button
          aria-label={isVisible ? "Hide password" : "Show password"}
          aria-pressed={isVisible}
          className="input-shell__trailing"
          onClick={() => setIsVisible((visible) => !visible)}
          type="button"
        >
          {isVisible ? <EyeOff aria-hidden="true" size={18} /> : <Eye aria-hidden="true" size={18} />}
        </button>
      </div>
      {error && (
        <p className="field-error" id={errorId}>
          {error}
        </p>
      )}
    </div>
  );
}
