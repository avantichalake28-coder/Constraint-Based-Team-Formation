import { Github } from "lucide-react";

export function SocialSignIn({ register = false }: { register?: boolean }) {
  const action = register ? "Sign up" : "Continue";

  return (
    <>
      <div aria-hidden="true" className="auth-divider">
        <span />
        <span>or</span>
        <span />
      </div>
      <div className="social-options">
        <button
          aria-describedby="social-unavailable"
          className="social-button"
          disabled
          title="Google sign-in is not configured in the backend."
          type="button"
        >
          <span aria-hidden="true" className="google-mark">G</span>
          <span>{action} with Google</span>
        </button>
        <button
          aria-describedby="social-unavailable"
          className="social-button"
          disabled
          title="GitHub sign-in is not configured in the backend."
          type="button"
        >
          <Github aria-hidden="true" size={18} />
          <span>{action} with GitHub</span>
        </button>
      </div>
      <p className="sr-only" id="social-unavailable">
        Google and GitHub sign-in are not available.
      </p>
    </>
  );
}
