import { Plus, Play, Pause, Trash2, Mail, Bot, CheckSquare, Bell } from "lucide-react";

export default function WorkflowAutomation() {
  const workflows = [
    {
      id: 1,
      name: "Email to Task Automation",
      description: "Automatically extract tasks from incoming emails and create task entries",
      status: "active",
      triggers: 247,
      steps: [
        { icon: Mail, label: "Email Received", color: "bg-blue-500" },
        { icon: Bot, label: "AI Task Extraction", color: "bg-indigo-500" },
        { icon: CheckSquare, label: "Task Created", color: "bg-green-500" },
        { icon: Bell, label: "Notification Sent", color: "bg-orange-500" },
      ],
    },
    {
      id: 2,
      name: "Document Analysis Pipeline",
      description: "Process uploaded documents with AI and generate summaries",
      status: "active",
      triggers: 128,
      steps: [
        { icon: Mail, label: "Document Upload", color: "bg-purple-500" },
        { icon: Bot, label: "AI Analysis", color: "bg-indigo-500" },
        { icon: CheckSquare, label: "Summary Generated", color: "bg-green-500" },
        { icon: Bell, label: "Notify User", color: "bg-orange-500" },
      ],
    },
    {
      id: 3,
      name: "Weekly Report Generator",
      description: "Automatically compile and send weekly productivity reports",
      status: "paused",
      triggers: 52,
      steps: [
        { icon: Mail, label: "Scheduled Trigger", color: "bg-blue-500" },
        { icon: Bot, label: "Data Collection", color: "bg-indigo-500" },
        { icon: CheckSquare, label: "Report Generation", color: "bg-green-500" },
        { icon: Bell, label: "Email Report", color: "bg-orange-500" },
      ],
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Workflow Automation</h1>
          <p className="text-gray-600 mt-1">Create and manage automated workflows</p>
        </div>
        <button className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2">
          <Plus size={18} />
          Create Workflow
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-gray-600 text-sm mb-1">Active Workflows</p>
              <p className="text-3xl font-bold text-gray-800">
                {workflows.filter((w) => w.status === "active").length}
              </p>
            </div>
            <div className="bg-green-500 p-3 rounded-lg">
              <Play size={24} className="text-white" />
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-gray-600 text-sm mb-1">Total Executions</p>
              <p className="text-3xl font-bold text-gray-800">
                {workflows.reduce((acc, w) => acc + w.triggers, 0)}
              </p>
            </div>
            <div className="bg-indigo-500 p-3 rounded-lg">
              <CheckSquare size={24} className="text-white" />
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-gray-600 text-sm mb-1">Success Rate</p>
              <p className="text-3xl font-bold text-gray-800">98.5%</p>
            </div>
            <div className="bg-blue-500 p-3 rounded-lg">
              <Bot size={24} className="text-white" />
            </div>
          </div>
        </div>
      </div>

      <div className="space-y-6">
        {workflows.map((workflow) => (
          <div key={workflow.id} className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
            <div className="flex items-start justify-between mb-6">
              <div>
                <div className="flex items-center gap-3 mb-2">
                  <h3 className="text-xl font-bold text-gray-800">{workflow.name}</h3>
                  <span
                    className={`px-3 py-1 rounded-full text-xs font-medium ${
                      workflow.status === "active"
                        ? "bg-green-100 text-green-700"
                        : "bg-orange-100 text-orange-700"
                    }`}
                  >
                    {workflow.status === "active" ? "Active" : "Paused"}
                  </span>
                </div>
                <p className="text-gray-600">{workflow.description}</p>
                <p className="text-sm text-gray-500 mt-2">Executed {workflow.triggers} times</p>
              </div>

              <div className="flex items-center gap-2">
                <button
                  className={`p-2 rounded-lg transition-colors ${
                    workflow.status === "active"
                      ? "bg-orange-100 hover:bg-orange-200"
                      : "bg-green-100 hover:bg-green-200"
                  }`}
                >
                  {workflow.status === "active" ? (
                    <Pause size={18} className="text-orange-700" />
                  ) : (
                    <Play size={18} className="text-green-700" />
                  )}
                </button>
                <button className="p-2 bg-red-100 hover:bg-red-200 rounded-lg transition-colors">
                  <Trash2 size={18} className="text-red-700" />
                </button>
              </div>
            </div>

            <div className="relative">
              <div className="flex items-center justify-between gap-4">
                {workflow.steps.map((step, index) => {
                  const Icon = step.icon;
                  return (
                    <div key={index} className="flex-1">
                      <div className="flex flex-col items-center">
                        <div className={`${step.color} p-4 rounded-xl mb-3`}>
                          <Icon size={24} className="text-white" />
                        </div>
                        <p className="text-sm font-medium text-gray-700 text-center">{step.label}</p>
                      </div>
                      {index < workflow.steps.length - 1 && (
                        <div className="absolute top-8 h-0.5 bg-gray-300" style={{
                          left: `${(index + 1) * (100 / workflow.steps.length)}%`,
                          width: `${100 / workflow.steps.length}%`
                        }}></div>
                      )}
                    </div>
                  );
                })}
              </div>
              <div className="absolute top-8 left-0 right-0 h-0.5 bg-gray-300 -z-10"></div>
            </div>
          </div>
        ))}
      </div>

      <div className="bg-gradient-to-br from-indigo-50 to-purple-50 rounded-xl p-6 border border-indigo-200">
        <div className="flex items-start gap-4">
          <Bot className="text-indigo-600 flex-shrink-0" size={24} />
          <div>
            <h3 className="font-semibold text-gray-800 mb-2">Create Custom Workflow</h3>
            <p className="text-sm text-gray-600 mb-4">
              Build your own automation workflows with our visual workflow builder. Connect triggers, actions, and conditions to automate your business processes.
            </p>
            <button className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors text-sm flex items-center gap-2">
              <Plus size={16} />
              Open Workflow Builder
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
