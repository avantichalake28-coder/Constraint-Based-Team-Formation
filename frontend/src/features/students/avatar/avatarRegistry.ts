export type AvatarAnimationState = "static";

export interface StudentAvatarAssets {
  id: StudentCharacterId;
  name: string;
  filename: string;
  image: string;
  format: "JPEG";
  width: number;
  height: number;
  animated: false;
  animationStates: readonly AvatarAnimationState[];
}

export const studentCharacterIds = [
  "avatar-01",
  "avatar-02",
  "avatar-03",
  "avatar-04",
  "avatar-05",
  "avatar-06",
  "avatar-07",
  "avatar-08",
  "avatar-09",
  "avatar-10",
  "avatar-11",
  "avatar-12",
  "avatar-13",
  "avatar-14",
  "avatar-15",
  "avatar-16",
] as const;

export type StudentCharacterId = (typeof studentCharacterIds)[number];

const avatar = (
  id: StudentCharacterId,
  name: string,
  filename: string,
  width: number,
  height: number,
): StudentAvatarAssets => ({
  id,
  name,
  filename,
  image: `/images/${encodeURIComponent(filename)}`,
  format: "JPEG",
  width,
  height,
  animated: false,
  animationStates: ["static"],
});

export const studentAvatarRegistry: Readonly<
  Record<StudentCharacterId, StudentAvatarAssets>
> = {
  "avatar-01": avatar("avatar-01", "Avatar 1", "Avatar 1.jpg", 610, 1356),
  "avatar-02": avatar("avatar-02", "Avatar 2", "Avatar 2.jpg", 610, 1356),
  "avatar-03": avatar("avatar-03", "Avatar 3", "Avatar 3.jpg", 610, 1356),
  "avatar-04": avatar("avatar-04", "Avatar 4", "Avatar 4.jpg", 610, 1356),
  "avatar-05": avatar("avatar-05", "Avatar 5", "Avatar 5.jpg", 610, 1356),
  "avatar-06": avatar("avatar-06", "Avatar 6", "Avatar 6.jpg", 1220, 2712),
  "avatar-07": avatar("avatar-07", "Avatar 7", "Avatar 7.jpg", 610, 1356),
  "avatar-08": avatar("avatar-08", "Avatar 8", "Avatar 8.jpg", 610, 1356),
  "avatar-09": avatar("avatar-09", "Avatar 9", "Avatar 9.jpg", 610, 1356),
  "avatar-10": avatar("avatar-10", "Avatar 10", "Avatar 10.jpg", 610, 1356),
  "avatar-11": avatar("avatar-11", "Avatar 11", "Avatar 11.jpg", 610, 1356),
  "avatar-12": avatar("avatar-12", "Avatar 12", "Avatar 12.jpg", 610, 1356),
  "avatar-13": avatar("avatar-13", "Avatar 13", "Avatar 13.jpg", 610, 1356),
  "avatar-14": avatar("avatar-14", "Avatar 14", "Avatar 14.jpg", 610, 1356),
  "avatar-15": avatar("avatar-15", "Avatar 15", "Avatar 15.jpg", 610, 1356),
  "avatar-16": avatar("avatar-16", "Avatar 16", "Avatar 16.jpg", 610, 1356),
};

export const registeredStudentAvatarCount = Object.keys(
  studentAvatarRegistry,
).length;
