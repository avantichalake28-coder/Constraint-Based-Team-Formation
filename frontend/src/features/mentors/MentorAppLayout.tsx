import {
  BarChart3,
  Bell,
  BriefcaseBusiness,
  CircleUserRound,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  Menu,
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
  { label: "Dashboard", to: "/mentor", icon: LayoutDashboard, end: true },
  { label: "Profile", to: "/mentor/profile", icon: CircleUserRound },
  { label: "Projects", to: "/mentor/projects", icon: BriefcaseBusiness },
  { label: "Students", to: "/mentor/students", icon: UsersRound },
  { label: "Teams", to: "/mentor/teams", icon: BarChart3 },
  { label: "Notifications", to: "/mentor/notifications", icon: Bell },
  { label: "Settings", to: "/mentor/settings", icon: Settings },
] as const;

export function MentorAppLayout() {
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
          <span className="student-brand__mark"><Sparkles size={20} /></span>
          <span>SkillMatch Mentor</span>
          <button
            aria-label="Close menu"
            className="student-icon-button student-sidebar__close"
            onClick={closeMenu}
            type="button"
          >
            <X size={18} />
          </button>
        </div>
        <p className="student-sidebar__caption">FACULTY WORKSPACE</p>
        <nav aria-label="Mentor navigation" className="student-sidebar__nav">
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
          <span>Guide student teams with confidence.</span>
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
            <span className="role-badge role-badge--mentor">Faculty Mentor</span>
            <span className="student-app__account-mark student-app__account-mark--mentor">
              {(user?.name || "M").trim().charAt(0).toUpperCase()}
            </span>
            <span className="student-app__account-name">{user?.name || "Mentor"}</span>
          </div>
        </header>
        <main className="student-app__content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
