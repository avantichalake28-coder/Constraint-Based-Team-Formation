import { useQuery } from "@tanstack/react-query";
import { useEffect, useState, type FormEvent } from "react";
import { LoaderCircle } from "lucide-react";
import { adminService } from "../adminService";
import { getApiErrorMessage } from "../../../utils/apiError";

export function AdminStudentManagementPage() {
  const [searchInput, setSearchInput] = useState("");
  const [searchTerm, setSearchTerm] = useState("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    document.title = "Student Management | SkillMatch Admin";
  }, []);

  const studentsQuery = useQuery({
    queryKey: ["admin", "students", { search: searchTerm, page }],
    queryFn: () => adminService.getStudents({ search: searchTerm, page, size: 20 }),
  });

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
        <h1>Student management</h1>
        <p>Browse, search, and view enrolled student profiles, registration status, and assigned project counts.</p>
      </header>

      <section className="student-card">
        <div className="student-card__heading">
          <div>
            <p className="student-card__eyebrow">DIRECTORY</p>
            <h2>Enrolled students</h2>
          </div>
          <button
            className="student-button student-button--secondary"
            disabled={studentsQuery.isFetching}
            onClick={() => void studentsQuery.refetch()}
            type="button"
          >
            Refresh
          </button>
        </div>

        <form className="student-inline-form" onSubmit={handleSearchSubmit} role="search">
          <label className="student-field" htmlFor="student-search">
            Search students
            <input
              id="student-search"
              name="studentSearch"
              onChange={(e) => {
                setSearchInput(e.target.value);
                setSearchTerm(e.target.value);
                setPage(0);
              }}
              placeholder="Search by name, SRN, or email…"
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

        {studentsQuery.isPending ? (
          <div aria-live="polite" className="student-state">
            <LoaderCircle aria-hidden="true" className="spin" size={22} />
            <span>Loading students…</span>
          </div>
        ) : studentsQuery.isError ? (
          <div className="student-state student-state--error" role="alert">
            <strong>Could not load students.</strong>
            <p>{getApiErrorMessage(studentsQuery.error)}</p>
            <button
              className="student-button student-button--secondary"
              onClick={() => void studentsQuery.refetch()}
              type="button"
            >
              Try again
            </button>
          </div>
        ) : studentsQuery.data.content.length === 0 ? (
          <p className="student-empty">No students found.</p>
        ) : (
          <>
            <div className="student-project-list">
              {studentsQuery.data.content.map((student) => (
                <article className="student-project-row" key={student.id}>
                  <div>
                    <h3>{student.name}</h3>
                    <p>
                      <strong>SRN:</strong> {student.srn} · <strong>Email:</strong> {student.email} · {student.department} · Semester {student.semester}
                    </p>
                  </div>
                  <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                    <span className={student.active ? "student-status student-status--locked" : "student-status"}>
                      {student.active ? "Active" : "Inactive"}
                    </span>
                    <span className="student-status">
                      {student.projectCount} {student.projectCount === 1 ? "project" : "projects"}
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
                Page {studentsQuery.data.page + 1} of {Math.max(1, studentsQuery.data.totalPages)} ({studentsQuery.data.totalElements} {studentsQuery.data.totalElements === 1 ? "student" : "students"})
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
                  disabled={studentsQuery.data.last || page >= studentsQuery.data.totalPages - 1}
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
