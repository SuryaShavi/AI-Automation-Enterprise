import { Mail, CheckCircle, Clock, AlertCircle, Eye } from "lucide-react";

export default function EmailAutomation() {
  const summaryCards = [
    { label: "Emails Processed", value: "1,247", icon: Mail, color: "bg-blue-500" },
    { label: "Tasks Detected", value: "89", icon: CheckCircle, color: "bg-green-500" },
    { label: "Pending Review", value: "23", icon: Clock, color: "bg-orange-500" },
  ];

  const emails = [
    {
      sender: "Sarah Johnson",
      email: "sarah.j@client.com",
      subject: "Q1 Budget Review Required",
      summary: "Sarah is requesting a review of the Q1 budget allocation. She mentions concerns about the marketing spend and suggests a meeting to discuss.",
      tasks: ["Review Q1 budget", "Schedule meeting with Sarah"],
      status: "pending",
      time: "2 hours ago",
      priority: "high",
    },
    {
      sender: "Mike Chen",
      email: "mike.chen@company.com",
      subject: "Project Deadline Update",
      summary: "Mike has updated the project timeline. The deliverable date has been moved to next Friday. Team needs to be notified.",
      tasks: ["Update project timeline", "Notify team members"],
      status: "completed",
      time: "5 hours ago",
      priority: "medium",
    },
    {
      sender: "HR Department",
      email: "hr@company.com",
      subject: "New Policy Documents",
      summary: "HR has shared updated policy documents for employee review. All staff must acknowledge receipt by end of week.",
      tasks: ["Review policy documents", "Submit acknowledgment"],
      status: "pending",
      time: "1 day ago",
      priority: "low",
    },
    {
      sender: "Lisa Wang",
      email: "lisa.wang@vendor.com",
      subject: "Invoice #2024-156",
      summary: "Invoice for March services totaling $5,240. Payment due within 30 days. Includes cloud hosting and support fees.",
      tasks: ["Process invoice payment"],
      status: "pending",
      time: "2 days ago",
      priority: "medium",
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Email Automation</h1>
          <p className="text-gray-600 mt-1">AI-powered email processing and task detection</p>
        </div>
        <button className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors">
          Configure Settings
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {summaryCards.map((card) => {
          const Icon = card.icon;
          return (
            <div key={card.label} className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-gray-600 text-sm mb-1">{card.label}</p>
                  <p className="text-3xl font-bold text-gray-800">{card.value}</p>
                </div>
                <div className={`${card.color} p-3 rounded-lg`}>
                  <Icon size={24} className="text-white" />
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-100">
        <div className="p-6 border-b border-gray-200">
          <h2 className="text-xl font-bold text-gray-800">Email List</h2>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                  Sender
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                  Subject
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                  AI Summary
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                  Detected Tasks
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {emails.map((email, index) => (
                <tr key={index} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-4">
                    <div>
                      <p className="font-medium text-gray-800">{email.sender}</p>
                      <p className="text-sm text-gray-500">{email.email}</p>
                      <p className="text-xs text-gray-400 mt-1">{email.time}</p>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-start gap-2">
                      {email.priority === "high" && (
                        <AlertCircle size={16} className="text-red-500 flex-shrink-0 mt-0.5" />
                      )}
                      <p className="font-medium text-gray-800">{email.subject}</p>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <p className="text-sm text-gray-600 line-clamp-2 max-w-md">{email.summary}</p>
                  </td>
                  <td className="px-6 py-4">
                    <div className="space-y-1">
                      {email.tasks.map((task, taskIndex) => (
                        <div key={taskIndex} className="flex items-center gap-2">
                          <CheckCircle size={14} className="text-green-500" />
                          <span className="text-sm text-gray-700">{task}</span>
                        </div>
                      ))}
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <span
                      className={`px-3 py-1 rounded-full text-xs font-medium ${
                        email.status === "completed"
                          ? "bg-green-100 text-green-700"
                          : "bg-orange-100 text-orange-700"
                      }`}
                    >
                      {email.status === "completed" ? "Completed" : "Pending"}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <button className="p-2 hover:bg-gray-100 rounded-lg transition-colors">
                      <Eye size={18} className="text-gray-600" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
