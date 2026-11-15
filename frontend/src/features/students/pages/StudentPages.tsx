import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowRight,
  CircleAlert,
  CircleCheck,
  ExternalLink,
  LoaderCircle,
  Plus,
  Save,
  Trash2,
} from "lucide-react";
import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Notification } from "../../../components/Notification";
import { ThemeControls } from "../../../components/ThemeControls";
import { useAuthStore } from "../../../stores/authStore";
import { useThemeStore } from "../../../stores/themeStore";
import { getApiErrorMessage } from "../../../utils/apiError";
import { projectService, type StudentProject } from "../../../services/students/projectService";
import {
  studentService,
  type StudentSkill,
} from "../../../services/students/studentService";
import { PageHeader } from "../StudentAppLayout";

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
        <span>Loading your information…</span>
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
          {onRetry && <button className="student-button student-button--secondary" onClick={onRetry} type="button">Try again</button>}
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

function PageTitle({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: string;
  description: string;
}) {
  return <PageHeader eyebrow={eyebrow} title={title} description={description} />;
}

function ProjectList({
  projects,
  empty,
}: {
  projects: StudentProject[];
  empty: string;
}) {
  if (projects.length === 0) return <p className="student-empty">{empty}</p>;
  return (
    <div className="student-project-list">
      {projects.map((project) => (
        <article className="student-project-row" key={project.id}>
          <div>
            <h3>{project.title}</h3>
            <p>{project.courseCode} · Mentor {project.mentorName}</p>
          </div>
          <span className={`student-status${project.preferencesLocked ? " student-status--locked" : ""}`}>
            {project.preferencesLocked ? "Preferences locked" : project.status.replaceAll("_", " ")}
          </span>
        </article>
      ))}
    </div>
  );
}

