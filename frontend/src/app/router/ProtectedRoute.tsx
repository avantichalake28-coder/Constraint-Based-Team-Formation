import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { authService } from "../../services/auth/authService";
import { useAuthStore } from "../../stores/authStore";

type AllowedRole = "STUDENT" | "MENTOR" | "ADMIN";

export function ProtectedRoute() {
  return <RoleProtectedRoute requiredRole="STUDENT" />;
}

export function MentorProtectedRoute() {
  return <RoleProtectedRoute requiredRole="MENTOR" />;
}

export function AdminProtectedRoute() {
  return <RoleProtectedRoute requiredRole="ADMIN" />;
}

function RoleProtectedRoute({ requiredRole }: { requiredRole: AllowedRole }) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const signOut = useAuthStore((state) => state.signOut);
  const setUser = useAuthStore((state) => state.setUser);
  const location = useLocation();
  const currentUser = useQuery({
    queryKey: ["auth", "me"],
    queryFn: authService.me,
    enabled: Boolean(accessToken),
    retry: false,
  });

  useEffect(() => {
    if (currentUser.data) setUser(currentUser.data);
  }, [currentUser.data, setUser]);

  if (!accessToken) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (currentUser.isPending) {
    return (
      <main className="protected-loading" aria-label="Checking your session">
        <span className="loading-spinner" />
        <span>Checking your session…</span>
      </main>
    );
  }
  if (currentUser.isError || !currentUser.data) {
    return <ExpiredSession onExpired={signOut} />;
  }
  if (currentUser.data.role !== requiredRole) {
    return <RoleMismatch role={currentUser.data.role} />;
  }

  return <Outlet />;
}

function ExpiredSession({ onExpired }: { onExpired: () => void }) {
  useEffect(() => {
    onExpired();
  }, [onExpired]);

  return <Navigate to="/login" replace state={{ reason: "session" }} />;
}

function RoleMismatch({ role }: { role: AllowedRole }) {
  const destination = role === "ADMIN" ? "/admin" : role === "MENTOR" ? "/mentor" : "/app";
  return <Navigate to={destination} replace state={{ reason: "role" }} />;
}
