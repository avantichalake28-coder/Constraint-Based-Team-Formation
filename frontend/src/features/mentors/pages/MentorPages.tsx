import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowRight,
  CircleAlert,
  LoaderCircle,
  Plus,
  Save,
  Search,
  ShieldCheck,
  Trash2,
} from "lucide-react";
import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Notification } from "../../../components/Notification";
import { getApiErrorMessage } from "../../../utils/apiError";
import { mentorService } from "../../../services/mentor/mentorService";

type Feedback = { kind: "success" | "error"; message: string };

function PageState({
  loading,
  error,
  onRetry,
  children,
}: {
  loading?: boolean;
  error?: string;
  onRetry?: () => void;
  children: ReactNode;
}) {
  if (loading) {
    return (
      <div aria-live="polite" className="student-state">
        <LoaderCircle aria-hidden="true" className="spin" size={22} />
        <span>Loading mentor data…</span>
      </div>
    );
  }
  if (error) {
    return (
      <div className="student-state student-state--error" role="alert">
        <CircleAlert aria-hidden="true" size={21} />
        <div>
          <strong>We couldn’t load this information.</strong>
          <p>{error}</p>
          {onRetry && (
            <button className="student-button student-button--secondary" onClick={onRetry} type="button">
              Try again
            </button>
          )}
        </div>
      </div>
    );
  }
  return <>{children}</>;
}

function FeedbackMessage({ feedback }: { feedback: Feedback | null }) {
  if (!feedback) return null;
  return <Notification kind={feedback.kind}>{feedback.message}</Notification>;
}

