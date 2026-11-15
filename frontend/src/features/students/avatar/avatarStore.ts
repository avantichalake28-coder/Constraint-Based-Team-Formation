import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import {
  studentAvatarRegistry,
  type StudentCharacterId,
} from "./avatarRegistry";

interface StudentAvatarState {
  selectedCharacterId: StudentCharacterId;
  selectCharacter: (characterId: StudentCharacterId) => void;
}

export const useStudentAvatarStore = create<StudentAvatarState>()(
  persist(
    (set) => ({
      selectedCharacterId: "avatar-01",
      selectCharacter: (selectedCharacterId) => set({ selectedCharacterId }),
    }),
    {
      name: "skillmatch.student-avatar",
      storage: createJSONStorage(() => localStorage),
      partialize: ({ selectedCharacterId }) => ({ selectedCharacterId }),
      merge: (persistedState, currentState) => {
        if (
          typeof persistedState !== "object" ||
          persistedState === null ||
          !("selectedCharacterId" in persistedState)
        ) {
          return currentState;
        }

        const character = Object.values(studentAvatarRegistry).find(
          ({ id }) => id === persistedState.selectedCharacterId,
        );
        if (!character) {
          return currentState;
        }

        return {
          ...currentState,
          selectedCharacterId: character.id,
        };
      },
    },
  ),
);
