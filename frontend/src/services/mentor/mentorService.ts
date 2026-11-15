import { apiClient } from "../api/client";
import { authService } from "../auth/authService";
import type { PageResponse, SkillCatalogEntry } from "../students/studentService";
import type { ApiResponse } from "../../types/auth";

export interface MentorProject {
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

export interface ProjectPayload {
  title: string;
  problemStatement: string;
  courseCode: string;
  minTeamSize: number;
  maxTeamSize: number;
  teamSizeSlack: number;
  preferenceDeadline: string | null;
}

export interface RosterEntry {
  id: number;
  studentId: number;
  srn: string;
  name: string;
  email: string;
  eligibilityStatus: string;
  enrolledAt: string;
}

export interface RosterAddResponse {
  added: RosterEntry[];
  alreadyOnRoster: string[];
}

export interface ProhibitedPair {
  studentId1: number;
  studentId2: number;
}

export interface MandatorySkill {
  skillId: number;
  minimumProficiency: number | null;
}

export interface ConstraintConfig {
  version: number;
  prohibitedPairs: ProhibitedPair[];
  mandatorySkills: MandatorySkill[];
  changedById: number | null;
  changedByName: string | null;
  changedAt: string | null;
}

export interface MandatorySkillCoverage {
  skillId: number;
  skillName: string;
  minimumProficiency: number;
  satisfiedCount: number;
  availableCount: number;
}

export interface FeasibilityFinding {
  code: string;
  message: string;
}

export interface FeasibilityReport {
  projectId: number;
  eligibleStudents: number;
  constraintVersion: number;
  minTeamSize: number;
  maxTeamSize: number;
  slack: number;
  status: string;
  planner: {
    teamSize: number;
    explanation: string;
  } | null;
  mandatorySkills: MandatorySkillCoverage[];
  findings: FeasibilityFinding[];
  disclaimer: string;
}

export interface FormationJobSummary {
  id: number;
  projectId: number;
  jobStatus: string;
  constraintVersion: number;
  seed: number | null;
  algorithmVersion: string | null;
  startedAt: string | null;
  completedAt: string | null;
  errorMessage: string | null;
  failureCode: string | null;
  failurePhase: string | null;
  durationMillis: number | null;
  createdAt: string | null;
}

export interface CandidateTeamMember {
  studentId: number;
  fullName: string;
  srn: string | null;
  skills: string[];
}

export interface CandidateTeam {
  id: number;
  index: number;
  teamSize: number;
  score: number;
  members: CandidateTeamMember[];
}

export interface FormationJobDetails extends FormationJobSummary {
  teamCount: number;
  assignedStudentCount: number;
  totalPreferenceScore: number;
  unsatisfiedPreferenceStudents: number;
  studentsWithoutPreferences: number;
  teams: CandidateTeam[];
}

function requireData<T>(response: ApiResponse<T>): T {
  if (!response.success) {
    throw new Error(response.message || "The server could not complete the request.");
  }
  return response.data;
}

export const mentorService = {
  async getProfile() {
    return authService.me();
  },

  async getProjects(): Promise<PageResponse<MentorProject>> {
    const response = await apiClient.get<ApiResponse<PageResponse<MentorProject>>>(
      "/api/v1/projects",
      { params: { page: 0, size: 100 } },
    );
    return requireData(response.data);
  },

  async getProject(projectId: number): Promise<MentorProject> {
    const response = await apiClient.get<ApiResponse<MentorProject>>(`/api/v1/projects/${projectId}`);
    return requireData(response.data);
  },

  async createProject(payload: ProjectPayload): Promise<MentorProject> {
    const response = await apiClient.post<ApiResponse<MentorProject>>("/api/v1/projects", payload);
    return requireData(response.data);
  },

  async updateProject(projectId: number, payload: ProjectPayload): Promise<MentorProject> {
    const response = await apiClient.put<ApiResponse<MentorProject>>(
      `/api/v1/projects/${projectId}`,
      payload,
    );
    return requireData(response.data);
  },

  async publishProject(projectId: number): Promise<MentorProject> {
    const response = await apiClient.post<ApiResponse<MentorProject>>(
      `/api/v1/projects/${projectId}/publish`,
    );
    return requireData(response.data);
  },

  async lockPreferences(projectId: number): Promise<{ projectId: number; locked: boolean; lockedAt: string | null; status: string }> {
    const response = await apiClient.post<ApiResponse<{ projectId: number; locked: boolean; lockedAt: string | null; status: string }>>(
      `/api/v1/projects/${projectId}/lock-preferences`,
    );
    return requireData(response.data);
  },

  async getRoster(projectId: number): Promise<PageResponse<RosterEntry>> {
    const response = await apiClient.get<ApiResponse<PageResponse<RosterEntry>>>(
      `/api/v1/projects/${projectId}/roster`,
      { params: { page: 0, size: 100 } },
    );
    return requireData(response.data);
  },

  async addToRoster(projectId: number, srns: string[]): Promise<RosterAddResponse> {
    const response = await apiClient.post<ApiResponse<RosterAddResponse>>(
      `/api/v1/projects/${projectId}/roster`,
      { srns },
    );
    return requireData(response.data);
  },

  async removeFromRoster(projectId: number, studentId: number): Promise<void> {
    const response = await apiClient.delete<ApiResponse<void>>(
      `/api/v1/projects/${projectId}/roster/${studentId}`,
    );
    requireData(response.data);
  },

  async getConstraints(projectId: number): Promise<ConstraintConfig> {
    const response = await apiClient.get<ApiResponse<ConstraintConfig>>(
      `/api/v1/projects/${projectId}/constraints`,
    );
    return requireData(response.data);
  },

  async replaceConstraints(projectId: number, payload: { prohibitedPairs: ProhibitedPair[]; mandatorySkills: MandatorySkill[] }): Promise<ConstraintConfig> {
    const response = await apiClient.put<ApiResponse<ConstraintConfig>>(
      `/api/v1/projects/${projectId}/constraints`,
      payload,
    );
    return requireData(response.data);
  },

  async checkFeasibility(projectId: number): Promise<FeasibilityReport> {
    const response = await apiClient.post<ApiResponse<FeasibilityReport>>(
      `/api/v1/projects/${projectId}/feasibility`,
    );
    return requireData(response.data);
  },

  async startFormation(projectId: number): Promise<FormationJobDetails> {
    const response = await apiClient.post<ApiResponse<FormationJobDetails>>(
      `/api/v1/projects/${projectId}/formation-jobs`,
    );
    return requireData(response.data);
  },

  async listFormationJobs(projectId: number): Promise<PageResponse<FormationJobSummary>> {
    const response = await apiClient.get<ApiResponse<PageResponse<FormationJobSummary>>>(
      `/api/v1/projects/${projectId}/formation-jobs`,
      { params: { page: 0, size: 20 } },
    );
    return requireData(response.data);
  },

  async getFormationJob(projectId: number, jobId: number): Promise<FormationJobDetails> {
    const response = await apiClient.get<ApiResponse<FormationJobDetails>>(
      `/api/v1/projects/${projectId}/formation-jobs/${jobId}`,
    );
    return requireData(response.data);
  },

  async getSkillCatalog(): Promise<PageResponse<SkillCatalogEntry>> {
    return apiClient
      .get<ApiResponse<PageResponse<SkillCatalogEntry>>>("/api/v1/skills", { params: { page: 0, size: 200 } })
      .then((response) => requireData(response.data));
  },

  async createSkill(payload: { name: string; category?: string | null; description?: string | null }): Promise<{ id: number; name: string; category: string | null; description: string | null }> {
    const response = await apiClient.post<ApiResponse<{ id: number; name: string; category: string | null; description: string | null }>>(
      "/api/v1/skills",
      payload,
    );
    return requireData(response.data);
  },
};
