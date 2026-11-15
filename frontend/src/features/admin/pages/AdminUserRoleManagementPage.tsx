import { useAuthStore } from "../../../stores/authStore";

export function AdminUserRoleManagementPage() {
  const user = useAuthStore((state) => state.user);

  return (
    <>
      <header className="student-page-header">
        <p className="student-page-header__eyebrow">ACCESS CONTROL</p>
        <h1>Roles &amp; permissions</h1>
        <p>The backend is authoritative for account roles and resource access.</p>
      </header>
      <section className="student-card">
        <div className="student-card__heading">
          <div><p className="student-card__eyebrow">CURRENT SESSION</p><h2>Signed-in administrator</h2></div>
        </div>
        <div className="student-project-list">
          <div className="student-project-row"><div><h3>{user?.name ?? "Administrator"}</h3><p>{user?.email ?? "Email unavailable"}</p></div><span className="student-status">{user?.role ?? "ADMIN"}</span></div>
        </div>
        <p className="student-note">The backend does not expose APIs to list users, inspect other accounts, change roles, or manage permissions. Mentor creation is available on the Mentor Management page; public registration creates Student accounts.</p>
      </section>
    </>
  );
}
