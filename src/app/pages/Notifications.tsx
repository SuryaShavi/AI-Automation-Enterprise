import { CheckCircle, FileText, Upload, Bot, Bell, Trash2 } from "lucide-react";

export default function Notifications() {
  const notifications = [
    {
      id: 1,
      type: "task",
      icon: CheckCircle,
      color: "text-green-500",
      bgColor: "bg-green-50",
      title: "New task created",
      description: "Task 'Review Q1 Report' has been assigned to you",
      timestamp: "2 minutes ago",
      status: "unread",
    },
    {
      id: 2,
      type: "report",
      icon: FileText,
      color: "text-blue-500",
      bgColor: "bg-blue-50",
      title: "Report generated",
      description: "Your weekly productivity report is ready to view",
      timestamp: "15 minutes ago",
      status: "unread",
    },
    {
      id: 3,
      type: "document",
      icon: Upload,
      color: "text-purple-500",
      bgColor: "bg-purple-50",
      title: "Document uploaded",
      description: "New document 'Annual_Report_2024.pdf' has been uploaded",
      timestamp: "1 hour ago",
      status: "read",
    },
    {
      id: 4,
      type: "ai",
      icon: Bot,
      color: "text-indigo-500",
      bgColor: "bg-indigo-50",
      title: "AI task extracted",
      description: "AI detected 3 new tasks from your emails",
      timestamp: "2 hours ago",
      status: "read",
    },
    {
      id: 5,
      type: "task",
      icon: CheckCircle,
      color: "text-green-500",
      bgColor: "bg-green-50",
      title: "Task completed",
      description: "Sarah Johnson marked 'Update Documentation' as completed",
      timestamp: "3 hours ago",
      status: "read",
    },
    {
      id: 6,
      type: "report",
      icon: FileText,
      color: "text-blue-500",
      bgColor: "bg-blue-50",
      title: "Report scheduled",
      description: "Monthly analytics report scheduled for generation",
      timestamp: "5 hours ago",
      status: "read",
    },
    {
      id: 7,
      type: "ai",
      icon: Bot,
      color: "text-indigo-500",
      bgColor: "bg-indigo-50",
      title: "AI insight",
      description: "AI suggests reviewing high-priority emails from this morning",
      timestamp: "1 day ago",
      status: "read",
    },
    {
      id: 8,
      type: "document",
      icon: Upload,
      color: "text-purple-500",
      bgColor: "bg-purple-50",
      title: "Document analysis complete",
      description: "AI finished analyzing 'Financial_Projections.xlsx'",
      timestamp: "1 day ago",
      status: "read",
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Notifications</h1>
          <p className="text-gray-600 mt-1">Stay updated with all system activities</p>
        </div>
        <div className="flex items-center gap-3">
          <button className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors">
            Mark all as read
          </button>
          <button className="px-4 py-2 bg-red-100 text-red-700 rounded-lg hover:bg-red-200 transition-colors flex items-center gap-2">
            <Trash2 size={18} />
            Clear all
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {[
          { label: "All", count: notifications.length, active: true },
          { label: "Unread", count: notifications.filter((n) => n.status === "unread").length, active: false },
          { label: "Tasks", count: notifications.filter((n) => n.type === "task").length, active: false },
          { label: "AI Alerts", count: notifications.filter((n) => n.type === "ai").length, active: false },
        ].map((filter, index) => (
          <button
            key={index}
            className={`p-4 rounded-lg border-2 transition-all ${
              filter.active
                ? "border-indigo-500 bg-indigo-50"
                : "border-gray-200 bg-white hover:border-gray-300"
            }`}
          >
            <p className="text-2xl font-bold text-gray-800">{filter.count}</p>
            <p className="text-sm text-gray-600">{filter.label}</p>
          </button>
        ))}
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-100">
        <div className="p-6 border-b border-gray-200">
          <h2 className="text-xl font-bold text-gray-800">Recent Notifications</h2>
        </div>

        <div className="divide-y divide-gray-200">
          {notifications.map((notification) => {
            const Icon = notification.icon;
            return (
              <div
                key={notification.id}
                className={`p-6 transition-colors ${
                  notification.status === "unread" ? "bg-blue-50/50" : "bg-white hover:bg-gray-50"
                }`}
              >
                <div className="flex items-start gap-4">
                  <div className={`${notification.bgColor} p-3 rounded-lg`}>
                    <Icon size={24} className={notification.color} />
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <div className="flex items-center gap-2">
                          <h3 className="font-semibold text-gray-800">{notification.title}</h3>
                          {notification.status === "unread" && (
                            <span className="w-2 h-2 bg-blue-500 rounded-full"></span>
                          )}
                        </div>
                        <p className="text-sm text-gray-600 mt-1">{notification.description}</p>
                        <p className="text-xs text-gray-500 mt-2">{notification.timestamp}</p>
                      </div>

                      <button className="p-2 hover:bg-gray-100 rounded-lg transition-colors flex-shrink-0">
                        <Trash2 size={16} className="text-gray-400" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      <div className="bg-gradient-to-br from-indigo-50 to-purple-50 rounded-xl p-6 border border-indigo-200">
        <div className="flex items-start gap-4">
          <Bell className="text-indigo-600" size={24} />
          <div>
            <h3 className="font-semibold text-gray-800 mb-2">Notification Settings</h3>
            <p className="text-sm text-gray-600 mb-4">
              Customize which notifications you receive and how you want to be alerted.
            </p>
            <button className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors text-sm">
              Configure Settings
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