function MentorPageHeader({
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

function formatStatus(value: string | null | undefined) {
  return value ? value.replaceAll("_", " ").toLowerCase() : "Pending";
}

export function MentorDashboardPage() {
  const queryClient = useQueryClient();
  const profile = useQuery({ queryKey: ["mentor", "profile"], queryFn: mentorService.getProfile });
  const projects = useQuery({ queryKey: ["mentor", "projects"], queryFn: mentorService.getProjects });
  const projectList = projects.data?.content ?? [];

  useEffect(() => {
    document.title = "Mentor Dashboard | SkillMatch";
  }, []);

  const rosterSummary = useQuery({
    queryKey: ["mentor", "roster-summary"],
    queryFn: async () => {
      const results = await Promise.all(
        projectList.map(async (project) => mentorService.getRoster(project.id)),
      );
      return results.reduce((total, item) => total + item.content.length, 0);
    },
    enabled: projectList.length > 0,
  });

  const formationSummary = useQuery({
    queryKey: ["mentor", "formation-summary"],
    queryFn: async () => {
      const jobs = await Promise.all(
        projectList.map(async (project) => mentorService.listFormationJobs(project.id)),
      );
      return jobs.flatMap((item) => item.content);
    },
    enabled: projectList.length > 0,
  });

  const totalStudents = rosterSummary.data ?? 0;
  const completedJobs = formationSummary.data?.filter((job) => job.jobStatus === "COMPLETED").length ?? 0;
  const lockedProjects = projectList.filter((project) => project.preferencesLocked).length;

  return (
    <>
      <MentorPageHeader
        eyebrow="FACULTY WORKSPACE"
        title={`Welcome, ${profile.data?.name ?? "mentor"}`}
        description="Track your projects, rostered students, and team-formation activity from the backend APIs available to mentors."
      />

      <section className="student-welcome-card">
        <div>
          <p className="student-welcome-card__eyebrow">MENTOR OVERVIEW</p>
          <h2>Keep student team formation moving.</h2>
          <p>Review project readiness, rostered students, and formation jobs using the backed service layer and role-based mentorship permissions.</p>
        </div>
        <Link className="student-button" to="/mentor/projects">
          Manage projects <ArrowRight aria-hidden="true" size={17} />
        </Link>
      </section>

      <div className="student-card-grid student-card-grid--dashboard">
        <section className="student-card">
          <div className="student-card__heading">
            <div>
              <p className="student-card__eyebrow">PROJECTS</p>
              <h2>Assigned projects</h2>
            </div>
            <Link className="student-card__link" to="/mentor/projects">Open</Link>
          </div>
          <PageState loading={projects.isPending} error={projects.isError ? getApiErrorMessage(projects.error) : undefined} onRetry={() => void projects.refetch()}>
            <p className="student-metric">{projectList.length}</p>
            <p className="student-card__support">Projects currently owned by this mentor.</p>
          </PageState>
        </section>

        <section className="student-card">
          <div className="student-card__heading">
            <div>
              <p className="student-card__eyebrow">STUDENTS</p>
              <h2>Rostered students</h2>
            </div>
            <Link className="student-card__link" to="/mentor/students">View</Link>
          </div>
          <PageState loading={rosterSummary.isPending} error={rosterSummary.isError ? getApiErrorMessage(rosterSummary.error) : undefined} onRetry={() => { void projects.refetch(); void rosterSummary.refetch(); }}>
            <p className="student-metric">{totalStudents}</p>
            <p className="student-card__support">Verified students currently on owned project rosters.</p>
          </PageState>
        </section>

        <section className="student-card">
          <div className="student-card__heading">
            <div>
              <p className="student-card__eyebrow">FORMATION</p>
              <h2>Completed jobs</h2>
            </div>
            <Link className="student-card__link" to="/mentor/teams">Review</Link>
          </div>
          <PageState loading={formationSummary.isPending} error={formationSummary.isError ? getApiErrorMessage(formationSummary.error) : undefined} onRetry={() => void formationSummary.refetch()}>
            <p className="student-metric">{completedJobs}</p>
            <p className="student-card__support">Synchronous formation runs completed across mentor-owned projects.</p>
          </PageState>
        </section>

        <section className="student-card">
          <div className="student-card__heading">
            <div>
              <p className="student-card__eyebrow">READINESS</p>
              <h2>Locked projects</h2>
            </div>
            <span className="student-count">{lockedProjects}</span>
          </div>
          <PageState loading={projects.isPending} error={projects.isError ? getApiErrorMessage(projects.error) : undefined} onRetry={() => void projects.refetch()}>
            <p className="student-card__support">Project preference locks created via the backend lock-preferences endpoint.</p>
          </PageState>
        </section>
      </div>

      <section className="student-card">
        <div className="student-card__heading">
          <div>
            <p className="student-card__eyebrow">ACTIVITY</p>
            <h2>Recent project activity</h2>
          </div>
          <button className="student-button student-button--secondary" onClick={() => void queryClient.invalidateQueries({ queryKey: ["mentor"] })} type="button">
            Refresh
          </button>
        </div>
        <PageState loading={projects.isPending || formationSummary.isPending} error={projects.isError ? getApiErrorMessage(projects.error) : formationSummary.isError ? getApiErrorMessage(formationSummary.error) : undefined} onRetry={() => { void projects.refetch(); void formationSummary.refetch(); }}>
          {projectList.length === 0 ? (
            <p className="student-empty">No mentor-owned projects have been created yet.</p>
          ) : (
            <div className="student-project-list">
              {projectList.map((project) => {
                const latestJob = formationSummary.data?.filter((job) => job.projectId === project.id)[0];
                return (
                  <article className="student-project-row" key={project.id}>
                    <div>
                      <h3>{project.title}</h3>
                      <p>{project.courseCode} · {project.status}</p>
                    </div>
                    <span className={`student-status${project.preferencesLocked ? " student-status--locked" : ""}`}>
                      {latestJob ? latestJob.jobStatus : project.preferencesLocked ? "Locked" : "Open"}
                    </span>
                  </article>
                );
              })}
            </div>
          )}
        </PageState>
      </section>
    </>
  );
}

export function MentorProfilePage() {
  const profile = useQuery({ queryKey: ["mentor", "profile"], queryFn: mentorService.getProfile });
  const filledFields = profile.data ? [profile.data.name, profile.data.email, profile.data.role].filter(Boolean).length : 0;
  const completeness = profile.data ? Math.round((filledFields / 3) * 100) : 0;

  return (
    <>
      <MentorPageHeader
        eyebrow="PROFILE"
        title="Mentor profile"
        description="The backend exposes the current authenticated mentor identity through /api/v1/auth/me. No mentor-specific profile update endpoint is available in the current codebase."
      />
      <section className="student-card student-form-card">
        <PageState loading={profile.isPending} error={profile.isError ? getApiErrorMessage(profile.error) : undefined} onRetry={() => void profile.refetch()}>
          {profile.data && (
            <div className="student-form">
              <div className="student-card__heading">
                <div>
                  <p className="student-card__eyebrow">ACCOUNT</p>
                  <h2>{profile.data.name}</h2>
                </div>
                <span className="student-status student-status--locked">{profile.data.role}</span>
              </div>

              <div className="student-card-grid student-card-grid--two">
                <div className="student-card"><p className="student-card__eyebrow">FULL NAME</p><h3>{profile.data.name}</h3></div>
                <div className="student-card"><p className="student-card__eyebrow">EMAIL</p><h3>{profile.data.email}</h3></div>
                <div className="student-card"><p className="student-card__eyebrow">ROLE</p><h3>{profile.data.role}</h3></div>
                <div className="student-card"><p className="student-card__eyebrow">SRN</p><h3>{profile.data.srn ?? "Not applicable for mentor accounts"}</h3></div>
              </div>

              <div className="student-card student-card--muted">
                <p className="student-card__eyebrow">PROFILE COMPLETENESS</p>
                <div className="student-progress">
                  <div style={{ width: `${completeness}%` }} />
                </div>
                <p className="student-card__support">{completeness}% of the available mentor profile fields are populated from the backend identity response.</p>
              </div>
            </div>
          )}
        </PageState>
      </section>
    </>
  );
}

export function MentorProjectsPage() {
  const queryClient = useQueryClient();
  const projects = useQuery({ queryKey: ["mentor", "projects"], queryFn: mentorService.getProjects });
  const [form, setForm] = useState({
    title: "",
    problemStatement: "",
    courseCode: "",
    minTeamSize: 2,
    maxTeamSize: 4,
    teamSizeSlack: 1,
    preferenceDeadline: "",
  });
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [rosterText, setRosterText] = useState("");
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  const selectedProject = useMemo(
    () => projects.data?.content.find((project) => project.id === selectedProjectId) ?? projects.data?.content[0] ?? null,
    [projects.data, selectedProjectId],
  );

  const roster = useQuery({
    queryKey: ["mentor", "project-roster", selectedProject?.id],
    queryFn: () => mentorService.getRoster(selectedProject!.id),
    enabled: Boolean(selectedProject),
  });

  const feasibility = useQuery({
    queryKey: ["mentor", "project-feasibility", selectedProject?.id],
    queryFn: () => mentorService.checkFeasibility(selectedProject!.id),
    enabled: Boolean(selectedProject),
  });

  const createProject = useMutation({
    mutationFn: mentorService.createProject,
    onSuccess: async (project) => {
      setFeedback({ kind: "success", message: `Project "${project.title}" created successfully.` });
      setSelectedProjectId(project.id);
      await queryClient.invalidateQueries({ queryKey: ["mentor"] });
    },
    onError: (error) => setFeedback({ kind: "error", message: getApiErrorMessage(error, "Unable to create the project.") }),
  });

  const submitProject = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFeedback(null);
    createProject.mutate({
      ...form,
      preferenceDeadline: form.preferenceDeadline ? new Date(form.preferenceDeadline).toISOString() : null,
    });
  };

  const publish = useMutation({
    mutationFn: (projectId: number) => mentorService.publishProject(projectId),
    onSuccess: async (_, projectId) => {
      setFeedback({ kind: "success", message: `Project ${projectId} published successfully.` });
      await queryClient.invalidateQueries({ queryKey: ["mentor"] });
    },
    onError: (error) => setFeedback({ kind: "error", message: getApiErrorMessage(error, "Unable to publish the project.") }),
  });

  const addRoster = useMutation({
    mutationFn: ({ projectId, srns }: { projectId: number; srns: string[] }) => mentorService.addToRoster(projectId, srns),
    onSuccess: async (_, variables) => {
      setFeedback({ kind: "success", message: `Roster update submitted for project ${variables.projectId}.` });
      setRosterText("");
      await queryClient.invalidateQueries({ queryKey: ["mentor", "project-roster", variables.projectId] });
    },
    onError: (error) => setFeedback({ kind: "error", message: getApiErrorMessage(error, "Unable to add students to the roster.") }),
  });

  return (
    <>
      <MentorPageHeader
        eyebrow="PROJECTS"
        title="Project monitoring"
        description="Create projects, publish them, and manage rostered students using the backend project and roster APIs permitted to mentors."
      />
      <FeedbackMessage feedback={feedback} />

      <div className="student-card-grid student-card-grid--two">
        <section className="student-card">
          <div className="student-card__heading">
            <div>
              <p className="student-card__eyebrow">CREATE</p>
              <h2>New project</h2>
            </div>
          </div>
          <form className="student-form" onSubmit={submitProject}>
            <div className="student-form__grid">
              <label className="student-field student-field--wide">Title
                <input onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} required value={form.title} />
              </label>
              <label className="student-field student-field--wide">Problem statement
                <textarea onChange={(event) => setForm((current) => ({ ...current, problemStatement: event.target.value }))} required rows={4} value={form.problemStatement} />
              </label>
              <label className="student-field">Course code
                <input onChange={(event) => setForm((current) => ({ ...current, courseCode: event.target.value }))} required value={form.courseCode} />
              </label>
              <label className="student-field">Preference deadline
                <input onChange={(event) => setForm((current) => ({ ...current, preferenceDeadline: event.target.value }))} type="datetime-local" value={form.preferenceDeadline} />
              </label>
              <label className="student-field">Min team size
                <input min={2} onChange={(event) => setForm((current) => ({ ...current, minTeamSize: Number(event.target.value) }))} type="number" value={form.minTeamSize} />
              </label>
              <label className="student-field">Max team size
                <input min={2} onChange={(event) => setForm((current) => ({ ...current, maxTeamSize: Number(event.target.value) }))} type="number" value={form.maxTeamSize} />
              </label>
              <label className="student-field">Team size slack
                <input min={0} onChange={(event) => setForm((current) => ({ ...current, teamSizeSlack: Number(event.target.value) }))} type="number" value={form.teamSizeSlack} />
              </label>
            </div>
            <button className="student-button" disabled={createProject.isPending} type="submit">
              {createProject.isPending ? <LoaderCircle className="spin" size={17} /> : <Plus size={17} />}
              Create project
            </button>
          </form>
        </section>

        <section className="student-card">
          <div className="student-card__heading">
            <div>
              <p className="student-card__eyebrow">DISCOVERY</p>
              <h2>Owned projects</h2>
            </div>
          </div>
          <PageState loading={projects.isPending} error={projects.isError ? getApiErrorMessage(projects.error) : undefined} onRetry={() => void projects.refetch()}>
            {projects.data?.content.length ? (
              <div className="student-project-list">
                {projects.data.content.map((project) => (
                  <button className="student-project-row student-project-row--button" key={project.id} onClick={() => setSelectedProjectId(project.id)} type="button">
                    <div>
                      <h3>{project.title}</h3>
                      <p>{project.courseCode} · {formatStatus(project.status)}</p>
                    </div>
                    <span className={`student-status${project.preferencesLocked ? " student-status--locked" : ""}`}>{project.preferencesLocked ? "Locked" : "Open"}</span>
                  </button>
                ))}
              </div>
            ) : (
              <p className="student-empty">No projects found for this mentor account yet.</p>
            )}
          </PageState>
        </section>
      </div>

      {selectedProject && (
        <section className="student-card student-form-card">
          <div className="student-card__heading">
            <div>
              <p className="student-card__eyebrow">DETAILS</p>
              <h2>{selectedProject.title}</h2>
            </div>
            <div className="student-inline-actions">
              {selectedProject.status === "DRAFT" && (
                <button className="student-button student-button--secondary" disabled={publish.isPending} onClick={() => publish.mutate(selectedProject.id)} type="button">
                  {publish.isPending ? <LoaderCircle className="spin" size={17} /> : <ShieldCheck size={17} />}
                  Publish
                </button>
              )}
            </div>
          </div>
          <div className="student-card-grid student-card-grid--two">
            <div className="student-card"><p className="student-card__eyebrow">COURSE</p><h3>{selectedProject.courseCode}</h3></div>
            <div className="student-card"><p className="student-card__eyebrow">TEAM SIZE</p><h3>{selectedProject.minTeamSize}-{selectedProject.maxTeamSize}</h3></div>
            <div className="student-card"><p className="student-card__eyebrow">STATUS</p><h3>{formatStatus(selectedProject.status)}</h3></div>
            <div className="student-card"><p className="student-card__eyebrow">PREFERENCE DEADLINE</p><h3>{selectedProject.preferenceDeadline ? new Date(selectedProject.preferenceDeadline).toLocaleString() : "Not set"}</h3></div>
          </div>

          <p className="student-note">{selectedProject.problemStatement}</p>

          <div className="student-card__heading">
            <div>
              <p className="student-card__eyebrow">ROSTER</p>
              <h2>Students on project</h2>
            </div>
          </div>
          <PageState loading={roster.isPending} error={roster.isError ? getApiErrorMessage(roster.error) : undefined} onRetry={() => void roster.refetch()}>
            <div className="student-inline-form">
              <label className="student-field student-field--wide">Add students by SRN
                <textarea onChange={(event) => setRosterText(event.target.value)} placeholder="One SRN per line or comma separated" rows={3} value={rosterText} />
              </label>
              <button className="student-button student-button--secondary" disabled={addRoster.isPending} onClick={() => {
                const srns = rosterText.split(/[\n,]+/).map((value) => value.trim()).filter(Boolean);
                if (!srns.length) {
                  setFeedback({ kind: "error", message: "Enter at least one SRN before adding students." });
                  return;
                }
                addRoster.mutate({ projectId: selectedProject.id, srns });
              }} type="button">
                {addRoster.isPending ? <LoaderCircle className="spin" size={17} /> : <Plus size={17} />}
                Add roster entries
              </button>
            </div>
            {roster.data?.content.length ? (
              <div className="student-project-list">
                {roster.data.content.map((entry) => (
                  <div className="student-project-row" key={entry.studentId}>
                    <div>
                      <h3>{entry.name}</h3>
                      <p>{entry.srn} · {entry.email}</p>
                    </div>
                    <div className="student-inline-actions">
                      <span className="student-status">{entry.eligibilityStatus}</span>
                      <button aria-label={`Remove ${entry.name}`} className="student-icon-button student-icon-button--danger" onClick={() => mentorService.removeFromRoster(selectedProject.id, entry.studentId).then(() => queryClient.invalidateQueries({ queryKey: ["mentor", "project-roster", selectedProject.id] })).catch((error) => setFeedback({ kind: "error", message: getApiErrorMessage(error, "Unable to remove the student from the roster.") }))} type="button">
                        <Trash2 size={15} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="student-empty">No students have been added to this project roster yet.</p>
            )}
          </PageState>

          <div className="student-card__heading">
            <div>
              <p className="student-card__eyebrow">FEASIBILITY</p>
              <h2>Constraint check</h2>
            </div>
          </div>
          <PageState loading={feasibility.isPending} error={feasibility.isError ? getApiErrorMessage(feasibility.error) : undefined} onRetry={() => void feasibility.refetch()}>
            {feasibility.data ? (
              <div>
                <p className="student-card__support">Eligible students: {feasibility.data.eligibleStudents}</p>
                <p className="student-card__support">Feasibility status: {feasibility.data.status}</p>
                {feasibility.data.findings.length ? (
                  <ul className="student-missing-list">
                    {feasibility.data.findings.map((finding) => (
                      <li key={`${finding.code}-${finding.message}`}>{finding.message}</li>
                    ))}
                  </ul>
                ) : (
                  <p className="student-empty">No feasibility issues reported for this project.</p>
                )}
              </div>
            ) : (
              <p className="student-empty">Check feasibility to review project readiness.</p>
            )}
          </PageState>
        </section>
      )}
    </>
  );
}

export function MentorStudentsPage() {
  const projects = useQuery({ queryKey: ["mentor", "projects"], queryFn: mentorService.getProjects });
  const [search, setSearch] = useState("");

  const studentRows = useQuery({
    queryKey: ["mentor", "student-roster-rows"],
    queryFn: async () => {
      if (!projects.data) return [];
      const rosterSets = await Promise.all(
        projects.data.content.map(async (project) => {
          const roster = await mentorService.getRoster(project.id);
          return roster.content.map((entry) => ({ ...entry, projectTitle: project.title, projectStatus: project.status }));
        }),
      );
      return rosterSets.flat();
    },
    enabled: Boolean(projects.data),
  });

  const filteredStudents = (studentRows.data ?? []).filter((student) => {
    const haystack = `${student.name} ${student.email} ${student.srn} ${student.projectTitle}`.toLowerCase();
    return haystack.includes(search.toLowerCase());
  });

  return (
    <>
      <MentorPageHeader
        eyebrow="STUDENT MONITORING"
        title="Assigned students"
        description="Review rostered students across your projects, their SRNs, and their placement status in the mentor-owned project list."
      />
      <section className="student-card">
        <div className="student-card__heading">
          <div>
            <p className="student-card__eyebrow">SEARCH</p>
            <h2>Roster search</h2>
          </div>
        </div>
        <PageState loading={projects.isPending || studentRows.isPending} error={projects.isError ? getApiErrorMessage(projects.error) : studentRows.isError ? getApiErrorMessage(studentRows.error) : undefined} onRetry={() => { void projects.refetch(); void studentRows.refetch(); }}>
          <div className="student-inline-form">
            <label className="student-field student-field--wide">
              <Search size={16} />
              <input onChange={(event) => setSearch(event.target.value)} placeholder="Search by name, SRN, email or project" value={search} />
            </label>
          </div>

          {filteredStudents.length === 0 ? (
            <p className="student-empty">No rostered students match this search yet.</p>
          ) : (
            <div className="student-table-wrap">
              <table className="student-table">
                <thead>
                  <tr>
                    <th>Student</th>
                    <th>SRN</th>
                    <th>Project</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredStudents.map((student) => (
                    <tr key={`${student.projectTitle}-${student.studentId}`}>
                      <td>
                        <div>
                          <strong>{student.name}</strong>
                          <small>{student.email}</small>
                        </div>
                      </td>
                      <td>{student.srn}</td>
                      <td>{student.projectTitle}</td>
                      <td><span className="student-status">{student.eligibilityStatus}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </PageState>
      </section>
    </>
  );
}

export function MentorTeamsPage() {
  const projects = useQuery({ queryKey: ["mentor", "projects"], queryFn: mentorService.getProjects });
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  const selectedProject = useMemo(
    () => projects.data?.content.find((project) => project.id === selectedProjectId) ?? projects.data?.content[0] ?? null,
    [projects.data, selectedProjectId],
  );

  const jobs = useQuery({
    queryKey: ["mentor", "project-jobs", selectedProject?.id],
    queryFn: () => mentorService.listFormationJobs(selectedProject!.id),
    enabled: Boolean(selectedProject),
  });

  const latestJobId = jobs.data?.content[0]?.id ?? null;
  const latestJob = useQuery({
    queryKey: ["mentor", "project-job-detail", selectedProject?.id, latestJobId],
    queryFn: () => mentorService.getFormationJob(selectedProject!.id, latestJobId!),
    enabled: Boolean(selectedProject && latestJobId),
  });

  const startFormation = useMutation({
    mutationFn: (projectId: number) => mentorService.startFormation(projectId),
    onSuccess: async (_, projectId) => {
      setFeedback({ kind: "success", message: `Formation started for project ${projectId}.` });
      await Promise.all([
        projects.refetch(),
        jobs.refetch(),
        latestJob.refetch(),
      ]);
    },
    onError: (error) => setFeedback({ kind: "error", message: getApiErrorMessage(error, "Unable to start formation.") }),
  });

  return (
    <>
      <MentorPageHeader
        eyebrow="TEAM MANAGEMENT"
        title="Team formation results"
        description="Review mentor-owned formation jobs, candidate teams, and result details returned by the backend formation engine."
      />
      <FeedbackMessage feedback={feedback} />

      <section className="student-card">
        <div className="student-card__heading">
          <div>
            <p className="student-card__eyebrow">PROJECT</p>
            <h2>Formation dashboard</h2>
          </div>
        </div>
        <PageState loading={projects.isPending} error={projects.isError ? getApiErrorMessage(projects.error) : undefined} onRetry={() => void projects.refetch()}>
          {projects.data?.content.length ? (
            <div className="student-inline-form">
              <label className="student-field student-field--wide">Selected project
                <select onChange={(event) => setSelectedProjectId(Number(event.target.value))} value={selectedProject?.id ?? ""}>
                  {projects.data.content.map((project) => (
                    <option key={project.id} value={project.id}>{project.title}</option>
                  ))}
                </select>
              </label>
              {selectedProject && (
                <button className="student-button student-button--secondary" disabled={startFormation.isPending} onClick={() => startFormation.mutate(selectedProject.id)} type="button">
                  {startFormation.isPending ? <LoaderCircle className="spin" size={17} /> : <Save size={17} />}
                  Start formation
                </button>
              )}
            </div>
          ) : (
            <p className="student-empty">No mentor-owned projects are available for team formation yet.</p>
          )}
        </PageState>
      </section>

      {selectedProject && (
        <section className="student-card">
          <div className="student-card__heading">
            <div>
              <p className="student-card__eyebrow">RESULTS</p>
              <h2>{selectedProject.title}</h2>
            </div>
          </div>
          <PageState loading={jobs.isPending || latestJob.isPending} error={jobs.isError ? getApiErrorMessage(jobs.error) : latestJob.isError ? getApiErrorMessage(latestJob.error) : undefined} onRetry={() => { void jobs.refetch(); void latestJob.refetch(); }}>
            {latestJob.data ? (
              <>
                <div className="student-card-grid student-card-grid--two">
                  <div className="student-card"><p className="student-card__eyebrow">STATUS</p><h3>{latestJob.data.jobStatus}</h3></div>
                  <div className="student-card"><p className="student-card__eyebrow">ASSIGNED STUDENTS</p><h3>{latestJob.data.assignedStudentCount}</h3></div>
                  <div className="student-card"><p className="student-card__eyebrow">TEAM COUNT</p><h3>{latestJob.data.teamCount}</h3></div>
                  <div className="student-card"><p className="student-card__eyebrow">PREFERENCE SCORE</p><h3>{latestJob.data.totalPreferenceScore}</h3></div>
                </div>

                {latestJob.data.teams.length ? (
                  <div className="student-project-list">
                    {latestJob.data.teams.map((team) => (
                      <article className="student-project-row" key={team.id}>
                        <div>
                          <h3>Team #{team.index}</h3>
                          <p>{team.members.map((member) => member.fullName).join(", ") || "No members"}</p>
                        </div>
                        <span className="student-status">Score {team.score}</span>
                      </article>
                    ))}
                  </div>
                ) : (
                  <p className="student-empty">No candidate teams are available from the latest formation job.</p>
                )}
              </>
            ) : (
              <p className="student-empty">No formation jobs exist yet for this project. Start a formation job to generate results.</p>
            )}
          </PageState>
        </section>
      )}
    </>
  );
}

export function MentorNotificationsPage() {
  const projects = useQuery({ queryKey: ["mentor", "projects"], queryFn: mentorService.getProjects });

  const activities = useMemo(() => {
    if (!projects.data) return [];
    return projects.data.content.map((project) => ({
      id: project.id,
      title: project.title,
      detail: `${project.status} · ${project.preferencesLocked ? "locked" : "open for preferences"}`,
      createdAt: project.updatedAt,
    }));
  }, [projects.data]);

  return (
    <>
      <MentorPageHeader
        eyebrow="COMMUNICATION"
        title="Notifications and activity"
        description="The backend currently exposes no dedicated mentor notification endpoint. This view uses real project and job data as the only supported activity feed available to mentors."
      />
      <section className="student-card">
        <PageState loading={projects.isPending} error={projects.isError ? getApiErrorMessage(projects.error) : undefined} onRetry={() => void projects.refetch()}>
          {activities.length === 0 ? (
            <p className="student-empty">No mentor activity is available yet.</p>
          ) : (
            <div className="student-project-list">
              {activities.map((activity) => (
                <article className="student-project-row" key={activity.id}>
                  <div>
                    <h3>{activity.title}</h3>
                    <p>{activity.detail}</p>
                  </div>
                  <span className="student-status">{new Date(activity.createdAt).toLocaleString()}</span>
                </article>
              ))}
            </div>
          )}
        </PageState>
      </section>
    </>
  );
}

export function MentorSettingsPage() {
  const profile = useQuery({ queryKey: ["mentor", "profile"], queryFn: mentorService.getProfile });

  return (
    <>
      <MentorPageHeader
        eyebrow="SETTINGS"
        title="Mentor settings"
        description="The current backend exposes account identity only through the authenticated user endpoint. No separate mentor settings API is available."
      />
      <section className="student-card student-form-card">
        <PageState loading={profile.isPending} error={profile.isError ? getApiErrorMessage(profile.error) : undefined} onRetry={() => void profile.refetch()}>
          {profile.data && (
            <div className="student-form">
              <div className="student-card-grid student-card-grid--two">
                <div className="student-card"><p className="student-card__eyebrow">NAME</p><h3>{profile.data.name}</h3></div>
                <div className="student-card"><p className="student-card__eyebrow">EMAIL</p><h3>{profile.data.email}</h3></div>
                <div className="student-card"><p className="student-card__eyebrow">ROLE</p><h3>{profile.data.role}</h3></div>
                <div className="student-card"><p className="student-card__eyebrow">SESSION</p><h3>JWT-backed</h3></div>
              </div>
              <Link className="student-button" to="/mentor/projects">
                Review projects <ArrowRight size={17} />
              </Link>
            </div>
          )}
        </PageState>
      </section>
    </>
  );
}
