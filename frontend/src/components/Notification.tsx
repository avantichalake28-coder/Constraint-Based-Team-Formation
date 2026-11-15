import type { ReactNode } from "react";

interface NotificationProps {
  children: ReactNode;
  kind: "error" | "success" | "info";
}

export function Notification({ children, kind }: NotificationProps) {
  return (
    <div
      className={`notification notification--${kind}`}
      role={kind === "error" ? "alert" : "status"}
    >
      {children}
    </div>
  );
}
