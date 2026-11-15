import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  adminService,
  type AdminMentor,
  type AdminOverview,
  type AdminProject,
  type AdminStudent,
  type PageResponse,
} from "../adminService";
import { AdminDashboardPage } from "./AdminDashboardPage";
import { AdminMentorManagementPage } from "./AdminMentorManagementPage";
import { AdminStudentManagementPage } from "./AdminStudentManagementPage";
import { AdminTeamFormationPage } from "./AdminTeamFormationPage";

vi.mock("../adminService", () => ({
  adminService: {
    getOverview: vi.fn(),
    getStudents: vi.fn(),
    getMentors: vi.fn(),
    getProjects: vi.fn(),
    createMentor: vi.fn(),
  },
}));

const mockOverview: AdminOverview = {
  studentCount: 15,
  mentorCount: 4,
  projectsByStatus: {
    DRAFT: 2,
    PUBLISHED: 3,
    COMPLETED: 1,
  },
  formationJobsByStatus: {
    QUEUED: 1,
    RUNNING: 1,
    COMPLETED: 5,
    FAILED: 0,
  },
};

const emptyOverview: AdminOverview = {
  studentCount: 0,
  mentorCount: 0,
  projectsByStatus: {
    DRAFT: 0,
    PUBLISHED: 0,
    COMPLETED: 0,
  },
  formationJobsByStatus: {
    QUEUED: 0,
    RUNNING: 0,
    COMPLETED: 0,
    FAILED: 0,
  },
};

const mockStudent: AdminStudent = {
  id: 1,
  name: "Alice Johnson",
  email: "alice@kletech.ac.in",
  srn: "02FE24BCS101",
  department: "CSE",
  semester: 5,
  active: true,
  projectCount: 2,
};

const mockStudentsPage: PageResponse<AdminStudent> = {
  content: [mockStudent],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
};

const emptyStudentsPage: PageResponse<AdminStudent> = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
};

const mockMentor: AdminMentor = {
  id: 10,
  name: "Dr. Robert Smith",
  email: "robert@kletech.ac.in",
  active: true,
  projectCount: 3,
};

const mockMentorsPage: PageResponse<AdminMentor> = {
  content: [mockMentor],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
};

const emptyMentorsPage: PageResponse<AdminMentor> = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
};

const mockProject: AdminProject = {
  id: 101,
  title: "Autonomous Drone Navigation",
  mentorName: "Dr. Robert Smith",
  status: "PUBLISHED",
  rosterSize: 4,
  locked: true,
  latestFormationJobStatus: "COMPLETED",
};

const mockProjectsPage: PageResponse<AdminProject> = {
  content: [mockProject],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
};

const emptyProjectsPage: PageResponse<AdminProject> = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
};

function renderWithClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>{ui}</MemoryRouter>
      </QueryClientProvider>,
    ),
  };
}

describe("Admin Dashboard Page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows loading state while overview is pending", () => {
    vi.mocked(adminService.getOverview).mockReturnValue(new Promise(() => {}));
    renderWithClient(<AdminDashboardPage />);

    expect(screen.getByText(/loading system overview…/i)).toBeInTheDocument();
  });

  it("renders system overview real counts", async () => {
    vi.mocked(adminService.getOverview).mockResolvedValue(mockOverview);
    renderWithClient(<AdminDashboardPage />);

    expect(await screen.findByText("15")).toBeInTheDocument(); // studentCount
    expect(screen.getByText("4")).toBeInTheDocument(); // mentorCount
    expect(screen.getByText("Platform counts & metrics")).toBeInTheDocument();
    expect(screen.getByText("Draft")).toBeInTheDocument();
    expect(screen.getByText("Published")).toBeInTheDocument();
    expect(screen.getAllByText("Completed").length).toBeGreaterThanOrEqual(1);
  });

  it("shows empty state when overview contains no activity", async () => {
    vi.mocked(adminService.getOverview).mockResolvedValue(emptyOverview);
    renderWithClient(<AdminDashboardPage />);

    expect(await screen.findByText(/no system activity recorded yet/i)).toBeInTheDocument();
  });

  it("shows error state when query fails", async () => {
    vi.mocked(adminService.getOverview).mockRejectedValue(new Error("Network failure"));
    renderWithClient(<AdminDashboardPage />);

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByText(/could not load system overview/i)).toBeInTheDocument();
    expect(screen.getByText("Network failure")).toBeInTheDocument();
  });
});

