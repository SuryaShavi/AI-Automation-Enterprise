import { createBrowserRouter } from "react-router";
import LoginPage from "./pages/LoginPage";
import SignupPage from "./pages/SignupPage";
import DashboardLayout from "./components/layout/DashboardLayout";
import Dashboard from "./pages/Dashboard";
import AIAssistant from "./pages/AIAssistant";
import EmailAutomation from "./pages/EmailAutomation";
import Tasks from "./pages/Tasks";
import Documents from "./pages/Documents";
import Reports from "./pages/Reports";
import Notifications from "./pages/Notifications";
import WorkflowAutomation from "./pages/WorkflowAutomation";
import Integrations from "./pages/Integrations";
import Settings from "./pages/Settings";

export const router = createBrowserRouter([
  {
    path: "/login",
    Component: LoginPage,
  },
  {
    path: "/signup",
    Component: SignupPage,
  },
  {
    path: "/",
    Component: DashboardLayout,
    children: [
      { index: true, Component: Dashboard },
      { path: "ai-assistant", Component: AIAssistant },
      { path: "email-automation", Component: EmailAutomation },
      { path: "tasks", Component: Tasks },
      { path: "documents", Component: Documents },
      { path: "reports", Component: Reports },
      { path: "notifications", Component: Notifications },
      { path: "workflow-automation", Component: WorkflowAutomation },
      { path: "integrations", Component: Integrations },
      { path: "settings", Component: Settings },
    ],
  },
]);
