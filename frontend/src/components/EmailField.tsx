import { Mail } from "lucide-react";
import type { InputHTMLAttributes } from "react";
import type { UseFormRegisterReturn } from "react-hook-form";

interface EmailFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  error?: string;
  hint?: string;
  label: string;
  name: string;
  registration: UseFormRegisterReturn;
}

export function EmailField({
  error,
  hint,
  label,
  name,
  registration,
  ...inputProps
}: EmailFieldProps) {
  const errorId = `${name}-error`;
  const hintId = `${name}-hint`;
  const describedBy = [hint ? hintId : undefined, error ? errorId : undefined]
    .filter(Boolean)
    .join(" ") || undefined;

  return (
    <div className="form-field">
      <label className="sr-only" htmlFor={name}>
        {label}
      </label>
      <div className={`input-shell${error ? " input-shell--error" : ""}`}>
        <Mail aria-hidden="true" className="input-shell__icon" size={18} />
        <input
          {...registration}
          {...inputProps}
          aria-describedby={describedBy}
          aria-invalid={Boolean(error)}
          autoComplete="email"
          className="input-shell__input"
          id={name}
          placeholder={label}
          type="email"
        />
      </div>
      {hint && (
        <p className="field-hint" id={hintId}>
          {hint}
        </p>
      )}
      {error && (
        <p className="field-error" id={errorId}>
          {error}
        </p>
      )}
    </div>
  );
}
