export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface ApiErrorResponse {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
  validationErrors?: Record<string, string> | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: "Bearer" | string;
  expiresInSeconds: number;
}

export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
  srn: string;
}

export interface RegisterResponse {
  id: number;
  email: string;
}

export interface CurrentUser {
  id: number;
  name: string;
  email: string;
  role: "STUDENT" | "MENTOR" | "ADMIN";
  srn: string | null;
}
