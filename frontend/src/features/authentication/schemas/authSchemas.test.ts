import { describe, expect, it } from "vitest";
import { loginSchema, registerSchema } from "./authSchemas";

describe("authentication schemas", () => {
  it("requires a valid email and password for sign-in", () => {
    expect(loginSchema.safeParse({ email: "", password: "", rememberMe: true }).success).toBe(false);
    expect(loginSchema.safeParse({ email: "not-an-email", password: "pass", rememberMe: true }).success).toBe(false);
    expect(loginSchema.safeParse({ email: "student@college.edu", password: "pass", rememberMe: true }).success).toBe(true);
  });

  it("matches the backend registration length and SRN constraints", () => {
    const validRegistration = {
      fullName: "Student Example",
      email: "student@kletech.ac.in",
      srn: "02FE24BCS141",
      password: "a-secure-password",
      confirmPassword: "a-secure-password",
      agreeToTerms: true,
    };
    expect(registerSchema.safeParse(validRegistration).success).toBe(true);
    expect(registerSchema.safeParse({
      ...validRegistration,
      email: "student@KLETECH.AC.IN",
    }).success).toBe(true);
    expect(registerSchema.safeParse({
      ...validRegistration,
      email: "student@gmail.com",
    }).success).toBe(false);
    expect(registerSchema.safeParse({
      ...validRegistration,
      email: "student@kletech.ac.in.example.com",
    }).success).toBe(false);
    expect(registerSchema.safeParse({ ...validRegistration, srn: "invalid" }).success).toBe(false);
    expect(registerSchema.safeParse({ ...validRegistration, password: "short" }).success).toBe(false);
    expect(registerSchema.safeParse({ ...validRegistration, confirmPassword: "different" }).success).toBe(false);
  });
});
