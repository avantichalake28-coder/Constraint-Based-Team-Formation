import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AppProviders } from "./providers";
import { AdminProtectedRoute, MentorProtectedRoute, ProtectedRoute } from "./router/ProtectedRoute";
import { AuthLayout } from "../components/AuthLayout";
import { LoginPage } from "../features/authentication/pages/LoginPage";
import { RegisterPage } from "../features/authentication/pages/RegisterPage";
import { VerifyEmailPage } from "../features/authentication/pages/VerifyEmailPage";
import { MentorAppLayout } from "../features/mentors/MentorAppLayout";
import {
  MentorDashboardPage,
  MentorNotificationsPage,
  MentorProfilePage,
  MentorProjectsPage,
  MentorSettingsPage,
  MentorStudentsPage,
  MentorTeamsPage,
} from "../features/mentors/pages/MentorPages";
import { StudentAppLayout } from "../features/students/StudentAppLayout";
import {
  DashboardPage,
  MyTeamPage,
  NotificationsPage,
  ProfilePage,
  ProjectPreferencesPage,
  SettingsPage,
  SkillsPage,
  TeamFormationPage,
} from "../features/students/pages/StudentPages";
import { AdminLayout } from "../features/admin/AdminLayout";
import { AdminDashboardPage } from "../features/admin/pages/AdminDashboardPage";
import { AdminStudentManagementPage } from "../features/admin/pages/AdminStudentManagementPage";
import { AdminMentorManagementPage } from "../features/admin/pages/AdminMentorManagementPage";
import { AdminTeamFormationPage } from "../features/admin/pages/AdminTeamFormationPage";
import { AdminAnalyticsPage } from "../features/admin/pages/AdminAnalyticsPage";
import { AdminSettingsPage } from "../features/admin/pages/AdminSettingsPage";
import { AdminUserRoleManagementPage } from "../features/admin/pages/AdminUserRoleManagementPage";

export function App() {
  return (
    <AppProviders>
      <BrowserRouter>
        <Routes>
          <Route element={<AuthLayout />}>
            <Route element={<LoginPage />} path="/login" />
            <Route element={<RegisterPage />} path="/register" />
            <Route element={<VerifyEmailPage />} path="/verify-email" />
          </Route>

          <Route element={<ProtectedRoute />}>
            <Route element={<StudentAppLayout />} path="/app">
              <Route element={<DashboardPage />} index />
              <Route element={<ProfilePage />} path="profile" />
              <Route element={<SkillsPage />} path="skills" />
              <Route element={<ProjectPreferencesPage />} path="preferences" />
              <Route element={<TeamFormationPage />} path="formation" />
              <Route element={<MyTeamPage />} path="team" />
              <Route element={<NotificationsPage />} path="notifications" />
              <Route element={<SettingsPage />} path="settings" />
            </Route>
            <Route element={<Navigate replace to="/app/settings" />} path="/settings" />
          </Route>

          <Route element={<MentorProtectedRoute />}>
            <Route element={<MentorAppLayout />} path="/mentor">
              <Route element={<MentorDashboardPage />} index />
              <Route element={<MentorProfilePage />} path="profile" />
              <Route element={<MentorProjectsPage />} path="projects" />
              <Route element={<MentorStudentsPage />} path="students" />
              <Route element={<MentorTeamsPage />} path="teams" />
              <Route element={<MentorNotificationsPage />} path="notifications" />
              <Route element={<MentorSettingsPage />} path="settings" />
            </Route>
          </Route>

          <Route element={<AdminProtectedRoute />}>
            <Route element={<AdminLayout />} path="/admin">
              <Route element={<AdminDashboardPage />} index />
              <Route element={<AdminStudentManagementPage />} path="students" />
              <Route element={<AdminMentorManagementPage />} path="mentors" />
              <Route element={<AdminTeamFormationPage />} path="team-formation" />
              <Route element={<AdminAnalyticsPage />} path="analytics" />
              <Route element={<AdminSettingsPage />} path="settings" />
              <Route element={<AdminUserRoleManagementPage />} path="user-role-management" />
            </Route>
          </Route>

          <Route element={<Navigate replace to="/login" />} path="*" />
        </Routes>
      </BrowserRouter>
    </AppProviders>
  );
}