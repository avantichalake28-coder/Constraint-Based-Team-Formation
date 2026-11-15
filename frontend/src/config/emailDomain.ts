const configuredDomain = import.meta.env.VITE_ALLOWED_EMAIL_DOMAIN?.trim();

export const allowedEmailDomain = (configuredDomain || "kletech.ac.in")
  .replace(/^@/, "")
  .toLowerCase();

export function isAllowedCollegeEmail(email: string): boolean {
  const atIndex = email.lastIndexOf("@");
  return atIndex > 0 && email.slice(atIndex + 1).toLowerCase() === allowedEmailDomain;
}
