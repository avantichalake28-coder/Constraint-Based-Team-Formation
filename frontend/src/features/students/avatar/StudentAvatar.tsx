import type { AvatarAnimationState, StudentCharacterId } from "./avatarRegistry";
import { studentAvatarRegistry } from "./avatarRegistry";
import "./avatar.css";

export type StudentAvatarSize = "small" | "medium" | "large";

interface StudentAvatarProps {
  characterId: StudentCharacterId;
  size?: StudentAvatarSize;
  animationState?: AvatarAnimationState;
  className?: string;
}

export function StudentAvatar({
  characterId,
  size = "medium",
  animationState = "static",
  className = "",
}: StudentAvatarProps) {
  const character = studentAvatarRegistry[characterId];

  return (
    <img
      alt={character.name}
      className={`student-avatar student-avatar--${size} ${className}`.trim()}
      data-character-id={character.id}
      data-animation-state={animationState}
      height={character.height}
      src={character.image}
      title={character.name}
      width={character.width}
    />
  );
}
