import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { Link } from "react-router-dom";
import { LoaderCircle } from "lucide-react";
import { adminService, type AdminOverview } from "../adminService";
import { useAuthStore } from "../../../stores/authStore";
import { getApiErrorMessage } from "../../../utils/apiError";

function isEmptyOverview(data: AdminOverview): boolean {
  const hasStudents = data.studentCount > 0;
  const hasMentors = data.mentorCount > 0;
  const hasProjects = Object.values(data.projectsByStatus ?? {}).some((count) => count > 0);
  const hasJobs = Object.values(data.formationJobsByStatus ?? {}).some((count) => count > 0);
  return !hasStudents && !hasMentors && !hasProjects && !hasJobs;
}

export function AdminDashboardPage() {
  const name = useAuthStore((state) => state.user?.name ?? "Administrator");
  const overview = useQuery({
    queryKey: ["admin", "overview"],
    queryFn: adminService.getOverview,
  });

  useEffect(() => {
    document.title = "Admin Dashboard | SkillMatch";
  }, []);

  return (
    <>
      <header className="student-page-header">
        <p className="student-page-header__eyebrow">ADMINISTRATOR WORKSPACE</p>
        <h1>Welcome, {name}</h1>
        <p>Real-time system overview and administration metrics across accounts, projects, and formation jobs.</p>
      </header>

      <section className="student-card">
        <div className="student-card__heading">
          <div>
            <p className="student-card__eyebrow">SYSTEM OVERVIEW</p>
            <h2>Platform counts & metrics</h2>
          </div>
          <button
            className="student-button student-button--secondary"
            disabled={overview.isFetching}
            onClick={() => void overview.refetch()}
            type="button"
          >
            Refresh
          </button>
        </div>

        {overview.isPending ? (
          <div aria-live="polite" className="student-state">
            <LoaderCircle aria-hidden="true" className="spin" size={22} />
            <span>Loading system overview…</span>
          </div>
        ) : overview.isError ? (
          <div className="student-state student-state--error" role="alert">
            <strong>Could not load system overview.</strong>
            <p>{getApiErrorMessage(overview.error)}</p>
            <button
              className="student-button student-button--secondary"
              onClick={() => void overview.refetch()}
              type="button"
            >
              Try again
            </button>
          </div>
        ) : isEmptyOverview(overview.data) ? (
          <p className="student-empty">No system activity recorded yet.</p>
        ) : (
          <div className="student-card-grid student-card-grid--dashboard" style={{ marginTop: "1rem" }}>
            <div className="student-preference-overview__item">
              <strong>Accounts</strong>
              <div style={{ display: "flex", gap: "1.5rem", marginTop: "0.5rem" }}>
                <div>
                  <span style={{ fontSize: "1.5rem", fontWeight: "bold", display: "block" }}>
                    {overview.data.studentCount}
                  </span>
                  <span className="student-field__hint">Students</span>
                </div>
                <div>
                  <span style={{ fontSize: "1.5rem", fontWeight: "bold", display: "block" }}>
                    {overview.data.mentorCount}
                  </span>
                  <span className="student-field__hint">Mentors</span>
                </div>
              </div>
            </div>

            <div className="student-preference-overview__item">
              <strong>Projects by Status</strong>
              <div style={{ display: "flex", gap: "1rem", marginTop: "0.5rem" }}>
                <div>
                  <span style={{ fontSize: "1.2rem", fontWeight: "bold", display: "block" }}>
                    {overview.data.projectsByStatus?.DRAFT ?? 0}
                  </span>
                  <span className="student-field__hint">Draft</span>
                </div>
                <div>
                  <span style={{ fontSize: "1.2rem", fontWeight: "bold", display: "block" }}>
                    {overview.data.projectsByStatus?.PUBLISHED ?? 0}
                  </span>
                  <span className="student-field__hint">Published</span>
                </div>
                <div>
                  <span style={{ fontSize: "1.2rem", fontWeight: "bold", display: "block" }}>
                    {overview.data.projectsByStatus?.COMPLETED ?? 0}
                  </span>
                  <span className="student-field__hint">Completed</span>
                </div>
              </div>
            </div>

            <div className="student-preference-overview__item" style={{ gridColumn: "1 / -1" }}>
              <strong>Formation Jobs by Status</strong>
              <div style={{ display: "flex", gap: "1.5rem", marginTop: "0.5rem" }}>
                <div>
                  <span style={{ fontSize: "1.2rem", fontWeight: "bold", display: "block" }}>
                    {overview.data.formationJobsByStatus?.QUEUED ?? 0}
                  </span>
                  <span className="student-field__hint">Queued</span>
                </div>
                <div>
                  <span style={{ fontSize: "1.2rem", fontWeight: "bold", display: "block" }}>
                    {overview.data.formationJobsByStatus?.RUNNING ?? 0}
                  </span>
                  <span className="student-field__hint">Running</span>
                </div>
                <div>
                  <span style={{ fontSize: "1.2rem", fontWeight: "bold", display: "block" }}>
                    {overview.data.formationJobsByStatus?.COMPLETED ?? 0}
                  </span>
                  <span className="student-field__hint">Completed</span>
                </div>
                <div>
                  <span style={{ fontSize: "1.2rem", fontWeight: "bold", display: "block" }}>
                    {overview.data.formationJobsByStatus?.FAILED ?? 0}
                  </span>
                  <span className="student-field__hint">Failed</span>
                </div>
              </div>
            </div>
          </div>
        )}
      </section>

      <section className="student-card">
        <div className="student-card__heading">
          <div>
            <p className="student-card__eyebrow">AVAILABLE ACTIONS</p>
            <h2>Administrative tools</h2>
          </div>
        </div>
        <div className="student-project-list">
          <Link className="student-project-row" to="/admin/students">
            <div>
              <h3>Student management</h3>
              <p>Browse, search, and inspect enrolled student profiles and project counts.</p>
            </div>
            <span className="student-text-link">Open</span>
          </Link>
          <Link className="student-project-row" to="/admin/mentors">
            <div>
              <h3>Mentor management</h3>
              <p>Create new mentor accounts and browse active mentors with project counts.</p>
            </div>
            <span className="student-text-link">Open</span>
          </Link>
          <Link className="student-project-row" to="/admin/team-formation">
            <div>
              <h3>Team formation</h3>
              <p>Review projects, roster sizes, lock states, and formation job statuses.</p>
            </div>
            <span className="student-text-link">Open</span>
          </Link>
        </div>
      </section>
    </>
  );
}
