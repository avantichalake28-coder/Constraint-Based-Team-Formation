import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { LoaderCircle, Plus } from "lucide-react";
import { Notification } from "../../../components/Notification";
import { getApiErrorMessage } from "../../../utils/apiError";
import { adminService } from "../adminService";

export function AdminMentorManagementPage() {
  const formRef = useRef<HTMLFormElement>(null);
  const [feedback, setFeedback] = useState<{ kind: "success" | "error"; message: string } | null>(null);
  const [searchInput, setSearchInput] = useState("");
  const [searchTerm, setSearchTerm] = useState("");
  const [page, setPage] = useState(0);

  const queryClient = useQueryClient();

  useEffect(() => {
    document.title = "Mentor Management | SkillMatch Admin";
  }, []);

  const createMentor = useMutation({
    mutationFn: (payload: { fullName: string; email: string; initialPassword: string }) =>
      adminService.createMentor(payload),
    onSuccess: (mentor) => {
      formRef.current?.reset();
      setFeedback({ kind: "success", message: `Mentor account created for ${mentor.name} (${mentor.email}).` });
      void queryClient.invalidateQueries({ queryKey: ["admin", "mentors"] });
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getApiErrorMessage(error, "Unable to create the mentor account.") });
    },
  });

  const mentorsQuery = useQuery({
    queryKey: ["admin", "mentors", { search: searchTerm, page }],
    queryFn: () => adminService.getMentors({ search: searchTerm, page, size: 20 }),
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setFeedback(null);
    createMentor.mutate({
      fullName: String(form.get("fullName") ?? "").trim(),
      email: String(form.get("email") ?? "").trim(),
      initialPassword: String(form.get("initialPassword") ?? ""),
    });
  }

  function handleSearchSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setSearchTerm(searchInput.trim());
    setPage(0);
  }

  function handleSearchClear() {
    setSearchInput("");
    setSearchTerm("");
    setPage(0);
  }

  return (
    <>
      <header className="student-page-header">
        <p className="student-page-header__eyebrow">ACCOUNT ADMINISTRATION</p>
        <h1>Mentor management</h1>
        <p>Create mentor accounts and browse active mentors with assigned project counts.</p>
      </header>

      <section className="student-card">
        <div className="student-card__heading">
          <div>
            <p className="student-card__eyebrow">NEW ACCOUNT</p>
            <h2>Create a mentor</h2>
          </div>
        </div>
        {feedback && <Notification kind={feedback.kind}>{feedback.message}</Notification>}
        <form className="student-form" onSubmit={submit} ref={formRef}>
          <div className="student-form__grid">
            <label className="student-field" htmlFor="create-mentor-name">
              Full name
              <input autoComplete="name" id="create-mentor-name" maxLength={150} name="fullName" required />
            </label>
            <label className="student-field" htmlFor="create-mentor-email">
              Email
              <input autoComplete="email" id="create-mentor-email" maxLength={150} name="email" required type="email" />
            </label>
            <label className="student-field student-field--wide" htmlFor="create-mentor-password">
              Initial password
              <input
                autoComplete="new-password"
                id="create-mentor-password"
                maxLength={72}
                minLength={10}
                name="initialPassword"
                required
                type="password"
              />
              <span className="student-field__hint">Use 10–72 characters. Backend email-domain policy still applies.</span>
            </label>
          </div>
          <button className="student-button" disabled={createMentor.isPending} type="submit">
            {createMentor.isPending ? (
              <LoaderCircle aria-hidden="true" className="spin" size={17} />
            ) : (
              <Plus aria-hidden="true" size={17} />
            )}
            Create mentor account
          </button>
        </form>
      </section>

      <section className="student-card">
        <div className="student-card__heading">
          <div>
            <p className="student-card__eyebrow">DIRECTORY</p>
            <h2>Active mentors</h2>
          </div>
          <button
            className="student-button student-button--secondary"
            disabled={mentorsQuery.isFetching}
            onClick={() => void mentorsQuery.refetch()}
            type="button"
          >
            Refresh
          </button>
        </div>

        <form className="student-inline-form" onSubmit={handleSearchSubmit} role="search">
          <label className="student-field" htmlFor="mentor-search">
            Search mentors
            <input
              id="mentor-search"
              name="mentorSearch"
              onChange={(e) => {
                setSearchInput(e.target.value);
                setSearchTerm(e.target.value);
                setPage(0);
              }}
              placeholder="Search by name or email…"
              type="search"
              value={searchInput}
            />
          </label>
          <button className="student-button student-button--secondary" type="submit">
            Search
          </button>
          {searchInput && (
            <button className="student-button student-button--secondary" onClick={handleSearchClear} type="button">
              Clear
            </button>
          )}
        </form>

        {mentorsQuery.isPending ? (
          <div aria-live="polite" className="student-state">
            <LoaderCircle aria-hidden="true" className="spin" size={22} />
            <span>Loading mentors…</span>
          </div>
        ) : mentorsQuery.isError ? (
          <div className="student-state student-state--error" role="alert">
            <strong>Could not load mentors.</strong>
            <p>{getApiErrorMessage(mentorsQuery.error)}</p>
            <button
              className="student-button student-button--secondary"
              onClick={() => void mentorsQuery.refetch()}
              type="button"
            >
              Try again
            </button>
          </div>
        ) : mentorsQuery.data.content.length === 0 ? (
          <p className="student-empty">No mentors found.</p>
        ) : (
          <>
            <div className="student-project-list">
              {mentorsQuery.data.content.map((mentor) => (
                <article className="student-project-row" key={mentor.id}>
                  <div>
                    <h3>{mentor.name}</h3>
                    <p>
                      <strong>Email:</strong> {mentor.email}
                    </p>
                  </div>
                  <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                    <span className={mentor.active ? "student-status student-status--locked" : "student-status"}>
                      {mentor.active ? "Active" : "Inactive"}
                    </span>
                    <span className="student-status">
                      {mentor.projectCount} {mentor.projectCount === 1 ? "project" : "projects"}
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
                Page {mentorsQuery.data.page + 1} of {Math.max(1, mentorsQuery.data.totalPages)} ({mentorsQuery.data.totalElements} {mentorsQuery.data.totalElements === 1 ? "mentor" : "mentors"})
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
                  disabled={mentorsQuery.data.last || page >= mentorsQuery.data.totalPages - 1}
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
