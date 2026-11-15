import { motion, useReducedMotion } from "framer-motion";
import { Outlet, useLocation } from "react-router-dom";
import { useEffect } from "react";
import { BrandMark } from "./BrandMark";
import { ThemeControls } from "./ThemeControls";
import { useThemeStore } from "../stores/themeStore";

export function AuthLayout() {
  const location = useLocation();
  const reduceMotion = useReducedMotion();
  const mode = useThemeStore((state) => state.mode);
  useEffect(() => {
      const pageTitle =
      location.pathname === "/register"
        ? "Create an account"
        : location.pathname === "/verify-email"
          ? "Verify your email"
          : "Sign in";
    document.title = `${pageTitle} | SkillMatch`;
  }, [location.pathname]);
  const heroImage =
    mode === "dark"
      ? "/images/auth-aurora-dark.png"
      : "/images/auth-aurora-light.png";

  return (
    <main className="auth-page min-h-screen">
      <section
        aria-label="SkillMatch account"
        className="auth-card"
      >
        <div className="auth-hero" aria-hidden="true">
          <img
            alt=""
            className="auth-hero__image"
            src={heroImage}
          />
        </div>
        <section className="auth-panel">
          <ThemeControls />
          <div className="auth-panel__content">
            <div className="auth-panel__brand">
              <BrandMark />
            </div>
            <motion.div
              animate={{ opacity: 1, y: 0 }}
              initial={reduceMotion ? false : { opacity: 0, y: 10 }}
              key={location.pathname}
              transition={{ duration: reduceMotion ? 0 : 0.26 }}
            >
              <Outlet />
            </motion.div>
          </div>
          <div aria-hidden="true" className="auth-panel__sparkle auth-panel__sparkle--one">✦</div>
          <div aria-hidden="true" className="auth-panel__sparkle auth-panel__sparkle--two">✧</div>
        </section>
      </section>
    </main>
  );
}
