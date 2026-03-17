import { useEffect, useMemo, useRef, useState } from "react";
import { Download, FileText, TrendingUp } from "lucide-react";
import { BarChart, Bar, LineChart, Line, PieChart, Pie, Cell, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts";
import { apiClient } from "../../api/client";
import { endpoints } from "../../api/endpoints";
import type { ReportAnalytics, ReportAnalyticsPoint, ReportItem } from "../../api/contracts";
import { formatDateTime } from "../lib/format";

interface GenerateForm {
  reportType: string;
  title: string;
}

const COLORS = ["#6366F1", "#22C55E", "#F59E0B", "#3B82F6", "#EF4444"];

function toCsv(data: ReportAnalyticsPoint[]): string {
  if (data.length === 0) {
    return "";
  }

  const headers = Object.keys(data[0]);
  const rows = data.map((row) => headers.map((header) => String(row[header] ?? "")).join(","));
  return [headers.join(","), ...rows].join("\n");
}

function downloadBlob(fileName: string, content: string, type: string) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

export default function Reports() {
  const [analytics, setAnalytics] = useState<ReportAnalytics>({
    weeklyProductivity: [],
    taskCompletionRate: [],
    aiUsageAnalytics: [],
  });
  const [reports, setReports] = useState<ReportItem[]>([]);
  const [selectedReport, setSelectedReport] = useState<ReportItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showGenerateModal, setShowGenerateModal] = useState(false);
  const [generateForm, setGenerateForm] = useState<GenerateForm>({ reportType: "WEEKLY_PRODUCTIVITY", title: "" });
  const reportsRef = useRef<ReportItem[]>([]);

  useEffect(() => {
    reportsRef.current = reports;
  }, [reports]);

  useEffect(() => {
    let active = true;

    async function loadData() {
      try {
        const [analyticsEnvelope, reportsEnvelope] = await Promise.all([
          apiClient.request<ReportAnalytics>(endpoints.reports.analytics),
          apiClient.request<{ items: ReportItem[] }>(endpoints.reports.list),
        ]);

        if (!active) {
          return;
        }

        setAnalytics(analyticsEnvelope.data);
        setReports(reportsEnvelope.data.items);
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load reports");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    async function pollInProgressReports() {
      try {
        if (!active) {
          return;
        }

        const hasInProgress = reportsRef.current.some((report) => report.status === "REQUESTED" || report.status === "IN_PROGRESS");
        if (!hasInProgress) {
          return;
        }

        const reportsEnvelope = await apiClient.request<{ items: ReportItem[] }>(endpoints.reports.list);
        if (active) {
          setReports(reportsEnvelope.data.items);
        }
      } catch {
        // Ignore polling errors.
      }
    }

    void loadData();
    const pollId = window.setInterval(() => {
      void pollInProgressReports();
    }, 20_000);

    return () => {
      active = false;
      window.clearInterval(pollId);
    };
  }, []);

  async function handleGenerateReport() {
    if (!generateForm.title.trim()) {
      return;
    }

    try {
      const envelope = await apiClient.request<ReportItem>(endpoints.reports.generate, {
        method: "POST",
        body: {
          reportType: generateForm.reportType,
          title: generateForm.title,
          parameters: {},
        },
      });

      setReports((previous) => [envelope.data, ...previous]);
      setShowGenerateModal(false);
      setGenerateForm({ reportType: "WEEKLY_PRODUCTIVITY", title: "" });
    } catch (generateError) {
      setError(generateError instanceof Error ? generateError.message : "Failed to generate report");
    }
  }

  async function handleViewReport(reportId: string) {
    try {
      const envelope = await apiClient.request<ReportItem>(endpoints.reports.detail(reportId));
      setSelectedReport(envelope.data);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Failed to load report details");
    }
  }

  const latestReportSummary = useMemo(() => selectedReport ?? reports[0] ?? null, [reports, selectedReport]);

  if (loading) {
    return (
      <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
        <div className="w-8 h-8 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin"></div>
        <p className="text-sm text-gray-600 mt-3">Loading reports...</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Reports & Analytics</h1>
          <p className="text-gray-600 mt-1">Track performance and generate insights</p>
        </div>
        <button onClick={() => setShowGenerateModal(true)} className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2">
          <FileText size={18} />
          Generate Report
        </button>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-bold text-gray-800">Weekly Productivity</h2>
            <button onClick={() => downloadBlob("weekly-productivity.json", JSON.stringify(analytics.weeklyProductivity, null, 2), "application/json")} className="p-2 hover:bg-gray-100 rounded-lg transition-colors">
              <Download size={18} className="text-gray-600" />
            </button>
          </div>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={analytics.weeklyProductivity}>
              <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
              <XAxis dataKey="day" stroke="#6B7280" />
              <YAxis stroke="#6B7280" />
              <Tooltip contentStyle={{ backgroundColor: "#FFF", border: "1px solid #E5E7EB", borderRadius: "8px" }} />
              <Legend />
              <Bar dataKey="tasks" fill="#6366F1" name="Total Tasks" radius={[8, 8, 0, 0]} />
              <Bar dataKey="completed" fill="#22C55E" name="Completed" radius={[8, 8, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-bold text-gray-800">Task Completion Rate</h2>
            <button onClick={() => downloadBlob("task-completion-rate.csv", toCsv(analytics.taskCompletionRate), "text/csv") } className="p-2 hover:bg-gray-100 rounded-lg transition-colors">
              <Download size={18} className="text-gray-600" />
            </button>
          </div>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={analytics.taskCompletionRate}>
              <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
              <XAxis dataKey="month" stroke="#6B7280" />
              <YAxis stroke="#6B7280" />
              <Tooltip contentStyle={{ backgroundColor: "#FFF", border: "1px solid #E5E7EB", borderRadius: "8px" }} />
              <Legend />
              <Line type="monotone" dataKey="rate" stroke="#6366F1" strokeWidth={3} name="Completion Rate (%)" dot={{ fill: "#6366F1", r: 6 }} activeDot={{ r: 8 }} />
            </LineChart>
          </ResponsiveContainer>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-bold text-gray-800">AI Usage Analytics</h2>
            <button onClick={() => downloadBlob("ai-usage.json", JSON.stringify(analytics.aiUsageAnalytics, null, 2), "application/json")} className="p-2 hover:bg-gray-100 rounded-lg transition-colors">
              <Download size={18} className="text-gray-600" />
            </button>
          </div>
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={analytics.aiUsageAnalytics}
                cx="50%"
                cy="50%"
                labelLine={false}
                label={({ name, percent }) => `${name}: ${((percent ?? 0) * 100).toFixed(0)}%`}
                outerRadius={100}
                dataKey="value"
              >
                {analytics.aiUsageAnalytics.map((_, index) => <Cell key={index} fill={COLORS[index % COLORS.length]} />)}
              </Pie>
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <h2 className="text-xl font-bold text-gray-800 mb-6">Report History</h2>
          <div className="space-y-3">
            {reports.map((report) => (
              <div key={report.id} className="p-4 bg-gray-50 rounded-lg border border-gray-200">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-medium text-gray-800">{report.title}</p>
                    <p className="text-xs text-gray-500">{report.reportType} - {formatDateTime(report.requestedAt)}</p>
                  </div>
                  <span className={`px-2 py-1 rounded-full text-xs ${report.status === "GENERATED" ? "bg-green-100 text-green-700" : "bg-orange-100 text-orange-700"}`}>{report.status}</span>
                </div>
                <button onClick={() => void handleViewReport(report.id)} className="mt-3 text-sm text-indigo-600 hover:text-indigo-700">View Full Report</button>
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
          {!latestReportSummary ? (
            <p className="text-sm text-gray-600">No report selected.</p>
          ) : (
            <>
              <h3 className="font-semibold text-gray-800 mb-3">{latestReportSummary.title}</h3>
              <div className="space-y-2 text-sm text-gray-700">
                <p>- Type: {latestReportSummary.reportType}</p>
                <p>- Status: {latestReportSummary.status}</p>
                <p>- Requested: {formatDateTime(latestReportSummary.requestedAt)}</p>
                <p>- Payload keys: {Object.keys(latestReportSummary.payload ?? {}).join(", ") || "None"}</p>
              </div>
            </>
          )}
        </div>
      </div>

      {showGenerateModal && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-lg w-full max-w-lg p-6 space-y-4">
            <h2 className="text-xl font-bold text-gray-800">Generate Report</h2>
            <select value={generateForm.reportType} onChange={(event) => setGenerateForm((previous) => ({ ...previous, reportType: event.target.value }))} className="w-full px-4 py-2 border border-gray-300 rounded-lg">
              <option value="WEEKLY_PRODUCTIVITY">WEEKLY_PRODUCTIVITY</option>
              <option value="TASK_ANALYTICS">TASK_ANALYTICS</option>
              <option value="AI_USAGE">AI_USAGE</option>
            </select>
            <input value={generateForm.title} onChange={(event) => setGenerateForm((previous) => ({ ...previous, title: event.target.value }))} placeholder="Report title" className="w-full px-4 py-2 border border-gray-300 rounded-lg" />
            <div className="flex justify-end gap-2">
              <button onClick={() => setShowGenerateModal(false)} className="px-4 py-2 border border-gray-300 rounded-lg">Cancel</button>
              <button onClick={() => void handleGenerateReport()} className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700">Generate</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