describe("Admin Student Management Page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows loading state while students are pending", () => {
    vi.mocked(adminService.getStudents).mockReturnValue(new Promise(() => {}));
    renderWithClient(<AdminStudentManagementPage />);

    expect(screen.getByText(/loading students…/i)).toBeInTheDocument();
  });

  it("renders paged student list with full details", async () => {
    vi.mocked(adminService.getStudents).mockResolvedValue(mockStudentsPage);
    renderWithClient(<AdminStudentManagementPage />);

    expect(await screen.findByText("Alice Johnson")).toBeInTheDocument();
    expect(screen.getByText(/02FE24BCS101/)).toBeInTheDocument();
    expect(screen.getByText(/alice@kletech.ac.in/)).toBeInTheDocument();
    expect(screen.getByText(/CSE · Semester 5/)).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("2 projects")).toBeInTheDocument();
    expect(screen.getByLabelText(/previous page/i)).toBeDisabled();
  });

  it("shows empty state when no students are found", async () => {
    vi.mocked(adminService.getStudents).mockResolvedValue(emptyStudentsPage);
    renderWithClient(<AdminStudentManagementPage />);

    expect(await screen.findByText(/no students found/i)).toBeInTheDocument();
  });

  it("shows error state when query fails", async () => {
    vi.mocked(adminService.getStudents).mockRejectedValue(new Error("Forbidden access"));
    renderWithClient(<AdminStudentManagementPage />);

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByText(/could not load students/i)).toBeInTheDocument();
    expect(screen.getByText("Forbidden access")).toBeInTheDocument();
  });

  it("sends search query when typing in the search box", async () => {
    vi.mocked(adminService.getStudents).mockResolvedValue(mockStudentsPage);
    renderWithClient(<AdminStudentManagementPage />);

    const searchInput = screen.getByLabelText(/search students/i);
    fireEvent.change(searchInput, { target: { value: "02FE24BCS101" } });

    await waitFor(() => {
      expect(adminService.getStudents).toHaveBeenCalledWith(
        expect.objectContaining({ search: "02FE24BCS101", page: 0 }),
      );
    });
  });
});

