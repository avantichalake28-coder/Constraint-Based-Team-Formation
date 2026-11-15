import { z } from "zod";
import { allowedEmailDomain, isAllowedCollegeEmail } from "../../../config/emailDomain";

export const loginSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, "Enter your email address.")
    .email("Enter a valid email address.")
    .max(150, "Email must be 150 characters or fewer."),
  password: z.string().min(1, "Enter your password."),
  rememberMe: z.boolean(),
});

export type LoginFormValues = z.infer<typeof loginSchema>;

export const registerSchema = z
  .object({
    fullName: z
      .string()
      .trim()
      .min(1, "Enter your full name.")
      .max(150, "Name must be 150 characters or fewer."),
    email: z
      .string()
      .trim()
      .min(1, "Enter your email address.")
      .email("Enter a valid email address.")
      .max(150, "Email must be 150 characters or fewer.")
      .refine(isAllowedCollegeEmail, {
        message: `Use an @${allowedEmailDomain} email address.`,
      }),
    srn: z
      .string()
      .trim()
      .toUpperCase()
      .regex(
        /^\d{2}FE\d{2}[A-Z]{3}\d{3}$/,
        "Use the format 02FE24BCS141.",
      )
      .max(50, "SRN must be 50 characters or fewer."),
    password: z
      .string()
      .min(10, "Password must be at least 10 characters.")
      .max(72, "Password must be 72 characters or fewer."),
    confirmPassword: z.string().min(1, "Confirm your password."),
    agreeToTerms: z.boolean().refine(Boolean, "Please agree to the terms."),
  })
  .refine((values) => values.password === values.confirmPassword, {
    message: "Passwords do not match.",
    path: ["confirmPassword"],
  });

export type RegisterFormValues = z.infer<typeof registerSchema>;
