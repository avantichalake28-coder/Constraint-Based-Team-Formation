import { create } from "zustand";
import type { CurrentUser } from "../types/auth";
import {
  clearAccessToken,
  getAccessToken,
} from "../services/auth/accessToken";

interface AuthState {
  accessToken: string | null;
  user: CurrentUser | null;
  signIn: (user: CurrentUser) => void;
  setUser: (user: CurrentUser) => void;
  signOut: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: getAccessToken(),
  user: null,
  signIn: (user) => {
    set({ accessToken: getAccessToken(), user });
  },
  setUser: (user) => set({ user }),
  signOut: () => {
    clearAccessToken();
    set({ accessToken: null, user: null });
  },
}));
