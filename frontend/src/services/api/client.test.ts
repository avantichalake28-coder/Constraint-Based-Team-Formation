import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "./client";
import { getAccessToken, setAccessToken } from "../auth/accessToken";
import { useAuthStore } from "../../stores/authStore";
import type { CurrentUser } from "../../types/auth";

const user: CurrentUser = {
  id: 8,
  name: "Student Example",
  email: "student@college.edu",
  role: "STUDENT",
  srn: "02FE24BCS141",
};

describe("API authorization handling", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    useAuthStore.getState().signOut();
    apiClient.defaults.adapter = async (config) => ({
      config,
      data: {},
      headers: {},
      status: 403,
      statusText: "Forbidden",
    });
  });

  it("keeps the session when the server denies a role-scoped request", async () => {
    setAccessToken("valid-token", 3600, false);
    useAuthStore.setState({ accessToken: "valid-token", user });
    apiClient.defaults.adapter = async (config) => {
      throw Object.assign(new Error("Forbidden"), {
        config,
        isAxiosError: true,
        response: { status: 403, config, data: {} },
      });
    };

    await expect(apiClient.get("/restricted")).rejects.toThrow("Forbidden");

    expect(getAccessToken()).toBe("valid-token");
    expect(useAuthStore.getState().user).toEqual(user);
  });

  it("clears the session after an unauthorized response", async () => {
    setAccessToken("expired-token", 3600, false);
    useAuthStore.setState({ accessToken: "expired-token", user });
    apiClient.defaults.adapter = async (config) => {
      throw Object.assign(new Error("Unauthorized"), {
        config,
        isAxiosError: true,
        response: { status: 401, config, data: {} },
      });
    };

    await expect(apiClient.get("/private")).rejects.toThrow("Unauthorized");

    expect(getAccessToken()).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
  });
});
