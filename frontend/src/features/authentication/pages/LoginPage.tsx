import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, LoaderCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { EmailField } from "../../../components/EmailField";
import { Notification } from "../../../components/Notification";
import { PasswordField } from "../../../components/PasswordField";
import { SocialSignIn } from "../../../components/SocialSignIn";
import { setAccessToken } from "../../../services/auth/accessToken";
import { authService } from "../../../services/auth/authService";
import { useAuthStore } from "../../../stores/authStore";
import { getApiErrorMessage } from "../../../utils/apiError";
import { loginSchema, type LoginFormValues } from "../schemas/authSchemas";

export function LoginPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const accessToken = useAuthStore((state) => state.accessToken);
  const storedUser = useAuthStore((state) => state.user);
  const signIn = useAuthStore((state) => state.signIn);
  const signOut = useAuthStore((state) => state.signOut);
  const setUser = useAuthStore((state) => state.setUser);
  const sessionUser = useQuery({
    queryKey: ["auth", "me"],
    queryFn: authService.me,
    enabled: Boolean(accessToken && !storedUser),
    retry: false,
  });
  useEffect(() => {
    if (sessionUser.data) setUser(sessionUser.data);
  }, [sessionUser.data, setUser]);
  useEffect(() => {
    if (accessToken && sessionUser.isError) signOut();
  }, [accessToken, sessionUser.isError, signOut]);
  const [serverError, setServerError] = useState("");
  const [infoMessage, setInfoMessage] = useState("");
  const [isResending, setIsResending] = useState(false);
  const [resendMessage, setResendMessage] = useState("");
  const [resendError, setResendError] = useState("");
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    getValues,
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "", rememberMe: true },
  });

  if (accessToken) {
    const role = storedUser?.role ?? sessionUser.data?.role;
    if (role) {
      const destination = role === "ADMIN" ? "/admin" : role === "MENTOR" ? "/mentor" : "/app";
      return <Navigate to={destination} replace />;
    }
    if (sessionUser.isError) {
      return <Navigate to="/login" replace state={{ reason: "session" }} />;
    }
    return (
      <div aria-live="polite" className="protected-loading">
        <span className="loading-spinner" />
        <span>Checking your session…</span>
      </div>
    );
  }

  const onSubmit = handleSubmit(async ({ email, password, rememberMe }) => {
    setServerError("");
    setInfoMessage("");
    setResendMessage("");
    setResendError("");

    try {
      const login = await authService.login({ email, password });
      setAccessToken(login.accessToken, login.expiresInSeconds, rememberMe);
      const user = await authService.me();

      if (user.role === "MENTOR" || user.role === "ADMIN") {
        signIn(user);
        queryClient.setQueryData(["auth", "me"], user);
        await navigate(user.role === "ADMIN" ? "/admin" : "/mentor", { replace: true });
        return;
      }

      if (user.role !== "STUDENT") {
        signOut();
        setServerError("This account is not authorized for the student or mentor portals.");
        return;
      }

      signIn(user);
      queryClient.setQueryData(["auth", "me"], user);
      await navigate("/app", { replace: true });
    } catch (error) {
      signOut();
      setServerError(getApiErrorMessage(error, "Unable to sign in. Please try again."));
    }
  });

  async function resendVerification() {
    const email = getValues("email").trim();
    if (!email) {
      setInfoMessage("Enter your college email address first.");
      return;
    }

    setIsResending(true);
    setResendMessage("");
    setResendError("");
    try {
      await authService.resendVerification(email);
      setResendMessage("If your account needs verification, a new link is on its way.");
    } catch (error) {
      setResendError(getApiErrorMessage(error));
    } finally {
      setIsResending(false);
    }
  }

  return (
    <div className="auth-form">
      <header className="auth-form__header">
        <h1>Welcome Back!</h1>
        <p>Sign in to continue your journey</p>
      </header>

      <form className="login-form" noValidate onSubmit={onSubmit}>
        {serverError && (
          <Notification kind="error">
            <span>{serverError}</span>
            {serverError.toLowerCase().includes("verify") && (
              <button
                className="text-action"
                disabled={isResending}
                onClick={resendVerification}
                type="button"
              >
                {isResending ? "Sending…" : "Resend verification email"}
              </button>
            )}
          </Notification>
        )}
        {infoMessage && <Notification kind="info">{infoMessage}</Notification>}
        {resendMessage && <Notification kind="info">{resendMessage}</Notification>}
        {resendError && <Notification kind="error">{resendError}</Notification>}

        <div className="form-fields">
          <EmailField
            error={errors.email?.message}
            label="Email address"
            name="email"
            registration={register("email")}
          />
          <PasswordField
            error={errors.password?.message}
            label="Password"
            name="password"
            registration={register("password")}
          />
        </div>

        <div className="login-options">
          <label className="remember-option">
            <input {...register("rememberMe")} type="checkbox" />
            <span>Remember me</span>
          </label>
          <button
            className="link-button"
            onClick={() =>
              setInfoMessage(
                "Password reset is not available yet. Contact your SkillMatch administrator for help.",
              )
            }
            type="button"
          >
            Forgot password?
          </button>
        </div>

        <button className="primary-button" disabled={isSubmitting} type="submit">
          {isSubmitting ? (
            <>
              <LoaderCircle aria-hidden="true" className="spin" size={18} />
              <span>Signing in…</span>
            </>
          ) : (
            <>
              <span>Sign In</span>
              <ArrowRight aria-hidden="true" size={19} />
            </>
          )}
        </button>
      </form>

      <SocialSignIn />
      <p className="auth-switch">
        Don’t have an account? <Link to="/register">Register here</Link>
      </p>
    </div>
  );
}
