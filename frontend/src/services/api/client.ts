import axios from "axios";
import { clearAccessToken, getAccessToken } from "../auth/accessToken";
import { useAuthStore } from "../../stores/authStore";

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8081",
  timeout: 15_000,
  headers: { "Content-Type": "application/json" },
});

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const activeToken = getAccessToken();
      if (activeToken) {
        clearAccessToken();
        useAuthStore.getState().signOut();
      }
    }
    return Promise.reject(error);
  },
);
