import { apiClient } from "../../services/api/client";
import type { ApiResponse } from "../../types/auth";

export interface AppMetadata {
  metaKey: string;
  metaValue: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateMentorRequest {
  fullName: string;
  email: string;
  initialPassword: string;
}

export interface MentorResponse {
  id: number;
  name: string;
  email: string;
  role: "MENTOR";
}

export interface AdminOverview {
  studentCount: number;
  mentorCount: number;
  projectsByStatus: Record<string, number>;
  formationJobsByStatus: Record<string, number>;
}

export interface AdminStudent {
  id: number;
  name: string;
  email: string;
  srn: string;
  department: string;
  semester: number;
  active: boolean;
  projectCount: number;
}

export interface AdminMentor {
  id: number;
  name: string;
  email: string;
  active: boolean;
  projectCount: number;
}

export type ProjectStatus = "DRAFT" | "PUBLISHED" | "COMPLETED";
export type JobStatus = "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED";

export interface AdminProject {
  id: number;
  title: string;
  mentorName: string;
  status: ProjectStatus;
  rosterSize: number;
  locked: boolean;
  latestFormationJobStatus: JobStatus | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface PagingParams {
  search?: string;
  page?: number;
  size?: number;
}

export interface ProjectQueryParams {
  status?: ProjectStatus | string;
  page?: number;
  size?: number;
}

function requireData<T>(response: ApiResponse<T>): T {
  if (!response.success) {
    throw new Error(response.message || "The server could not complete the request.");
  }
  return response.data;
}

export const adminService = {
  async getAllMetadata(): Promise<AppMetadata[]> {
    const response = await apiClient.get<ApiResponse<AppMetadata[]>>("/api/v1/metadata");
    return requireData(response.data);
  },

  async createMentor(payload: CreateMentorRequest): Promise<MentorResponse> {
    const response = await apiClient.post<ApiResponse<MentorResponse>>(
      "/api/v1/admin/mentors",
      payload,
    );
    return requireData(response.data);
  },

  async getOverview(): Promise<AdminOverview> {
    const response = await apiClient.get<ApiResponse<AdminOverview>>("/api/v1/admin/overview");
    return requireData(response.data);
  },

  async getStudents(params?: PagingParams): Promise<PageResponse<AdminStudent>> {
    const response = await apiClient.get<ApiResponse<PageResponse<AdminStudent>>>(
      "/api/v1/admin/students",
      {
        params: {
          search: params?.search?.trim() ? params.search.trim() : undefined,
          page: params?.page ?? 0,
          size: params?.size ?? 20,
        },
      },
    );
    return requireData(response.data);
  },

  async getMentors(params?: PagingParams): Promise<PageResponse<AdminMentor>> {
    const response = await apiClient.get<ApiResponse<PageResponse<AdminMentor>>>(
      "/api/v1/admin/mentors",
      {
        params: {
          search: params?.search?.trim() ? params.search.trim() : undefined,
          page: params?.page ?? 0,
          size: params?.size ?? 20,
        },
      },
    );
    return requireData(response.data);
  },

  async getProjects(params?: ProjectQueryParams): Promise<PageResponse<AdminProject>> {
    const response = await apiClient.get<ApiResponse<PageResponse<AdminProject>>>(
      "/api/v1/admin/projects",
      {
        params: {
          status: params?.status ? params.status : undefined,
          page: params?.page ?? 0,
          size: params?.size ?? 20,
        },
      },
    );
    return requireData(response.data);
  },
};
