import { Search, Bell, User, ChevronDown, Moon, Sun, LogOut } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router";
import { useSession } from "../../auth/session";

export default function Navbar() {
  const [darkMode, setDarkMode] = useState(false);
  const navigate = useNavigate();
  const { user, logout } = useSession();

  const displayName = user ? `${user.firstName} ${user.lastName}` : 'Unknown User';
  const roleName = user?.roles[0] ?? 'EMPLOYEE';
  const organization = user?.preferences.organization ?? 'AI Enterprise Platform';

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="h-16 bg-white border-b border-gray-200 fixed top-0 right-0 left-64 z-10 flex items-center justify-between px-6">
      <div className="flex items-center gap-4 flex-1">
        <div className="relative flex-1 max-w-xl">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={20} />
          <input
            type="text"
            placeholder="Search or type AI command..."
            className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>
      </div>

      <div className="flex items-center gap-4">
        <button
          onClick={() => setDarkMode(!darkMode)}
          className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
        >
          {darkMode ? <Sun size={20} className="text-gray-600" /> : <Moon size={20} className="text-gray-600" />}
        </button>

        <button
          onClick={() => navigate('/notifications')}
          className="p-2 hover:bg-gray-100 rounded-lg transition-colors relative"
        >
          <Bell size={20} className="text-gray-600" />
          <span className="absolute top-1 right-1 w-2 h-2 bg-red-500 rounded-full"></span>
        </button>

        <div className="flex items-center gap-2 px-3 py-2 hover:bg-gray-100 rounded-lg cursor-pointer transition-colors">
          <div className="text-right">
            <p className="text-sm font-medium text-gray-700">{organization}</p>
            <p className="text-xs text-gray-500">Organization</p>
          </div>
          <ChevronDown size={16} className="text-gray-600" />
        </div>

        <div className="flex items-center gap-3 pl-4 border-l border-gray-200">
          <div className="text-right">
            <p className="text-sm font-medium text-gray-700">{displayName}</p>
            <p className="text-xs text-gray-500">{roleName}</p>
          </div>
          <div className="w-10 h-10 bg-indigo-600 rounded-full flex items-center justify-center">
            <User size={20} className="text-white" />
          </div>
          <button
            onClick={() => void handleLogout()}
            className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
            title="Sign out"
          >
            <LogOut size={18} className="text-gray-600" />
          </button>
        </div>
      </div>
    </div>
  );
}
