import { apiClient } from "../api/client";
import type { ApiResponse } from "../../types/auth";
import type { PageResponse } from "./studentService";

export interface StudentProject {
  id: number;
  mentorId: number;
  mentorName: string;
  title: string;
  problemStatement: string;
  courseCode: string;
  minTeamSize: number;
  maxTeamSize: number;
  teamSizeSlack: number;
  preferenceDeadline: string | null;
  preferencesLockedAt: string | null;
  preferencesLocked: boolean;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface Classmate {
  studentId: number;
  fullName: string;
  srn: string;
}

export interface TeammatePreference {
  id: number;
  studentId: number;
  studentSrn: string | null;
  studentName: string;
  preferredStudentId: number;
  preferredStudentSrn: string | null;
  preferredStudentName: string;
  rank: number;
  submittedAt: string;
}

function requireData<T>(response: ApiResponse<T>): T {
  if (!response.success) {
    throw new Error(response.message || "The server could not complete the request.");
  }
  return response.data;
}

export const projectService = {
  async getMyProjects(): Promise<PageResponse<StudentProject>> {
    const response = await apiClient.get<ApiResponse<PageResponse<StudentProject>>>(
      "/api/v1/projects",
      { params: { page: 0, size: 100 } },
    );
    return requireData(response.data);
  },

  async getClassmates(projectId: number): Promise<PageResponse<Classmate>> {
    const response = await apiClient.get<ApiResponse<PageResponse<Classmate>>>(
      `/api/v1/projects/${projectId}/classmates`,
      { params: { page: 0, size: 100 } },
    );
    return requireData(response.data);
  },

  async getMyPreferences(projectId: number): Promise<TeammatePreference[]> {
    const response = await apiClient.get<ApiResponse<TeammatePreference[]>>(
      `/api/v1/projects/${projectId}/preferences/me`,
    );
    return requireData(response.data);
  },

  async replaceMyPreferences(
    projectId: number,
    preferredStudentIds: number[],
  ): Promise<TeammatePreference[]> {
    const response = await apiClient.put<ApiResponse<TeammatePreference[]>>(
      `/api/v1/projects/${projectId}/preferences`,
      { preferredStudentIds },
    );
    return requireData(response.data);
  },
};