export function DashboardPage() {
  const profile = useQuery({ queryKey: ["student", "profile"], queryFn: studentService.getProfile });
  const skills = useQuery({ queryKey: ["student", "skills"], queryFn: studentService.getSkills });
  const projects = useQuery({ queryKey: ["student", "projects"], queryFn: projectService.getMyProjects });
  const cachedUser = useAuthStore((state) => state.user);
  const projectItems = projects.data?.content ?? [];
  const preferenceOverview = useQuery({
    queryKey: ["student", "preference-overview", projectItems.map(({ id }) => id)],
    queryFn: async () => Promise.all(projectItems.map(async (project) => ({
      project,
      preferences: await projectService.getMyPreferences(project.id),
    }))),
    enabled: projects.isSuccess && projectItems.length > 0,
  });
  const displayedName = profile.data?.name || cachedUser?.name || "there";
  const profileFields = profile.data
    ? [
        ["Full name", profile.data.name],
        ["Department", profile.data.department],
        ["Semester", profile.data.semester],
        ["Interests", profile.data.interests],
      ].filter(([, value]) => value === null || value === undefined || String(value).trim() === "")
    : [];
  useEffect(() => {
    document.title = "Student Dashboard | SkillMatch";
  }, []);

  return (
    <>
      <PageTitle eyebrow="STUDENT WORKSPACE" title={`Welcome, ${displayedName}`} description="Keep your profile up to date and find the right people to build with." />
      <section className="student-welcome-card">
        <div>
          <p className="student-welcome-card__eyebrow">YOUR NEXT STEP</p>
          <h2>Make your skills visible to your team.</h2>
          <p>Complete your profile and record the skills and interests you want teammates to know about.</p>
        </div>
        <Link className="student-button" to="/app/profile">
          Update profile <ArrowRight aria-hidden="true" size={17} />
        </Link>
      </section>

      <div className="student-card-grid student-card-grid--dashboard">
        <section className="student-card">
          <div className="student-card__heading">
            <div><p className="student-card__eyebrow">PROFILE</p><h2>Profile details</h2></div>
            <Link aria-label="Edit profile" className="student-card__link" to="/app/profile">Edit</Link>
          </div>
          <PageState loading={profile.isPending} error={profile.isError ? getApiErrorMessage(profile.error) : undefined} onRetry={() => void profile.refetch()}>
            {profileFields.length === 0
              ? <p className="student-empty"><CircleCheck aria-hidden="true" size={18} /> Your profile details are filled in.</p>
              : <><p className="student-card__support">Information still to add:</p><ul className="student-missing-list">{profileFields.map(([field]) => <li key={String(field)}>{field}</li>)}</ul></>}
            {profile.data?.interests?.trim() && <p className="student-interest-preview"><strong>Your interests</strong>{profile.data.interests}</p>}
          </PageState>
        </section>

        <section className="student-card">
          <div className="student-card__heading">
            <div><p className="student-card__eyebrow">YOUR TOOLKIT</p><h2>Skills</h2></div>
            <Link aria-label="Manage skills" className="student-card__link" to="/app/skills">Manage</Link>
          </div>
          <PageState loading={skills.isPending} error={skills.isError ? getApiErrorMessage(skills.error) : undefined} onRetry={() => void skills.refetch()}>
            {skills.data?.length ? (
              <div className="student-chip-list">{skills.data.map((skill) => <span className="student-chip" key={skill.skillId}>{skill.skillName}</span>)}</div>
            ) : <p className="student-empty">No skills added yet. Choose from the shared skill catalogue.</p>}
          </PageState>
        </section>

        <section className="student-card">
          <div className="student-card__heading">
            <div><p className="student-card__eyebrow">TEAMMATE CHOICES</p><h2>Project preferences</h2></div>
            <Link aria-label="Manage project preferences" className="student-card__link" to="/app/preferences">Open</Link>
          </div>
          <PageState loading={projects.isPending || (projectItems.length > 0 && preferenceOverview.isPending)} error={projects.isError ? getApiErrorMessage(projects.error) : preferenceOverview.isError ? getApiErrorMessage(preferenceOverview.error) : undefined} onRetry={() => { void projects.refetch(); void preferenceOverview.refetch(); }}>
            {projectItems.length ? (
              <div className="student-preference-overview">
                {preferenceOverview.data?.slice(0, 3).map(({ project, preferences }) => (
                  <div className="student-preference-overview__item" key={project.id}>
                    <strong>{project.title}</strong>
                    {preferences.length
                      ? <p>Preferred teammates: {preferences.map(({ preferredStudentName }) => preferredStudentName).join(", ")}</p>
                      : <p>No teammate choices saved yet.</p>}
                  </div>
                ))}
              </div>
            ) : <p className="student-empty">No projects are currently available to your account.</p>}
          </PageState>
        </section>

        <section className="student-card">
          <div className="student-card__heading">
            <div><p className="student-card__eyebrow">FORMATION</p><h2>Team formation</h2></div>
            <Link aria-label="View team formation" className="student-card__link" to="/app/formation">Details</Link>
          </div>
          <PageState loading={projects.isPending} error={projects.isError ? getApiErrorMessage(projects.error) : undefined} onRetry={() => void projects.refetch()}>
            {projectItems.length ? (
              <p className="student-empty">You’re rostered on {projectItems.length} {projectItems.length === 1 ? "project" : "projects"}. Formation is managed by the project mentor.</p>
            ) : <p className="student-empty">No team formation activity is available yet.</p>}
          </PageState>
        </section>
      </div>
      <section className="student-card student-dashboard-notifications">
        <div className="student-card__heading"><div><p className="student-card__eyebrow">UPDATES</p><h2>Notifications</h2></div><Link className="student-card__link" to="/app/notifications">View page</Link></div>
        <p className="student-empty">Student notifications aren’t available from the current backend API.</p>
      </section>
    </>
  );
}

