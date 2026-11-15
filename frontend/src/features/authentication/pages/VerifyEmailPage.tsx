import { useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { Notification } from "../../../components/Notification";
import { authService } from "../../../services/auth/authService";
import { getApiErrorMessage } from "../../../utils/apiError";

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");
  const verification = useQuery({
    queryKey: ["auth", "verify-email", token],
    queryFn: () => authService.verifyEmail(token!),
    enabled: Boolean(token),
    retry: false,
  });

  return (
    <div className="auth-form auth-form--message">
      <header className="auth-form__header">
        <h1>Verify your email</h1>
        <p>One quick step to get started with SkillMatch.</p>
      </header>
      {!token ? (
        <Notification kind="info">
          Open the verification link in the email we sent you to activate your account.
        </Notification>
      ) : verification.isPending ? (
        <div className="verify-progress" role="status">
          <span className="loading-spinner" />
          <span>Verifying your email…</span>
        </div>
      ) : verification.isError ? (
        <Notification kind="error">{getApiErrorMessage(verification.error)}</Notification>
      ) : (
        <Notification kind="success">Your email is verified. You can now sign in.</Notification>
      )}
      <Link className="primary-button primary-button--link" to="/login">
        <span>Back to sign in</span>
      </Link>
    </div>
  );
}
