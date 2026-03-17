import { useEffect, useMemo, useState } from "react";
import { Mail, CheckCircle, Clock, AlertCircle, Eye, WandSparkles } from "lucide-react";
import { apiClient } from "../../api/client";
import { endpoints } from "../../api/endpoints";
import type { EmailItem, EmailStats, ExtractTaskResponse } from "../../api/contracts";
import { formatRelativeTime } from "../lib/format";

export default function EmailAutomation() {
  const [emails, setEmails] = useState<EmailItem[]>([]);
  const [stats, setStats] = useState<EmailStats | null>(null);
  const [selectedEmail, setSelectedEmail] = useState<EmailItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [extractingId, setExtractingId] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function loadData() {
      try {
        const [emailsEnvelope, statsEnvelope] = await Promise.all([
          apiClient.request<{ items: EmailItem[] }>(endpoints.emails.list),
          apiClient.request<EmailStats>(endpoints.emails.stats),
        ]);

        if (!active) {
          return;
        }

        setEmails(emailsEnvelope.data.items);
        setStats(statsEnvelope.data);
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load emails");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    async function pollEmails() {
      try {
        const envelope = await apiClient.request<{ items: EmailItem[] }>(endpoints.emails.list);
        if (active) {
          setEmails(envelope.data.items);
        }
      } catch {
        // Ignore transient polling failures.
      }
    }

    void loadData();
    const intervalId = window.setInterval(() => {
      void pollEmails();
    }, 60_000);

    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, []);

  async function handleView(emailId: string) {
    try {
      const envelope = await apiClient.request<EmailItem>(endpoints.emails.detail(emailId));
      setSelectedEmail(envelope.data);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Failed to load email details");
    }
  }

  async function handleExtractTasks(emailId: string) {
    setExtractingId(emailId);

    try {
      const envelope = await apiClient.request<ExtractTaskResponse>(endpoints.emails.extractTasks(emailId), {
        method: "POST",
      });

      setEmails((previous) => previous.map((email) => (
        email.id === emailId
          ? { ...email, detectedTasks: envelope.data.tasks.map((task) => task.title) }
          : email
      )));
    } catch (extractError) {
      setError(extractError instanceof Error ? extractError.message : "Failed to extract tasks");
    } finally {
      setExtractingId(null);
    }
  }

  const summaryCards = useMemo(() => [
    { label: "Emails Processed", value: String(stats?.emailsProcessed ?? 0), icon: Mail, color: "bg-blue-500" },
    { label: "Tasks Detected", value: String(stats?.tasksDetected ?? 0), icon: CheckCircle, color: "bg-green-500" },
    { label: "Pending Review", value: String(stats?.pendingReview ?? 0), icon: Clock, color: "bg-orange-500" },
  ], [stats]);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Email Automation</h1>
          <p className="text-gray-600 mt-1">AI-powered email processing and task detection</p>
        </div>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      {loading ? (
        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="w-8 h-8 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin"></div>
          <p className="text-sm text-gray-600 mt-3">Loading email automation data...</p>
        </div>
      ) : (
        <>
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

          <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
            <div className="xl:col-span-2 bg-white rounded-xl shadow-sm border border-gray-100">
              <div className="p-6 border-b border-gray-200">
                <h2 className="text-xl font-bold text-gray-800">Email List</h2>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50 border-b border-gray-200">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Sender</th>
                      <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Subject</th>
                      <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">AI Summary</th>
                      <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Detected Tasks</th>
                      <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Status</th>
                      <th className="px-6 py-3 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {emails.map((email) => (
                      <tr key={email.id} className="hover:bg-gray-50 transition-colors">
                        <td className="px-6 py-4">
                          <div>
                            <p className="font-medium text-gray-800">{email.senderName}</p>
                            <p className="text-sm text-gray-500">{email.senderEmail}</p>
                            <p className="text-xs text-gray-400 mt-1">{formatRelativeTime(email.receivedAt)}</p>
                          </div>
                        </td>
                        <td className="px-6 py-4">
                          <div className="flex items-start gap-2">
                            {email.priority === "HIGH" && <AlertCircle size={16} className="text-red-500 flex-shrink-0 mt-0.5" />}
                            <p className="font-medium text-gray-800">{email.subject}</p>
                          </div>
                        </td>
                        <td className="px-6 py-4">
                          <p className="text-sm text-gray-600 line-clamp-2 max-w-md">{email.aiSummary}</p>
                        </td>
                        <td className="px-6 py-4">
                          <div className="space-y-1">
                            {email.detectedTasks.length === 0 ? (
                              <span className="text-xs text-gray-500">No tasks detected</span>
                            ) : (
                              email.detectedTasks.map((task) => (
                                <div key={`${email.id}-${task}`} className="flex items-center gap-2">
                                  <CheckCircle size={14} className="text-green-500" />
                                  <span className="text-sm text-gray-700">{task}</span>
                                </div>
                              ))
                            )}
                          </div>
                        </td>
                        <td className="px-6 py-4">
                          <span className={`px-3 py-1 rounded-full text-xs font-medium ${email.status === "COMPLETED" ? "bg-green-100 text-green-700" : "bg-orange-100 text-orange-700"}`}>
                            {email.status}
                          </span>
                        </td>
                        <td className="px-6 py-4">
                          <div className="flex gap-2">
                            <button onClick={() => void handleView(email.id)} className="p-2 hover:bg-gray-100 rounded-lg transition-colors" title="View Email">
                              <Eye size={18} className="text-gray-600" />
                            </button>
                            <button
                              onClick={() => void handleExtractTasks(email.id)}
                              disabled={extractingId === email.id}
                              className="p-2 hover:bg-indigo-100 rounded-lg transition-colors"
                              title="Extract Tasks"
                            >
                              <WandSparkles size={18} className="text-indigo-600" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
              <h2 className="text-xl font-bold text-gray-800 mb-4">Email Detail</h2>
              {!selectedEmail ? (
                <p className="text-sm text-gray-500">Select an email to view details.</p>
              ) : (
                <div className="space-y-3 text-sm text-gray-700">
                  <p><span className="font-semibold">From:</span> {selectedEmail.senderName} ({selectedEmail.senderEmail})</p>
                  <p><span className="font-semibold">Subject:</span> {selectedEmail.subject}</p>
                  <p><span className="font-semibold">Summary:</span> {selectedEmail.aiSummary}</p>
                  <div>
                    <p className="font-semibold">Detected Tasks</p>
                    <ul className="mt-1 space-y-1">
                      {selectedEmail.detectedTasks.map((task) => (
                        <li key={task} className="text-gray-600">- {task}</li>
                      ))}
                    </ul>
                  </div>
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
