import axios from "axios";
import type { ApiErrorResponse } from "../types/auth";

export function getApiErrorMessage(
  error: unknown,
  fallback = "Something went wrong. Please try again.",
): string {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    const message = error.response?.data?.message;
    if (message) return message;
    if (!error.response) {
      return "Unable to reach SkillMatch. Check your connection and try again.";
    }
  } else if (error instanceof Error && error.message) {
    return error.message;
  }

  return fallback;
}