describe("Admin Mentor Management Page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows loading state while mentors are pending", () => {
    vi.mocked(adminService.getMentors).mockReturnValue(new Promise(() => {}));
    renderWithClient(<AdminMentorManagementPage />);

    expect(screen.getByText(/loading mentors…/i)).toBeInTheDocument();
  });

  it("renders paged mentor list with details", async () => {
    vi.mocked(adminService.getMentors).mockResolvedValue(mockMentorsPage);
    renderWithClient(<AdminMentorManagementPage />);

    expect(await screen.findByText("Dr. Robert Smith")).toBeInTheDocument();
    expect(screen.getByText(/robert@kletech.ac.in/)).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("3 projects")).toBeInTheDocument();
  });

  it("shows empty state when no mentors are found", async () => {
    vi.mocked(adminService.getMentors).mockResolvedValue(emptyMentorsPage);
    renderWithClient(<AdminMentorManagementPage />);

    expect(await screen.findByText(/no mentors found/i)).toBeInTheDocument();
  });

  it("shows error state when query fails", async () => {
    vi.mocked(adminService.getMentors).mockRejectedValue(new Error("Service unavailable"));
    renderWithClient(<AdminMentorManagementPage />);

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByText(/could not load mentors/i)).toBeInTheDocument();
    expect(screen.getByText("Service unavailable")).toBeInTheDocument();
  });

  it("sends search query when searching mentors", async () => {
    vi.mocked(adminService.getMentors).mockResolvedValue(mockMentorsPage);
    renderWithClient(<AdminMentorManagementPage />);

    const searchInput = screen.getByLabelText(/search mentors/i);
    fireEvent.change(searchInput, { target: { value: "Robert" } });

    await waitFor(() => {
      expect(adminService.getMentors).toHaveBeenCalledWith(
        expect.objectContaining({ search: "Robert", page: 0 }),
      );
    });
  });

  it("refreshes the mentor list after creating a mentor", async () => {
    vi.mocked(adminService.getMentors).mockResolvedValue(mockMentorsPage);
    vi.mocked(adminService.createMentor).mockResolvedValue({
      id: 11,
      name: "Dr. Alan Turing",
      email: "alan@kletech.ac.in",
      role: "MENTOR",
    });

    const { queryClient } = renderWithClient(<AdminMentorManagementPage />);
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");

    expect(await screen.findByText("Dr. Robert Smith")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/^full name$/i), { target: { value: "Dr. Alan Turing" } });
    fireEvent.change(screen.getByLabelText(/^email$/i), { target: { value: "alan@kletech.ac.in" } });
    fireEvent.change(screen.getByLabelText(/initial password/i), { target: { value: "SecretPassword123" } });

    fireEvent.click(screen.getByRole("button", { name: /create mentor account/i }));

    await waitFor(() => {
      expect(adminService.createMentor).toHaveBeenCalledWith({
        fullName: "Dr. Alan Turing",
        email: "alan@kletech.ac.in",
        initialPassword: "SecretPassword123",
      });
    });

    expect(await screen.findByText(/mentor account created for dr\. alan turing/i)).toBeInTheDocument();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["admin", "mentors"] });
  });
});

describe("Admin Team Formation Page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows loading state while projects are pending", () => {
    vi.mocked(adminService.getProjects).mockReturnValue(new Promise(() => {}));
    renderWithClient(<AdminTeamFormationPage />);

    expect(screen.getByText(/loading projects…/i)).toBeInTheDocument();
  });

  it("renders paged project list with read-only details", async () => {
    vi.mocked(adminService.getProjects).mockResolvedValue(mockProjectsPage);
    renderWithClient(<AdminTeamFormationPage />);

    expect(await screen.findByText("Autonomous Drone Navigation")).toBeInTheDocument();
    expect(screen.getByText(/Dr\. Robert Smith/)).toBeInTheDocument();
    expect(screen.getByText("PUBLISHED")).toBeInTheDocument();
    expect(screen.getByText("Roster: 4")).toBeInTheDocument();
    expect(screen.getByText("Locked")).toBeInTheDocument();
    expect(screen.getByText("Latest job: COMPLETED")).toBeInTheDocument();

    // Verify read-only: no buttons that mutate or start formation
    expect(screen.queryByRole("button", { name: /start/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /finalize/i })).not.toBeInTheDocument();
  });

  it("shows empty state when no projects are found", async () => {
    vi.mocked(adminService.getProjects).mockResolvedValue(emptyProjectsPage);
    renderWithClient(<AdminTeamFormationPage />);

    expect(await screen.findByText(/no projects found/i)).toBeInTheDocument();
  });

  it("shows error state when query fails", async () => {
    vi.mocked(adminService.getProjects).mockRejectedValue(new Error("Unauthorized"));
    renderWithClient(<AdminTeamFormationPage />);

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByText(/could not load projects/i)).toBeInTheDocument();
    expect(screen.getByText("Unauthorized")).toBeInTheDocument();
  });

  it("filters projects when changing status filter", async () => {
    vi.mocked(adminService.getProjects).mockResolvedValue(mockProjectsPage);
    renderWithClient(<AdminTeamFormationPage />);

    const statusSelect = screen.getByLabelText(/filter by project status/i);
    fireEvent.change(statusSelect, { target: { value: "PUBLISHED" } });

    await waitFor(() => {
      expect(adminService.getProjects).toHaveBeenCalledWith(
        expect.objectContaining({ status: "PUBLISHED", page: 0 }),
      );
    });
  });
});
