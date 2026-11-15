import { apiClient } from "../api/client";
import type {
  ApiResponse,
  CurrentUser,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
} from "../../types/auth";

function requireData<T>(response: ApiResponse<T>): T {
  if (!response.success) {
    throw new Error(response.message || "The server could not complete the request.");
  }
  return response.data;
}

export const authService = {
  async login(payload: LoginRequest): Promise<LoginResponse> {
    const response = await apiClient.post<ApiResponse<LoginResponse>>(
      "/api/v1/auth/login",
      payload,
    );
    const login = requireData(response.data);
    if (
      !login.accessToken ||
      login.tokenType.toLowerCase() !== "bearer" ||
      login.expiresInSeconds <= 0
    ) {
      throw new Error("The server returned an invalid sign-in response.");
    }
    return login;
  },

  async register(payload: RegisterRequest): Promise<RegisterResponse> {
    const response = await apiClient.post<ApiResponse<RegisterResponse>>(
      "/api/v1/auth/register",
      payload,
    );
    return requireData(response.data);
  },

  async me(): Promise<CurrentUser> {
    const response = await apiClient.get<ApiResponse<CurrentUser>>(
      "/api/v1/auth/me",
    );
    return requireData(response.data);
  },

  async resendVerification(email: string): Promise<void> {
    const response = await apiClient.post<ApiResponse<null>>(
      "/api/v1/auth/resend-verification",
      { email },
    );
    requireData(response.data);
  },

  async verifyEmail(token: string): Promise<void> {
    const response = await apiClient.get<ApiResponse<null>>(
      "/api/v1/auth/verify-email",
      { params: { token } },
    );
    requireData(response.data);
  },
};
