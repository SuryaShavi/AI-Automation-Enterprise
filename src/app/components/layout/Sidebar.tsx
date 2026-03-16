import { Link, useLocation } from "react-router";
import {
  LayoutDashboard,
  Bot,
  Mail,
  CheckSquare,
  FileText,
  BarChart3,
  Bell,
  Workflow,
  Plug,
  Settings,
} from "lucide-react";

const menuItems = [
  { path: "/", label: "Dashboard", icon: LayoutDashboard },
  { path: "/ai-assistant", label: "AI Assistant", icon: Bot },
  { path: "/email-automation", label: "Email Automation", icon: Mail },
  { path: "/tasks", label: "Tasks", icon: CheckSquare },
  { path: "/documents", label: "Documents", icon: FileText },
  { path: "/reports", label: "Reports", icon: BarChart3 },
  { path: "/notifications", label: "Notifications", icon: Bell },
  { path: "/workflow-automation", label: "Workflow Automation", icon: Workflow },
  { path: "/integrations", label: "Integrations", icon: Plug },
  { path: "/settings", label: "Settings", icon: Settings },
];

export default function Sidebar() {
  const location = useLocation();

  return (
    <div className="w-64 bg-[#111827] text-white h-screen fixed left-0 top-0 flex flex-col">
      <div className="p-6 border-b border-gray-700">
        <h1 className="text-xl font-bold text-indigo-400">AI Enterprise</h1>
        <p className="text-xs text-gray-400 mt-1">Automation Platform</p>
      </div>

      <nav className="flex-1 overflow-y-auto py-4">
        {menuItems.map((item) => {
          const Icon = item.icon;
          const isActive = location.pathname === item.path;

          return (
            <Link
              key={item.path}
              to={item.path}
              className={`flex items-center gap-3 px-6 py-3 transition-colors ${
                isActive
                  ? "bg-indigo-600 text-white border-r-4 border-indigo-400"
                  : "text-gray-300 hover:bg-gray-800"
              }`}
            >
              <Icon size={20} />
              <span className="text-sm">{item.label}</span>
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
