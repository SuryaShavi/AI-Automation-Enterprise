import { Download, FileText, TrendingUp } from "lucide-react";
import { BarChart, Bar, LineChart, Line, PieChart, Pie, Cell, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts";

export default function Reports() {
  const weeklyProductivity = [
    { day: "Mon", tasks: 12, completed: 10 },
    { day: "Tue", tasks: 15, completed: 13 },
    { day: "Wed", tasks: 18, completed: 16 },
    { day: "Thu", tasks: 14, completed: 12 },
    { day: "Fri", tasks: 16, completed: 15 },
  ];

  const taskCompletionRate = [
    { month: "Jan", rate: 85 },
    { month: "Feb", rate: 88 },
    { month: "Mar", rate: 92 },
    { month: "Apr", rate: 90 },
    { month: "May", rate: 94 },
    { month: "Jun", rate: 96 },
  ];

  const aiUsageData = [
    { name: "Email Processing", value: 35 },
    { name: "Document Analysis", value: 25 },
    { name: "Task Extraction", value: 20 },
    { name: "Report Generation", value: 15 },
    { name: "Chat Queries", value: 5 },
  ];

  const COLORS = ["#6366F1", "#22C55E", "#F59E0B", "#3B82F6", "#EF4444"];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Reports & Analytics</h1>
          <p className="text-gray-600 mt-1">Track performance and generate insights</p>
        </div>
        <button className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2">
          <FileText size={18} />
          Generate Report
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-bold text-gray-800">Weekly Productivity</h2>
            <button className="p-2 hover:bg-gray-100 rounded-lg transition-colors">
              <Download size={18} className="text-gray-600" />
            </button>
          </div>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={weeklyProductivity}>
              <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
              <XAxis dataKey="day" stroke="#6B7280" />
              <YAxis stroke="#6B7280" />
              <Tooltip
                contentStyle={{
                  backgroundColor: "#FFF",
                  border: "1px solid #E5E7EB",
                  borderRadius: "8px",
                }}
              />
              <Legend />
              <Bar dataKey="tasks" fill="#6366F1" name="Total Tasks" radius={[8, 8, 0, 0]} />
              <Bar dataKey="completed" fill="#22C55E" name="Completed" radius={[8, 8, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-bold text-gray-800">Task Completion Rate</h2>
            <button className="p-2 hover:bg-gray-100 rounded-lg transition-colors">
              <Download size={18} className="text-gray-600" />
            </button>
          </div>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={taskCompletionRate}>
              <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
              <XAxis dataKey="month" stroke="#6B7280" />
              <YAxis stroke="#6B7280" />
              <Tooltip
                contentStyle={{
                  backgroundColor: "#FFF",
                  border: "1px solid #E5E7EB",
                  borderRadius: "8px",
                }}
              />
              <Legend />
              <Line
                type="monotone"
                dataKey="rate"
                stroke="#6366F1"
                strokeWidth={3}
                name="Completion Rate (%)"
                dot={{ fill: "#6366F1", r: 6 }}
                activeDot={{ r: 8 }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-bold text-gray-800">AI Usage Analytics</h2>
            <button className="p-2 hover:bg-gray-100 rounded-lg transition-colors">
              <Download size={18} className="text-gray-600" />
            </button>
          </div>
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={aiUsageData}
                cx="50%"
                cy="50%"
                labelLine={false}
                label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(0)}%`}
                outerRadius={100}
                fill="#8884d8"
                dataKey="value"
              >
                {aiUsageData.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <h2 className="text-xl font-bold text-gray-800 mb-6">Team Activity</h2>
          <div className="space-y-4">
            {[
              { name: "John Doe", tasks: 45, completion: 95, color: "bg-green-500" },
              { name: "Sarah Johnson", tasks: 38, completion: 92, color: "bg-blue-500" },
              { name: "Mike Chen", tasks: 42, completion: 88, color: "bg-purple-500" },
              { name: "Lisa Wang", tasks: 36, completion: 94, color: "bg-orange-500" },
            ].map((member, index) => (
              <div key={index} className="p-4 bg-gray-50 rounded-lg">
                <div className="flex items-center justify-between mb-2">
                  <p className="font-medium text-gray-800">{member.name}</p>
                  <span className="text-sm text-gray-600">{member.tasks} tasks</span>
                </div>
                <div className="flex items-center gap-3">
                  <div className="flex-1 bg-gray-200 rounded-full h-2">
                    <div
                      className={`${member.color} h-2 rounded-full transition-all`}
                      style={{ width: `${member.completion}%` }}
                    ></div>
                  </div>
                  <span className="text-sm font-medium text-gray-700">{member.completion}%</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
        <div className="flex items-center gap-3 mb-4">
          <TrendingUp className="text-green-500" size={24} />
          <h2 className="text-xl font-bold text-gray-800">Report Preview</h2>
        </div>
        <div className="bg-gray-50 rounded-lg p-6 border border-gray-200">
          <h3 className="font-semibold text-gray-800 mb-3">Monthly Performance Summary</h3>
          <div className="space-y-2 text-sm text-gray-700">
            <p>• Total tasks completed: 206 (↑ 15% from last month)</p>
            <p>• Average completion rate: 94% (↑ 3% from last month)</p>
            <p>• AI automation usage: 67 requests per day</p>
            <p>• Documents processed: 128 files</p>
            <p>• Team productivity increased by 18% overall</p>
          </div>
          <button className="mt-4 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors text-sm">
            View Full Report
          </button>
        </div>
      </div>
    </div>
  );
}
