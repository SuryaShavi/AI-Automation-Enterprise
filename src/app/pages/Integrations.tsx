import { Mail, MessageSquare, Cloud, Code, CheckCircle, XCircle } from "lucide-react";

export default function Integrations() {
  const integrations = [
    {
      name: "Gmail",
      description: "Connect your Gmail account to automatically process emails and extract tasks",
      icon: Mail,
      color: "bg-red-500",
      status: "connected",
      connectedDate: "Connected on Mar 10, 2024",
    },
    {
      name: "Outlook",
      description: "Integrate with Microsoft Outlook for email automation and calendar sync",
      icon: Mail,
      color: "bg-blue-500",
      status: "disconnected",
      connectedDate: null,
    },
    {
      name: "Slack",
      description: "Send notifications and updates directly to your Slack workspace",
      icon: MessageSquare,
      color: "bg-purple-500",
      status: "connected",
      connectedDate: "Connected on Mar 12, 2024",
    },
    {
      name: "Google Drive",
      description: "Automatically sync and analyze documents from your Google Drive",
      icon: Cloud,
      color: "bg-green-500",
      status: "connected",
      connectedDate: "Connected on Mar 8, 2024",
    },
    {
      name: "Dropbox",
      description: "Connect Dropbox for document storage and automated processing",
      icon: Cloud,
      color: "bg-blue-600",
      status: "disconnected",
      connectedDate: null,
    },
    {
      name: "Custom API",
      description: "Integrate with custom APIs using webhooks and REST endpoints",
      icon: Code,
      color: "bg-gray-700",
      status: "disconnected",
      connectedDate: null,
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Integrations</h1>
          <p className="text-gray-600 mt-1">Connect your favorite tools and services</p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-gray-600 text-sm mb-1">Connected Services</p>
              <p className="text-3xl font-bold text-gray-800">
                {integrations.filter((i) => i.status === "connected").length}
              </p>
            </div>
            <div className="bg-green-500 p-3 rounded-lg">
              <CheckCircle size={24} className="text-white" />
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-gray-600 text-sm mb-1">Available Integrations</p>
              <p className="text-3xl font-bold text-gray-800">{integrations.length}</p>
            </div>
            <div className="bg-indigo-500 p-3 rounded-lg">
              <Code size={24} className="text-white" />
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {integrations.map((integration, index) => {
          const Icon = integration.icon;
          return (
            <div
              key={index}
              className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 hover:shadow-md transition-shadow"
            >
              <div className="flex items-start justify-between mb-4">
                <div className={`${integration.color} p-3 rounded-lg`}>
                  <Icon size={24} className="text-white" />
                </div>
                <div className="flex items-center gap-2">
                  {integration.status === "connected" ? (
                    <CheckCircle size={20} className="text-green-500" />
                  ) : (
                    <XCircle size={20} className="text-gray-400" />
                  )}
                  <span
                    className={`text-xs font-medium ${
                      integration.status === "connected" ? "text-green-700" : "text-gray-500"
                    }`}
                  >
                    {integration.status === "connected" ? "Connected" : "Not Connected"}
                  </span>
                </div>
              </div>

              <h3 className="text-lg font-bold text-gray-800 mb-2">{integration.name}</h3>
              <p className="text-sm text-gray-600 mb-4">{integration.description}</p>

              {integration.connectedDate && (
                <p className="text-xs text-gray-500 mb-4">{integration.connectedDate}</p>
              )}

              <button
                className={`w-full py-2 rounded-lg font-medium transition-colors ${
                  integration.status === "connected"
                    ? "bg-red-100 text-red-700 hover:bg-red-200"
                    : "bg-indigo-600 text-white hover:bg-indigo-700"
                }`}
              >
                {integration.status === "connected" ? "Disconnect" : "Connect"}
              </button>
            </div>
          );
        })}
      </div>

      <div className="bg-gradient-to-br from-indigo-50 to-purple-50 rounded-xl p-6 border border-indigo-200">
        <div className="flex items-start gap-4">
          <Code className="text-indigo-600 flex-shrink-0" size={24} />
          <div>
            <h3 className="font-semibold text-gray-800 mb-2">Need a Custom Integration?</h3>
            <p className="text-sm text-gray-600 mb-4">
              Our API allows you to build custom integrations tailored to your specific needs. Access comprehensive documentation and code examples to get started.
            </p>
            <div className="flex gap-3">
              <button className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors text-sm">
                View API Documentation
              </button>
              <button className="px-4 py-2 bg-white text-indigo-600 border border-indigo-300 rounded-lg hover:bg-indigo-50 transition-colors text-sm">
                Contact Support
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
