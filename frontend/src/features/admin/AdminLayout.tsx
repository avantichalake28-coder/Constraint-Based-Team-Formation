import {
  BarChart3,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  Menu,
  Network,
  Settings,
  Sparkles,
  UsersRound,
  X,
} from "lucide-react";
import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { ThemeControls } from "../../components/ThemeControls";
import { useAuthStore } from "../../stores/authStore";

const navigation = [
  { label: "Dashboard", to: "/admin", icon: LayoutDashboard, end: true },
  { label: "Student management", to: "/admin/students", icon: UsersRound },
  { label: "Mentor management", to: "/admin/mentors", icon: Sparkles },
  { label: "Team formation", to: "/admin/team-formation", icon: Network },
  { label: "Analytics", to: "/admin/analytics", icon: BarChart3 },
  { label: "System settings", to: "/admin/settings", icon: Settings },
  { label: "Roles & permissions", to: "/admin/user-role-management", icon: UsersRound },
] as const;

export function AdminLayout() {
  const [menuOpen, setMenuOpen] = useState(false);
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);
  const signOut = useAuthStore((state) => state.signOut);

  function logout() {
    signOut();
    void navigate("/login", { replace: true });
  }

  return (
    <div className="student-app">
      {menuOpen && (
        <button
          aria-label="Close navigation"
          className="student-app__scrim"
          onClick={() => setMenuOpen(false)}
          type="button"
        />
      )}
      <aside className={`student-sidebar${menuOpen ? " student-sidebar--open" : ""}`}>
        <div className="student-sidebar__brand">
          <span className="student-brand__mark"><Network size={20} /></span>
          <span>SkillMatch Admin</span>
          <button
            aria-label="Close menu"
            className="student-icon-button student-sidebar__close"
            onClick={() => setMenuOpen(false)}
            type="button"
          >
            <X size={18} />
          </button>
        </div>
        <p className="student-sidebar__caption">ADMINISTRATOR WORKSPACE</p>
        <nav aria-label="Admin navigation" className="student-sidebar__nav">
          {navigation.map(({ label, to, icon: Icon, ...rest }) => (
            <NavLink
              className={({ isActive }) =>
                `student-sidebar__link${isActive ? " student-sidebar__link--active" : ""}`
              }
              end={"end" in rest ? rest.end : false}
              key={to}
              onClick={() => setMenuOpen(false)}
              to={to}
            >
              <Icon aria-hidden="true" size={18} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <button className="student-sidebar__logout" onClick={logout} type="button">
          <LogOut aria-hidden="true" size={18} />
          <span>Logout</span>
        </button>
        <div className="student-sidebar__footnote">
          <GraduationCap aria-hidden="true" size={17} />
          <span>SkillMatch Team Formation System</span>
        </div>
      </aside>

      <div className="student-app__main">
        <header className="student-app__topbar">
          <button
            aria-expanded={menuOpen}
            aria-label={menuOpen ? "Close navigation" : "Open navigation"}
            className="student-icon-button student-app__menu"
            onClick={() => setMenuOpen((open) => !open)}
            type="button"
          >
            <Menu size={20} />
          </button>
          <div className="student-app__topbar-spacer" />
          <ThemeControls />
          <div className="student-app__account">
            <span className="role-badge role-badge--admin">System Admin</span>
            <span className="student-app__account-mark student-app__account-mark--admin">
              {(user?.name || "A").trim().charAt(0).toUpperCase()}
            </span>
            <span className="student-app__account-name">{user?.name || "Admin"}</span>
          </div>
        </header>
        <main className="student-app__content"><Outlet /></main>
      </div>
    </div>
  );
}
