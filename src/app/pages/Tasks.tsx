import { useState } from "react";
import { Plus, List, LayoutGrid, Edit, Trash2 } from "lucide-react";

interface Task {
  id: number;
  name: string;
  description: string;
  assignedTo: string;
  priority: "low" | "medium" | "high" | "urgent";
  deadline: string;
  status: "pending" | "in-progress" | "completed";
}

export default function Tasks() {
  const [viewMode, setViewMode] = useState<"table" | "kanban">("table");

  const tasks: Task[] = [
    {
      id: 1,
      name: "Review Q1 Financial Report",
      description: "Analyze quarterly performance and prepare summary",
      assignedTo: "John Doe",
      priority: "high",
      deadline: "2024-03-20",
      status: "in-progress",
    },
    {
      id: 2,
      name: "Update Project Documentation",
      description: "Add new API endpoints to technical docs",
      assignedTo: "Sarah Johnson",
      priority: "medium",
      deadline: "2024-03-22",
      status: "pending",
    },
    {
      id: 3,
      name: "Client Meeting Preparation",
      description: "Prepare presentation slides for stakeholder review",
      assignedTo: "Mike Chen",
      priority: "urgent",
      deadline: "2024-03-18",
      status: "in-progress",
    },
    {
      id: 4,
      name: "Database Backup Verification",
      description: "Verify automated backup systems are functioning",
      assignedTo: "Lisa Wang",
      priority: "low",
      deadline: "2024-03-25",
      status: "completed",
    },
    {
      id: 5,
      name: "Security Audit Report",
      description: "Review and address security audit findings",
      assignedTo: "John Doe",
      priority: "high",
      deadline: "2024-03-19",
      status: "pending",
    },
  ];

  const getPriorityColor = (priority: string) => {
    switch (priority) {
      case "urgent":
        return "bg-red-100 text-red-700";
      case "high":
        return "bg-orange-100 text-orange-700";
      case "medium":
        return "bg-blue-100 text-blue-700";
      case "low":
        return "bg-gray-100 text-gray-700";
      default:
        return "bg-gray-100 text-gray-700";
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "completed":
        return "bg-green-100 text-green-700";
      case "in-progress":
        return "bg-blue-100 text-blue-700";
      case "pending":
        return "bg-orange-100 text-orange-700";
      default:
        return "bg-gray-100 text-gray-700";
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case "in-progress":
        return "In Progress";
      case "pending":
        return "Pending";
      case "completed":
        return "Completed";
      default:
        return status;
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Task Management</h1>
          <p className="text-gray-600 mt-1">Organize and track your team's tasks</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex bg-white rounded-lg border border-gray-200 p-1">
            <button
              onClick={() => setViewMode("table")}
              className={`px-3 py-2 rounded-md transition-colors ${
                viewMode === "table" ? "bg-indigo-600 text-white" : "text-gray-600 hover:bg-gray-100"
              }`}
            >
              <List size={18} />
            </button>
            <button
              onClick={() => setViewMode("kanban")}
              className={`px-3 py-2 rounded-md transition-colors ${
                viewMode === "kanban" ? "bg-indigo-600 text-white" : "text-gray-600 hover:bg-gray-100"
              }`}
            >
              <LayoutGrid size={18} />
            </button>
          </div>
          <button className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2">
            <Plus size={18} />
            Create Task
          </button>
        </div>
      </div>

      {viewMode === "table" ? (
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                    Task Name
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                    Description
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                    Assigned To
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                    Priority
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                    Deadline
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
                {tasks.map((task) => (
                  <tr key={task.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4">
                      <p className="font-medium text-gray-800">{task.name}</p>
                    </td>
                    <td className="px-6 py-4">
                      <p className="text-sm text-gray-600 max-w-xs">{task.description}</p>
                    </td>
                    <td className="px-6 py-4">
                      <p className="text-sm text-gray-800">{task.assignedTo}</p>
                    </td>
                    <td className="px-6 py-4">
                      <span className={`px-3 py-1 rounded-full text-xs font-medium ${getPriorityColor(task.priority)}`}>
                        {task.priority.charAt(0).toUpperCase() + task.priority.slice(1)}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <p className="text-sm text-gray-600">{task.deadline}</p>
                    </td>
                    <td className="px-6 py-4">
                      <span className={`px-3 py-1 rounded-full text-xs font-medium ${getStatusColor(task.status)}`}>
                        {getStatusLabel(task.status)}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2">
                        <button className="p-2 hover:bg-gray-100 rounded-lg transition-colors">
                          <Edit size={16} className="text-gray-600" />
                        </button>
                        <button className="p-2 hover:bg-red-50 rounded-lg transition-colors">
                          <Trash2 size={16} className="text-red-600" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {["pending", "in-progress", "completed"].map((status) => (
            <div key={status} className="bg-white rounded-xl shadow-sm border border-gray-100 p-4">
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-semibold text-gray-800">
                  {status === "in-progress" ? "In Progress" : status.charAt(0).toUpperCase() + status.slice(1)}
                </h3>
                <span className="px-2 py-1 bg-gray-100 text-gray-600 rounded-full text-xs">
                  {tasks.filter((t) => t.status === status).length}
                </span>
              </div>
              <div className="space-y-3">
                {tasks
                  .filter((task) => task.status === status)
                  .map((task) => (
                    <div key={task.id} className="bg-gray-50 rounded-lg p-4 border border-gray-200 hover:border-indigo-300 transition-colors cursor-pointer">
                      <h4 className="font-medium text-gray-800 mb-2">{task.name}</h4>
                      <p className="text-sm text-gray-600 mb-3">{task.description}</p>
                      <div className="flex items-center justify-between">
                        <span className={`px-2 py-1 rounded-full text-xs font-medium ${getPriorityColor(task.priority)}`}>
                          {task.priority.charAt(0).toUpperCase() + task.priority.slice(1)}
                        </span>
                        <p className="text-xs text-gray-500">{task.deadline}</p>
                      </div>
                      <div className="mt-3 pt-3 border-t border-gray-200">
                        <p className="text-xs text-gray-600">{task.assignedTo}</p>
                      </div>
                    </div>
                  ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
