import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "../api/client";
import { projectService } from "./projectService";
import { studentService } from "./studentService";

vi.mock("../api/client", () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
  },
}));

function apiResponse<T>(data: T) {
  return { data: { success: true, message: "OK", data, timestamp: "" } };
}

describe("student API services", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("updates only the supported profile fields through the student profile API", async () => {
    vi.mocked(apiClient.put).mockResolvedValue(apiResponse({ interests: "Design" }));

    await studentService.updateProfile({ interests: "Design" });

    expect(apiClient.put).toHaveBeenCalledWith("/api/v1/students/me", {
      interests: "Design",
    });
  });

  it("replaces skills using the backend proficiency contract", async () => {
    vi.mocked(apiClient.put).mockResolvedValue(apiResponse([]));

    await studentService.replaceSkills([{ skillId: 14, proficiency: 4 }]);

    expect(apiClient.put).toHaveBeenCalledWith("/api/v1/students/me/skills", {
      skills: [{ skillId: 14, proficiency: 4 }],
    });
  });

  it("reads the current student project roster using the paged project API", async () => {
    vi.mocked(apiClient.get).mockResolvedValue(apiResponse({ content: [] }));

    await projectService.getMyProjects();

    expect(apiClient.get).toHaveBeenCalledWith("/api/v1/projects", {
      params: { page: 0, size: 100 },
    });
  });

  it("submits ranked teammate ids to the existing project preference endpoint", async () => {
    vi.mocked(apiClient.put).mockResolvedValue(apiResponse([]));

    await projectService.replaceMyPreferences(6, [12, 14]);

    expect(apiClient.put).toHaveBeenCalledWith(
      "/api/v1/projects/6/preferences",
      { preferredStudentIds: [12, 14] },
    );
  });
});
