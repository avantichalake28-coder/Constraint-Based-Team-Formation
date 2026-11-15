import { apiClient } from "../api/client";
import type { ApiResponse } from "../../types/auth";

export interface StudentProfile {
  id: number;
  srn: string;
  name: string;
  email: string;
  department: string | null;
  semester: number | null;
  interests: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateStudentProfile {
  fullName?: string;
  department?: string;
  semester?: number;
  interests?: string;
}

export interface StudentSkill {
  skillId: number;
  skillName: string;
  category: string | null;
  proficiency: number;
}

export interface SkillCatalogEntry {
  id: number;
  name: string;
  category: string | null;
  description: string | null;
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

function requireData<T>(response: ApiResponse<T>): T {
  if (!response.success) {
    throw new Error(response.message || "The server could not complete the request.");
  }
  return response.data;
}

export const studentService = {
  async getProfile(): Promise<StudentProfile> {
    const response = await apiClient.get<ApiResponse<StudentProfile>>(
      "/api/v1/students/me",
    );
    return requireData(response.data);
  },

  async updateProfile(payload: UpdateStudentProfile): Promise<StudentProfile> {
    const response = await apiClient.put<ApiResponse<StudentProfile>>(
      "/api/v1/students/me",
      payload,
    );
    return requireData(response.data);
  },

  async getSkills(): Promise<StudentSkill[]> {
    const response = await apiClient.get<ApiResponse<StudentSkill[]>>(
      "/api/v1/students/me/skills",
    );
    return requireData(response.data);
  },

  async replaceSkills(skills: Array<{ skillId: number; proficiency: number }>): Promise<StudentSkill[]> {
    const response = await apiClient.put<ApiResponse<StudentSkill[]>>(
      "/api/v1/students/me/skills",
      { skills },
    );
    return requireData(response.data);
  },

  async getSkillCatalog(): Promise<PageResponse<SkillCatalogEntry>> {
    const response = await apiClient.get<ApiResponse<PageResponse<SkillCatalogEntry>>>(
      "/api/v1/skills",
      { params: { page: 0, size: 100 } },
    );
    return requireData(response.data);
  },
};
