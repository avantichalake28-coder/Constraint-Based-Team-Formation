import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StudentAvatar } from "./StudentAvatar";
import {
  registeredStudentAvatarCount,
  studentAvatarRegistry,
  studentCharacterIds,
} from "./avatarRegistry";

describe("StudentAvatar", () => {
  it("registers exactly 16 unique original, static JPEG character files", () => {
    expect(registeredStudentAvatarCount).toBe(16);
    expect(studentCharacterIds).toHaveLength(16);
    expect(new Set(studentCharacterIds).size).toBe(16);

    for (const [index, characterId] of studentCharacterIds.entries()) {
      const character = studentAvatarRegistry[characterId];
      expect(character.format).toBe("JPEG");
      expect(character.animated).toBe(false);
      expect(character.animationStates).toEqual(["static"]);
      expect(character.filename).toBe(`Avatar ${index + 1}.jpg`);
      expect(character.image).toBe(
        `/images/Avatar%20${index + 1}.jpg`,
      );
    }
  });

  it("renders every character from its unchanged full-portrait source", () => {
    render(
      <>
        {studentCharacterIds.map((characterId) => (
          <StudentAvatar characterId={characterId} key={characterId} />
        ))}
      </>,
    );

    const images = screen.getAllByRole("img");
    expect(images).toHaveLength(16);
    images.forEach((image, index) => {
      const character = studentAvatarRegistry[studentCharacterIds[index]!];
      expect(image).toHaveAttribute("src", character.image);
      expect(image).toHaveAttribute("width", String(character.width));
      expect(image).toHaveAttribute("height", String(character.height));
    });
  });
});
