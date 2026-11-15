import {
  Bell,
  BriefcaseBusiness,
  CircleUserRound,
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
import { useAuthStore } from "../../stores/authStore";
import { ThemeControls } from "../../components/ThemeControls";

const navigation = [
  { label: "Dashboard", to: "/app", icon: LayoutDashboard, end: true },
  { label: "My Profile", to: "/app/profile", icon: CircleUserRound },
  { label: "Skills & Interests", to: "/app/skills", icon: Sparkles },
  { label: "Project Preferences", to: "/app/preferences", icon: BriefcaseBusiness },
  { label: "Team Formation", to: "/app/formation", icon: Network },
  { label: "My Team", to: "/app/team", icon: UsersRound },
  { label: "Notifications", to: "/app/notifications", icon: Bell },
  { label: "Settings", to: "/app/settings", icon: Settings },
] as const;

export function StudentAppLayout() {
  const [menuOpen, setMenuOpen] = useState(false);
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);
  const signOut = useAuthStore((state) => state.signOut);

  function logout() {
    signOut();
    void navigate("/login", { replace: true });
  }

  function closeMenu() {
    setMenuOpen(false);
  }

  return (
    <div className="student-app">
      {menuOpen && (
        <button
          aria-label="Close navigation"
          className="student-app__scrim"
          onClick={closeMenu}
          type="button"
        />
      )}
      <aside className={`student-sidebar${menuOpen ? " student-sidebar--open" : ""}`}>
        <div className="student-sidebar__brand">
          <span className="student-brand__mark"><Network size={20} /></span>
          <span>SkillMatch</span>
          <button
            aria-label="Close menu"
            className="student-icon-button student-sidebar__close"
            onClick={closeMenu}
            type="button"
          >
            <X size={18} />
          </button>
        </div>
        <p className="student-sidebar__caption">STUDENT WORKSPACE</p>
        <nav aria-label="Student navigation" className="student-sidebar__nav">
          {navigation.map(({ label, to, icon: Icon, ...rest }) => (
            <NavLink
              className={({ isActive }) =>
                `student-sidebar__link${isActive ? " student-sidebar__link--active" : ""}`
              }
              end={"end" in rest ? rest.end : false}
              key={to}
              onClick={closeMenu}
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
          <span>Build your next great team.</span>
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
            <span className="role-badge role-badge--student">Student</span>
            <span className="student-app__account-mark">
              {(user?.name || "S").trim().charAt(0).toUpperCase()}
            </span>
            <span className="student-app__account-name">{user?.name || "Student"}</span>
          </div>
        </header>
        <main className="student-app__content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export function PageHeader({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: string;
  description: string;
}) {
  return (
    <header className="student-page-header">
      <p className="student-page-header__eyebrow">{eyebrow}</p>
      <h1>{title}</h1>
      <p>{description}</p>
    </header>
  );
}
