import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowRight, LoaderCircle, UserRound } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { allowedEmailDomain } from "../../../config/emailDomain";
import { EmailField } from "../../../components/EmailField";
import { Notification } from "../../../components/Notification";
import { PasswordField } from "../../../components/PasswordField";
import { SocialSignIn } from "../../../components/SocialSignIn";
import { authService } from "../../../services/auth/authService";
import { getApiErrorMessage } from "../../../utils/apiError";
import { registerSchema, type RegisterFormValues } from "../schemas/authSchemas";

export function RegisterPage() {
  const [successMessage, setSuccessMessage] = useState("");
  const [serverError, setServerError] = useState("");
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      fullName: "",
      email: "",
      srn: "",
      password: "",
      confirmPassword: "",
      agreeToTerms: false,
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setServerError("");
    setSuccessMessage("");
    try {
      await authService.register({
        fullName: values.fullName,
        email: values.email,
        password: values.password,
        srn: values.srn,
      });
      setSuccessMessage(
        "Your account is ready. Check your college email to verify it before signing in.",
      );
    } catch (error) {
      setServerError(getApiErrorMessage(error, "Unable to create your account."));
    }
  });

  return (
    <div className="auth-form auth-form--register">
      <header className="auth-form__header">
        <h1>Create an account! <span aria-hidden="true">✨</span></h1>
        <p>Sign up to start your journey</p>
      </header>

      <form className="register-form" noValidate onSubmit={onSubmit}>
        {serverError && <Notification kind="error">{serverError}</Notification>}
        {successMessage && <Notification kind="success">{successMessage}</Notification>}

        <div className="form-fields">
          <div className="form-field">
            <label className="sr-only" htmlFor="fullName">Full Name</label>
            <div className={`input-shell${errors.fullName ? " input-shell--error" : ""}`}>
              <UserRound aria-hidden="true" className="input-shell__icon" size={18} />
              <input
                {...register("fullName")}
                aria-describedby={errors.fullName ? "fullName-error" : undefined}
                aria-invalid={Boolean(errors.fullName)}
                autoComplete="name"
                className="input-shell__input"
                id="fullName"
                placeholder="Full Name"
              />
            </div>
            {errors.fullName && <p className="field-error" id="fullName-error">{errors.fullName.message}</p>}
          </div>

          <EmailField
            error={errors.email?.message}
            hint={`Use your @${allowedEmailDomain} college email address.`}
            label="Email address"
            name="email"
            registration={register("email")}
          />

          <div className="form-field">
            <label className="sr-only" htmlFor="srn">Student registration number</label>
            <div className={`input-shell${errors.srn ? " input-shell--error" : ""}`}>
              <UserRound aria-hidden="true" className="input-shell__icon" size={18} />
              <input
                {...register("srn")}
                aria-describedby={errors.srn ? "srn-error" : undefined}
                aria-invalid={Boolean(errors.srn)}
                autoComplete="off"
                className="input-shell__input"
                id="srn"
                placeholder="Student registration number"
              />
            </div>
            {errors.srn && <p className="field-error" id="srn-error">{errors.srn.message}</p>}
          </div>

          <PasswordField
            error={errors.password?.message}
            label="Password"
            name="password"
            registration={register("password")}
          />
          <PasswordField
            error={errors.confirmPassword?.message}
            label="Confirm Password"
            name="confirmPassword"
            registration={register("confirmPassword")}
          />
        </div>

        <label className="terms-option">
          <input
            {...register("agreeToTerms")}
            aria-describedby={errors.agreeToTerms ? "terms-error" : undefined}
            aria-invalid={Boolean(errors.agreeToTerms)}
            type="checkbox"
          />
          <span>I agree to the <strong>Terms &amp; Conditions</strong></span>
        </label>
        {errors.agreeToTerms && (
          <p className="field-error field-error--terms" id="terms-error">
            {errors.agreeToTerms.message}
          </p>
        )}

        <button className="primary-button" disabled={isSubmitting} type="submit">
          {isSubmitting ? (
            <>
              <LoaderCircle aria-hidden="true" className="spin" size={18} />
              <span>Creating account…</span>
            </>
          ) : (
            <>
              <span>Sign Up</span>
              <ArrowRight aria-hidden="true" size={19} />
            </>
          )}
        </button>
      </form>

      <SocialSignIn register />
      <p className="auth-switch">
        Already have an account? <Link to="/login">Sign in here</Link>
      </p>
    </div>
  );
}
