import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { LoaderCircle } from "lucide-react";
import { adminService, type ProjectStatus } from "../adminService";
import { getApiErrorMessage } from "../../../utils/apiError";

export function AdminTeamFormationPage() {
  const [statusFilter, setStatusFilter] = useState<ProjectStatus | "">("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    document.title = "Team Formation | SkillMatch Admin";
  }, []);

  const projectsQuery = useQuery({
    queryKey: ["admin", "projects", { status: statusFilter, page }],
    queryFn: () =>
      adminService.getProjects({
        status: statusFilter || undefined,
        page,
        size: 20,
      }),
  });

  return (
    <>
      <header className="student-page-header">
        <p className="student-page-header__eyebrow">FORMATION OPERATIONS</p>
        <h1>Team formation</h1>
        <p>Review project roster sizes, preference lock statuses, and algorithm formation run outcomes across all mentors.</p>
      </header>

      <section className="student-card">
        <div className="student-card__heading">
          <div>
            <p className="student-card__eyebrow">READ-ONLY DIRECTORY</p>
            <h2>Project formation status</h2>
          </div>
          <button
            className="student-button student-button--secondary"
            disabled={projectsQuery.isFetching}
            onClick={() => void projectsQuery.refetch()}
            type="button"
          >
            Refresh
          </button>
        </div>

        <div className="student-project-select">
          <label className="student-field" htmlFor="project-status-filter">
            Filter by project status
            <select
              id="project-status-filter"
              name="projectStatusFilter"
              onChange={(e) => {
                setStatusFilter(e.target.value as ProjectStatus | "");
                setPage(0);
              }}
              value={statusFilter}
            >
              <option value="">All statuses</option>
              <option value="DRAFT">Draft</option>
              <option value="PUBLISHED">Published</option>
              <option value="COMPLETED">Completed</option>
            </select>
          </label>
        </div>

        {projectsQuery.isPending ? (
          <div aria-live="polite" className="student-state">
            <LoaderCircle aria-hidden="true" className="spin" size={22} />
            <span>Loading projects…</span>
          </div>
        ) : projectsQuery.isError ? (
          <div className="student-state student-state--error" role="alert">
            <strong>Could not load projects.</strong>
            <p>{getApiErrorMessage(projectsQuery.error)}</p>
            <button
              className="student-button student-button--secondary"
              onClick={() => void projectsQuery.refetch()}
              type="button"
            >
              Try again
            </button>
          </div>
        ) : projectsQuery.data.content.length === 0 ? (
          <p className="student-empty">No projects found.</p>
        ) : (
          <>
            <div className="student-project-list">
              {projectsQuery.data.content.map((project) => (
                <article className="student-project-row" key={project.id}>
                  <div>
                    <h3>{project.title}</h3>
                    <p>
                      <strong>Mentor:</strong> {project.mentorName}
                    </p>
                  </div>
                  <div style={{ display: "flex", gap: "8px", alignItems: "center", flexWrap: "wrap" }}>
                    <span className="student-status">{project.status}</span>
                    <span className="student-status">
                      Roster: {project.rosterSize}
                    </span>
                    <span className={project.locked ? "student-status student-status--locked" : "student-status"}>
                      {project.locked ? "Locked" : "Unlocked"}
                    </span>
                    <span className="student-status">
                      Latest job: {project.latestFormationJobStatus ?? "None"}
                    </span>
                  </div>
                </article>
              ))}
            </div>

            <div
              className="student-pagination"
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                marginTop: "1.25rem",
                flexWrap: "wrap",
                gap: "10px",
              }}
            >
              <span className="student-field__hint">
                Page {projectsQuery.data.page + 1} of {Math.max(1, projectsQuery.data.totalPages)} ({projectsQuery.data.totalElements} {projectsQuery.data.totalElements === 1 ? "project" : "projects"})
              </span>
              <div style={{ display: "flex", gap: "8px" }}>
                <button
                  aria-label="Previous page"
                  className="student-button student-button--secondary"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  type="button"
                >
                  Previous
                </button>
                <button
                  aria-label="Next page"
                  className="student-button student-button--secondary"
                  disabled={projectsQuery.data.last || page >= projectsQuery.data.totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  type="button"
                >
                  Next
                </button>
              </div>
            </div>
          </>
        )}
      </section>
    </>
  );
}
