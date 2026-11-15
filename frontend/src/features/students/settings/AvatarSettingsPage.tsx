import { ArrowLeft, Check } from "lucide-react";
import { Link } from "react-router-dom";
import { useEffect } from "react";
import { Notification } from "../../../components/Notification";
import { ThemeControls } from "../../../components/ThemeControls";
import { StudentAvatar } from "../avatar/StudentAvatar";
import {
  registeredStudentAvatarCount,
  studentAvatarRegistry,
  studentCharacterIds,
} from "../avatar/avatarRegistry";
import { useStudentAvatarStore } from "../avatar/avatarStore";

export function AvatarSettingsPage() {
  useEffect(() => {
    document.title = "Avatar settings | SkillMatch";
  }, []);

  const selectedCharacterId = useStudentAvatarStore(
    (state) => state.selectedCharacterId,
  );
  const selectCharacter = useStudentAvatarStore(
    (state) => state.selectCharacter,
  );

  return (
    <main className="welcome-page">
      <header className="welcome-page__header settings-page__header">
        <Link aria-label="Back to dashboard" className="back-link" to="/app">
          <ArrowLeft aria-hidden="true" size={18} />
          <span>Dashboard</span>
        </Link>
        <div className="settings-page__actions">
          <ThemeControls />
          <p className="settings-page__count">
            {registeredStudentAvatarCount} original characters
          </p>
        </div>
      </header>

      <section aria-labelledby="avatar-settings-title" className="settings-content">
        <p className="eyebrow">PERSONALIZE YOUR PROFILE</p>
        <h1 id="avatar-settings-title">Choose your character</h1>
        <p className="settings-intro">
          Pick one of the original SkillMatch student characters to represent you.
        </p>
        <Notification kind="info">
          Your choice is saved on this browser. Account-level avatar syncing is
          not supported by the current student profile API.
        </Notification>

        <div aria-label="Student characters" className="avatar-grid" role="group">
          {studentCharacterIds.map((characterId) => {
            const character = studentAvatarRegistry[characterId];
            const isSelected = selectedCharacterId === characterId;

            return (
              <button
                aria-label={`Select ${character.name}`}
                aria-pressed={isSelected}
                className={`avatar-option${isSelected ? " avatar-option--selected" : ""}`}
                key={characterId}
                onClick={() => selectCharacter(characterId)}
                type="button"
              >
                <StudentAvatar
                  characterId={characterId}
                  className="student-avatar--selection"
                  size="large"
                />
                <span className="avatar-option__label">
                  {character.name}
                  {isSelected && <Check aria-label="Selected" size={16} />}
                </span>
              </button>
            );
          })}
        </div>
      </section>
    </main>
  );
}
