import { CheckSquare, FileText, Bot, Clock, CheckCircle, Upload, MessageSquare, TrendingUp, AlertCircle } from "lucide-react";

export default function Dashboard() {
  const metrics = [
    { label: "Total Tasks", value: "248", icon: CheckSquare, color: "bg-blue-500" },
    { label: "Pending Tasks", value: "42", icon: Clock, color: "bg-orange-500" },
    { label: "Completed Tasks", value: "206", icon: CheckCircle, color: "bg-green-500" },
    { label: "Documents Uploaded", value: "128", icon: Upload, color: "bg-purple-500" },
    { label: "AI Requests Today", value: "67", icon: Bot, color: "bg-indigo-500" },
  ];

  const activities = [
    { type: "Task created", description: "New task assigned: Review Q1 report", time: "2 min ago", icon: CheckSquare, color: "text-blue-500" },
    { type: "Document uploaded", description: "Annual_Report_2024.pdf uploaded", time: "15 min ago", icon: FileText, color: "text-purple-500" },
    { type: "Report generated", description: "Weekly productivity report is ready", time: "1 hour ago", icon: TrendingUp, color: "text-green-500" },
    { type: "AI chat request", description: "AI summarized 12 emails", time: "2 hours ago", icon: MessageSquare, color: "text-indigo-500" },
  ];

  const notifications = [
    { message: "5 new tasks detected from emails", time: "10 min ago", priority: "high" },
    { message: "Document analysis completed", time: "30 min ago", priority: "medium" },
    { message: "Weekly report scheduled for generation", time: "1 hour ago", priority: "low" },
  ];

  return (
    <div className="space-y-6">
      <div className="bg-gradient-to-r from-indigo-600 to-purple-600 rounded-2xl p-8 text-white">
        <h1 className="text-3xl font-bold mb-2">Welcome back, John! 👋</h1>
        <p className="text-indigo-100">Here's what's happening with your automation platform today.</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-6">
        {metrics.map((metric) => {
          const Icon = metric.icon;
          return (
            <div key={metric.label} className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-gray-600 text-sm mb-1">{metric.label}</p>
                  <p className="text-3xl font-bold text-gray-800">{metric.value}</p>
                </div>
                <div className={`${metric.color} p-3 rounded-lg`}>
                  <Icon size={24} className="text-white" />
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <h2 className="text-xl font-bold text-gray-800 mb-4">Activity Feed</h2>
          <div className="space-y-4">
            {activities.map((activity, index) => {
              const Icon = activity.icon;
              return (
                <div key={index} className="flex items-start gap-4 p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
                  <Icon size={20} className={activity.color} />
                  <div className="flex-1">
                    <p className="font-medium text-gray-800">{activity.type}</p>
                    <p className="text-sm text-gray-600">{activity.description}</p>
                  </div>
                  <span className="text-xs text-gray-500">{activity.time}</span>
                </div>
              );
            })}
          </div>
        </div>

        <div className="space-y-6">
          <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
            <h2 className="text-xl font-bold text-gray-800 mb-4">AI Insights</h2>
            <div className="bg-gradient-to-br from-indigo-50 to-purple-50 rounded-lg p-4 border border-indigo-200">
              <div className="flex items-start gap-3">
                <Bot className="text-indigo-600 flex-shrink-0" size={24} />
                <div>
                  <p className="font-medium text-gray-800 mb-2">AI detected 5 new tasks from emails today.</p>
                  <p className="text-sm text-gray-600">3 high priority items require your attention.</p>
                </div>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
            <h2 className="text-xl font-bold text-gray-800 mb-4">Recent Notifications</h2>
            <div className="space-y-3">
              {notifications.map((notification, index) => (
                <div key={index} className="flex items-start gap-3 p-3 bg-gray-50 rounded-lg">
                  <AlertCircle
                    size={16}
                    className={
                      notification.priority === "high" ? "text-red-500" :
                      notification.priority === "medium" ? "text-orange-500" : "text-blue-500"
                    }
                  />
                  <div className="flex-1">
                    <p className="text-sm text-gray-800">{notification.message}</p>
                    <p className="text-xs text-gray-500 mt-1">{notification.time}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
            <h2 className="text-xl font-bold text-gray-800 mb-2">System Health</h2>
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 bg-green-500 rounded-full animate-pulse"></div>
              <span className="text-sm text-gray-600">All systems operational</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