export function ProfilePage() {
  const queryClient = useQueryClient();
  const profile = useQuery({ queryKey: ["student", "profile"], queryFn: studentService.getProfile });
  const setUser = useAuthStore((state) => state.setUser);
  const [values, setValues] = useState({ fullName: "", department: "", semester: "", interests: "" });
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  useEffect(() => {
    if (!profile.data) return;
    setValues({
      fullName: profile.data.name,
      department: profile.data.department ?? "",
      semester: profile.data.semester?.toString() ?? "",
      interests: profile.data.interests ?? "",
    });
  }, [profile.data]);

  const save = useMutation({
    mutationFn: studentService.updateProfile,
    onSuccess: (saved) => {
      queryClient.setQueryData(["student", "profile"], saved);
      const currentUser = useAuthStore.getState().user;
      if (currentUser) {
        const updatedUser = { ...currentUser, name: saved.name };
        setUser(updatedUser);
        queryClient.setQueryData(["auth", "me"], updatedUser);
      }
      setFeedback({ kind: "success", message: "Your profile changes have been saved." });
    },
    onError: (error) => setFeedback({ kind: "error", message: getApiErrorMessage(error, "Unable to save your profile.") }),
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFeedback(null);
    const fullName = values.fullName.trim();
    const semester = values.semester.trim() === "" ? undefined : Number(values.semester);
    if (!fullName) return setFeedback({ kind: "error", message: "Full name is required." });
    if (fullName.length > 150) return setFeedback({ kind: "error", message: "Full name must be at most 150 characters." });
    if (values.department.length > 100) return setFeedback({ kind: "error", message: "Department must be at most 100 characters." });
    if (values.interests.length > 1000) return setFeedback({ kind: "error", message: "Interests must be at most 1000 characters." });
    if (semester !== undefined && (!Number.isInteger(semester) || semester < 1)) {
      return setFeedback({ kind: "error", message: "Semester must be a positive whole number." });
    }
    save.mutate({
      fullName,
      department: values.department,
      interests: values.interests,
      ...(semester === undefined ? {} : { semester }),
    });
  }

  return (
    <>
      <PageTitle eyebrow="YOUR INFORMATION" title="My Profile" description="Edit the student profile fields supported by SkillMatch." />
      <section className="student-card student-form-card">
        <PageState loading={profile.isPending} error={profile.isError ? getApiErrorMessage(profile.error) : undefined} onRetry={() => void profile.refetch()}>
          {profile.data && (
            <form className="student-form" onSubmit={submit}>
              <FeedbackMessage feedback={feedback} />
              <div className="student-form__grid">
                <label className="student-field">Full name
                  <input autoComplete="name" maxLength={150} onChange={(event) => setValues({ ...values, fullName: event.target.value })} required value={values.fullName} />
                </label>
                <label className="student-field">College email
                  <input readOnly value={profile.data.email} />
                  <span className="student-field__hint">Email is a read-only account identity field.</span>
                </label>
                <label className="student-field">SRN
                  <input readOnly value={profile.data.srn} />
                  <span className="student-field__hint">SRN is a read-only account identity field.</span>
                </label>
                <label className="student-field">Department
                  <input maxLength={100} onChange={(event) => setValues({ ...values, department: event.target.value })} value={values.department} />
                </label>
                <label className="student-field">Semester
                  <input min={1} onChange={(event) => setValues({ ...values, semester: event.target.value })} step={1} type="number" value={values.semester} />
                  <span className="student-field__hint">Leave blank to keep the current value; the API does not provide a clear operation.</span>
                </label>
                <label className="student-field student-field--wide">Interests
                  <textarea maxLength={1000} onChange={(event) => setValues({ ...values, interests: event.target.value })} rows={4} value={values.interests} />
                  <span className="student-field__hint">Describe areas you’re interested in (up to 1000 characters).</span>
                </label>
              </div>
              <button className="student-button" disabled={save.isPending} type="submit">
                {save.isPending ? <LoaderCircle className="spin" size={17} /> : <Save size={17} />}
                Save profile
              </button>
            </form>
          )}
        </PageState>
      </section>
    </>
  );
}

export function SkillsPage() {
  const queryClient = useQueryClient();
  const skills = useQuery({ queryKey: ["student", "skills"], queryFn: studentService.getSkills });
  const catalog = useQuery({ queryKey: ["student", "skill-catalog"], queryFn: studentService.getSkillCatalog });
  const profile = useQuery({ queryKey: ["student", "profile"], queryFn: studentService.getProfile });
  const [selectedSkills, setSelectedSkills] = useState<StudentSkill[]>([]);
  const [newSkillId, setNewSkillId] = useState("");
  const [interests, setInterests] = useState("");
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const catalogEntries = catalog.data?.content ?? [];
  const selectedIds = useMemo(() => new Set(selectedSkills.map(({ skillId }) => skillId)), [selectedSkills]);

  useEffect(() => {
    if (skills.data) setSelectedSkills(skills.data);
  }, [skills.data]);
  useEffect(() => {
    if (profile.data) setInterests(profile.data.interests ?? "");
  }, [profile.data]);

  const saveSkills = useMutation({
    mutationFn: studentService.replaceSkills,
    onSuccess: (saved) => {
      queryClient.setQueryData(["student", "skills"], saved);
      setFeedback({ kind: "success", message: "Your skills and proficiency levels have been saved." });
    },
    onError: (error) => setFeedback({ kind: "error", message: getApiErrorMessage(error, "Unable to save your skills.") }),
  });
  const saveInterests = useMutation({
    mutationFn: (text: string) => studentService.updateProfile({ interests: text }),
    onSuccess: (saved) => {
      queryClient.setQueryData(["student", "profile"], saved);
      setFeedback({ kind: "success", message: "Your interests have been saved." });
    },
    onError: (error) => setFeedback({ kind: "error", message: getApiErrorMessage(error, "Unable to save your interests.") }),
  });

  function addSkill(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const entry = catalogEntries.find((item) => item.id === Number(newSkillId));
    if (!entry || selectedIds.has(entry.id)) return;
    setSelectedSkills([...selectedSkills, {
      skillId: entry.id,
      skillName: entry.name,
      category: entry.category,
      proficiency: 1,
    }]);
    setNewSkillId("");
    setFeedback(null);
  }

  return (
    <>
      <PageTitle eyebrow="YOUR TOOLKIT" title="Skills & Interests" description="Choose skills from the shared catalogue, set your proficiency, and record what you’d like to work on." />
      <FeedbackMessage feedback={feedback} />
      <div className="student-card-grid student-card-grid--two">
        <section className="student-card">
          <div className="student-card__heading"><div><p className="student-card__eyebrow">SKILL CATALOGUE</p><h2>My skills</h2></div><span className="student-count">{selectedSkills.length} selected</span></div>
          <PageState loading={skills.isPending || catalog.isPending} error={skills.isError ? getApiErrorMessage(skills.error) : catalog.isError ? getApiErrorMessage(catalog.error) : undefined} onRetry={() => { void skills.refetch(); void catalog.refetch(); }}>
            <form className="student-inline-form" onSubmit={addSkill}>
              <label className="student-field">Add a skill
                <select onChange={(event) => setNewSkillId(event.target.value)} value={newSkillId}>
                  <option value="">Choose from the catalogue</option>
                  {catalogEntries.filter((entry) => !selectedIds.has(entry.id)).map((entry) => (
                    <option key={entry.id} value={entry.id}>{entry.name}{entry.category ? ` · ${entry.category}` : ""}</option>
                  ))}
                </select>
              </label>
              <button className="student-button student-button--secondary" disabled={!newSkillId} type="submit"><Plus size={17} />Add</button>
            </form>
            {catalogEntries.length === 0 && <p className="student-empty">The shared skill catalogue has no entries yet. A mentor or admin manages this catalogue.</p>}
            {selectedSkills.length === 0 ? (
              <p className="student-empty">No skills selected. Add a skill from the catalogue to get started.</p>
            ) : (
              <div className="student-skill-list">
                {selectedSkills.map((skill) => (
                  <div className="student-skill-row" key={skill.skillId}>
                    <div className="student-skill-row__name"><strong>{skill.skillName}</strong>{skill.category && <span>{skill.category}</span>}</div>
                    <label className="student-skill-row__proficiency">Proficiency
                      <select aria-label={`${skill.skillName} proficiency`} onChange={(event) => setSelectedSkills(selectedSkills.map((item) => item.skillId === skill.skillId ? { ...item, proficiency: Number(event.target.value) } : item))} value={skill.proficiency}>
                        {[1, 2, 3, 4, 5].map((level) => <option key={level} value={level}>{level} / 5</option>)}
                      </select>
                    </label>
                    <button aria-label={`Remove ${skill.skillName}`} className="student-icon-button student-icon-button--danger" onClick={() => setSelectedSkills(selectedSkills.filter((item) => item.skillId !== skill.skillId))} type="button"><Trash2 size={17} /></button>
                  </div>
                ))}
              </div>
            )}
            <button className="student-button" disabled={skills.isPending || saveSkills.isPending} onClick={() => { setFeedback(null); saveSkills.mutate(selectedSkills.map(({ skillId, proficiency }) => ({ skillId, proficiency }))); }} type="button">
              {saveSkills.isPending ? <LoaderCircle className="spin" size={17} /> : <Save size={17} />}Save skills
            </button>
          </PageState>
        </section>

        <section className="student-card">
          <div className="student-card__heading"><div><p className="student-card__eyebrow">AREAS OF INTEREST</p><h2>What I’m interested in</h2></div></div>
          <PageState loading={profile.isPending} error={profile.isError ? getApiErrorMessage(profile.error) : undefined} onRetry={() => void profile.refetch()}>
            <label className="student-field">Interests
              <textarea maxLength={1000} onChange={(event) => setInterests(event.target.value)} placeholder="For example: accessibility, data science, frontend design…" rows={7} value={interests} />
              <span className="student-field__hint">Saved to your student profile (up to 1000 characters).</span>
            </label>
            <button className="student-button student-button--spaced" disabled={saveInterests.isPending} onClick={() => { setFeedback(null); saveInterests.mutate(interests); }} type="button">
              {saveInterests.isPending ? <LoaderCircle className="spin" size={17} /> : <Save size={17} />}Save interests
            </button>
            <p className="student-note">The backend stores interests as profile text. It does not currently have a separate “skills to learn” field.</p>
          </PageState>
        </section>
      </div>
    </>
  );
}

export function ProjectPreferencesPage() {
  const queryClient = useQueryClient();
  const projects = useQuery({ queryKey: ["student", "projects"], queryFn: projectService.getMyProjects });
  const projectItems = projects.data?.content ?? [];
  const [selectedProjectId, setSelectedProjectId] = useState<number | "">("");
  const [selectedStudentIds, setSelectedStudentIds] = useState<number[]>([]);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const activeProjectId = selectedProjectId || projectItems[0]?.id || 0;
  const activeProject = projectItems.find((project) => project.id === activeProjectId);
  const classmates = useQuery({
    queryKey: ["project", activeProjectId, "classmates"],
    queryFn: () => projectService.getClassmates(activeProjectId),
    enabled: activeProjectId > 0,
  });
  const preferences = useQuery({
    queryKey: ["project", activeProjectId, "my-preferences"],
    queryFn: () => projectService.getMyPreferences(activeProjectId),
    enabled: activeProjectId > 0,
  });
  const userId = useAuthStore((state) => state.user?.id);

  useEffect(() => {
    if (preferences.data) {
      setSelectedStudentIds(preferences.data.map(({ preferredStudentId }) => preferredStudentId));
    }
  }, [preferences.data]);

  const save = useMutation({
    mutationFn: (studentIds: number[]) => projectService.replaceMyPreferences(activeProjectId, studentIds),
    onSuccess: (saved) => {
      queryClient.setQueryData(["project", activeProjectId, "my-preferences"], saved);
      setFeedback({ kind: "success", message: "Your ranked teammate preferences have been saved." });
    },
    onError: (error) => setFeedback({ kind: "error", message: getApiErrorMessage(error, "Unable to save project preferences.") }),
  });

  function toggleStudent(studentId: number) {
    setFeedback(null);
    setSelectedStudentIds((current) => current.includes(studentId)
      ? current.filter((id) => id !== studentId)
      : current.length < 2 ? [...current, studentId] : current);
  }

  return (
    <>
      <PageTitle eyebrow="PROJECT PARTICIPATION" title="Project Preferences" description="For projects you’re rostered on, rank up to two preferred teammates." />
      <FeedbackMessage feedback={feedback} />
      <section className="student-card student-form-card">
        <PageState loading={projects.isPending} error={projects.isError ? getApiErrorMessage(projects.error) : undefined} onRetry={() => void projects.refetch()}>
          {projectItems.length === 0 ? (
            <p className="student-empty">There are no published projects attached to your account yet. Project preferences are available only after a mentor adds you to a project roster.</p>
          ) : (
            <>
              <label className="student-field student-project-select">Project
                <select onChange={(event) => { setSelectedProjectId(Number(event.target.value)); setFeedback(null); }} value={activeProjectId}>
                  {projectItems.map((project) => <option key={project.id} value={project.id}>{project.title} · {project.courseCode}</option>)}
                </select>
              </label>
              {activeProject && (
                <>
                  <div className="student-project-summary">
                    <div><strong>{activeProject.title}</strong><p>{activeProject.problemStatement}</p></div>
                    <span className={`student-status${activeProject.preferencesLocked ? " student-status--locked" : ""}`}>{activeProject.preferencesLocked ? "Locked" : "Open"}</span>
                  </div>
                  {activeProject.preferencesLocked ? (
                    <p className="student-note">This project’s preferences are locked. Your saved choices are shown below and can no longer be changed.</p>
                  ) : (
                    <p className="student-card__support">Select up to two rostered classmates. Selection order determines preference rank.</p>
                  )}
                  <PageState
                    loading={classmates.isPending || preferences.isPending}
                    error={classmates.isError ? getApiErrorMessage(classmates.error) : preferences.isError ? getApiErrorMessage(preferences.error) : undefined}
                    onRetry={() => { void classmates.refetch(); void preferences.refetch(); }}
                  >
                    {classmates.data?.content.filter((classmate) => classmate.studentId !== userId).length ? (
                      <div className="student-classmate-list">
                        {classmates.data.content.filter((classmate) => classmate.studentId !== userId).map((classmate) => {
                          const rank = selectedStudentIds.indexOf(classmate.studentId);
                          return (
                            <label className={`student-classmate${rank >= 0 ? " student-classmate--selected" : ""}`} key={classmate.studentId}>
                              <input checked={rank >= 0} disabled={activeProject.preferencesLocked || (rank < 0 && selectedStudentIds.length >= 2)} onChange={() => toggleStudent(classmate.studentId)} type="checkbox" />
                              <span className="student-classmate__rank">{rank >= 0 ? rank + 1 : ""}</span>
                              <span><strong>{classmate.fullName}</strong><small>{classmate.srn}</small></span>
                            </label>
                          );
                        })}
                      </div>
                    ) : <p className="student-empty">No other rostered classmates are available for this project.</p>}
                    {!activeProject.preferencesLocked && (
                      <button className="student-button student-button--spaced" disabled={save.isPending} onClick={() => { setFeedback(null); save.mutate(selectedStudentIds); }} type="button">
                        {save.isPending ? <LoaderCircle className="spin" size={17} /> : <Save size={17} />}Save preferences
                      </button>
                    )}
                  </PageState>
                </>
              )}
            </>
          )}
        </PageState>
      </section>
      <p className="student-note student-note--page">The backend supports ranked teammate choices only; it has no student endpoints for project-category selection or preferred roles.</p>
    </>
  );
}

export function TeamFormationPage() {
  const projects = useQuery({ queryKey: ["student", "projects"], queryFn: projectService.getMyProjects });
  const items = projects.data?.content ?? [];

  return (
    <>
      <PageTitle eyebrow="TEAM FORMATION" title="Team Formation" description="Review projects you’re rostered on and their formation requirements." />
      <section className="student-card">
        <PageState loading={projects.isPending} error={projects.isError ? getApiErrorMessage(projects.error) : undefined} onRetry={() => void projects.refetch()}>
          {items.length === 0 ? (
            <p className="student-empty">You aren’t currently on a project roster. A project mentor manages roster membership and starts team formation.</p>
          ) : (
            <div className="student-project-list">
              {items.map((project) => (
                <article className="student-formation-card" key={project.id}>
                  <div className="student-card__heading">
                    <div><p className="student-card__eyebrow">{project.courseCode} · {project.status.replaceAll("_", " ")}</p><h2>{project.title}</h2></div>
                    <span className={`student-status${project.preferencesLocked ? " student-status--locked" : ""}`}>{project.preferencesLocked ? "Preferences locked" : "Preferences open"}</span>
                  </div>
                  <p>{project.problemStatement}</p>
                  <dl className="student-requirements">
                    <div><dt>Team size</dt><dd>{project.minTeamSize}–{project.maxTeamSize}</dd></div>
                    <div><dt>Mentor</dt><dd>{project.mentorName}</dd></div>
                    <div><dt>Preference deadline</dt><dd>{project.preferenceDeadline ? new Date(project.preferenceDeadline).toLocaleString() : "Not set"}</dd></div>
                  </dl>
                  <p className="student-note">Formation is initiated and monitored by the project mentor. Student requests and formation status are not exposed through the student API.</p>
                  <Link className="student-text-link" to="/app/preferences">Manage teammate preferences <ArrowRight size={16} /></Link>
                </article>
              ))}
            </div>
          )}
        </PageState>
      </section>
    </>
  );
}

export function MyTeamPage() {
  const projects = useQuery({ queryKey: ["student", "projects"], queryFn: projectService.getMyProjects });
  return (
    <>
      <PageTitle eyebrow="TEAM RESULTS" title="My Team" description="View your assigned team when student team results are available." />
      <section className="student-card">
        <PageState loading={projects.isPending} error={projects.isError ? getApiErrorMessage(projects.error) : undefined} onRetry={() => void projects.refetch()}>
          {projects.data?.content.length ? (
            <>
              <p className="student-empty">You’re rostered on these projects, but the current API does not expose assigned teams or team results to students.</p>
              <ProjectList empty="" projects={projects.data.content} />
            </>
          ) : <p className="student-empty">You haven’t been added to a project roster yet. Once a mentor adds you, it will appear here.</p>}
        </PageState>
      </section>
      <p className="student-note student-note--page">Formation job results, candidate teams, and compatibility scores are restricted to mentor endpoints. No team assignment is inferred or fabricated here.</p>
    </>
  );
}

export function NotificationsPage() {
  return (
    <>
      <PageTitle eyebrow="UPDATES" title="Notifications" description="Stay informed about activity on your SkillMatch account." />
      <section className="student-card student-blocked-card">
        <span className="student-blocked-card__icon"><CircleAlert aria-hidden="true" size={23} /></span>
        <div><h2>Notifications are not available yet</h2><p>The current backend has no student notification endpoint, so this page can’t load or update read status without inventing API behavior.</p></div>
      </section>
    </>
  );
}

export function SettingsPage() {
  const navigate = useNavigate();
  const signOut = useAuthStore((state) => state.signOut);
  const user = useAuthStore((state) => state.user);
  const mode = useThemeStore((state) => state.mode);

  function logout() {
    signOut();
    void navigate("/login", { replace: true });
  }

  return (
    <>
      <PageTitle eyebrow="ACCOUNT" title="Settings" description="Manage your account session and the appearance of SkillMatch." />
      <div className="student-card-grid student-card-grid--two">
        <section className="student-card">
          <div className="student-card__heading"><div><p className="student-card__eyebrow">ACCOUNT INFORMATION</p><h2>Student account</h2></div></div>
          <dl className="student-account-details">
            <div><dt>Name</dt><dd>{user?.name || "Loading…"}</dd></div>
            <div><dt>Email</dt><dd>{user?.email || "—"}</dd></div>
            <div><dt>SRN</dt><dd>{user?.srn || "—"}</dd></div>
            <div><dt>Role</dt><dd>{user?.role || "—"}</dd></div>
          </dl>
          <Link className="student-text-link" to="/app/profile">Edit supported profile fields <ExternalLink size={15} /></Link>
        </section>
        <section className="student-card">
          <div className="student-card__heading"><div><p className="student-card__eyebrow">APPEARANCE</p><h2>Theme</h2></div></div>
          <p className="student-card__support">Current appearance: {mode === "dark" ? "Dark" : "Light"} mode. Theme preferences are saved in this browser.</p>
          <div className="student-theme-control"><ThemeControls /></div>
        </section>
      </div>
      <section className="student-card student-settings-logout">
        <div><h2>Sign out</h2><p>End this session and return to the sign-in page.</p></div>
        <button className="student-button student-button--secondary" onClick={logout} type="button">Logout</button>
      </section>
    </>
  );
}
