import { useEffect, useMemo, useState } from "react";
import { Plus, Play, Pause, Trash2, Mail, Bot, CheckSquare, Bell, Clock3 } from "lucide-react";
import { apiClient } from "../../api/client";
import { endpoints } from "../../api/endpoints";
import type { WorkflowExecutionItem, WorkflowItem, WorkflowTriggerItem } from "../../api/contracts";
import { formatDateTime, formatRelativeTime } from "../lib/format";

type TriggerType = "MANUAL" | "SCHEDULE" | "EVENT" | "WEBHOOK";

interface WorkflowForm {
  name: string;
  steps: string;
  triggerType: TriggerType;
  scheduleCron: string;
  eventType: string;
  provider: string;
  secret: string;
}

const DEFAULT_FORM: WorkflowForm = {
  name: "",
  steps: "",
  triggerType: "MANUAL",
  scheduleCron: "0 */5 * * * *",
  eventType: "",
  provider: "Webhook",
  secret: "",
};

function buildTriggers(form: WorkflowForm) {
  if (form.triggerType === "MANUAL") {
    return [];
  }

  return [{
    type: form.triggerType,
    scheduleCron: form.triggerType === "SCHEDULE" ? form.scheduleCron.trim() : undefined,
    eventType: form.triggerType === "EVENT" || form.triggerType === "WEBHOOK" ? form.eventType.trim() || undefined : undefined,
    provider: form.triggerType === "WEBHOOK" ? form.provider.trim() : undefined,
    secret: form.triggerType === "WEBHOOK" && form.secret.trim() ? form.secret.trim() : undefined,
  }];
}

function triggerLabel(trigger: WorkflowTriggerItem): string {
  if (trigger.type === "SCHEDULE") {
    return trigger.scheduleCron ?? "Scheduled";
  }
  if (trigger.type === "EVENT") {
    return trigger.eventType ?? "Event trigger";
  }
  if (trigger.type === "WEBHOOK") {
    return `${trigger.provider ?? "Webhook"}${trigger.eventType ? ` · ${trigger.eventType}` : ""}`;
  }
  return "Manual";
}

export default function WorkflowAutomation() {
  const [workflows, setWorkflows] = useState<WorkflowItem[]>([]);
  const [executions, setExecutions] = useState<Record<string, WorkflowExecutionItem[]>>({});
  const [selectedWorkflowId, setSelectedWorkflowId] = useState<string | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showBuilder, setShowBuilder] = useState(false);
  const [form, setForm] = useState<WorkflowForm>(DEFAULT_FORM);
  const [loading, setLoading] = useState(true);
  const [busyWorkflowId, setBusyWorkflowId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function refreshWorkflows(showLoader: boolean) {
      if (showLoader) {
        setLoading(true);
      }
      try {
        const envelope = await apiClient.request<WorkflowItem[]>(endpoints.workflows.list);
        if (!active) {
          return;
        }
        setWorkflows(envelope.data);
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load workflows");
        }
      } finally {
        if (active && showLoader) {
          setLoading(false);
        }
      }
    }

    void refreshWorkflows(true);
    const intervalId = window.setInterval(() => {
      void refreshWorkflows(false);
    }, 15000);

    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, []);

  useEffect(() => {
    if (!selectedWorkflowId) {
      return undefined;
    }

    let active = true;

    async function refreshExecutions() {
      try {
        const envelope = await apiClient.request<WorkflowExecutionItem[]>(endpoints.workflows.executions(selectedWorkflowId));
        if (!active) {
          return;
        }
        setExecutions((previous) => ({ ...previous, [selectedWorkflowId]: envelope.data }));
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load execution history");
        }
      }
    }

    void refreshExecutions();
    const intervalId = window.setInterval(() => {
      void refreshExecutions();
    }, 4000);

    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, [selectedWorkflowId]);

  async function handleCreateWorkflow() {
    if (!form.name.trim()) {
      setError("Workflow name is required");
      return;
    }

    try {
      const envelope = await apiClient.request<WorkflowItem>(endpoints.workflows.create, {
        method: "POST",
        body: {
          name: form.name,
          steps: form.steps.split("\n").map((step) => step.trim()).filter(Boolean),
          triggers: buildTriggers(form),
        },
      });
      setWorkflows((previous) => [envelope.data, ...previous]);
      setForm(DEFAULT_FORM);
      setShowCreateModal(false);
      setError(null);
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "Failed to create workflow");
    }
  }

  async function handlePatchStatus(workflow: WorkflowItem) {
    const nextStatus = workflow.status === "ACTIVE" ? "PAUSED" : "ACTIVE";
    setBusyWorkflowId(workflow.id);

    try {
      const envelope = await apiClient.request<WorkflowItem>(endpoints.workflows.status(workflow.id), {
        method: "PATCH",
        body: { status: nextStatus },
      });
      setWorkflows((previous) => previous.map((item) => (item.id === workflow.id ? envelope.data : item)));
      setError(null);
    } catch (statusError) {
      setError(statusError instanceof Error ? statusError.message : "Failed to update workflow status");
    } finally {
      setBusyWorkflowId(null);
    }
  }

  async function handleRunWorkflow(workflowId: string) {
    setBusyWorkflowId(workflowId);
    try {
      const envelope = await apiClient.request<WorkflowExecutionItem>(endpoints.workflows.run(workflowId), {
        method: "POST",
        body: {},
      });
      setSelectedWorkflowId(workflowId);
      setExecutions((previous) => ({ ...previous, [workflowId]: [envelope.data, ...(previous[workflowId] ?? [])] }));
      setError(null);
    } catch (runError) {
      setError(runError instanceof Error ? runError.message : "Failed to run workflow");
    } finally {
      setBusyWorkflowId(null);
    }
  }

  async function handleDeleteWorkflow(id: string) {
    if (!window.confirm("Delete this workflow?")) {
      return;
    }

    const snapshot = workflows;
    setWorkflows((previous) => previous.filter((workflow) => workflow.id !== id));

    try {
      await apiClient.request<{ status: string; id: string }>(endpoints.workflows.remove(id), { method: "DELETE" });
      if (selectedWorkflowId === id) {
        setSelectedWorkflowId(null);
      }
      setError(null);
    } catch (deleteError) {
      setWorkflows(snapshot);
      setError(deleteError instanceof Error ? deleteError.message : "Failed to delete workflow");
    }
  }

  async function handleLoadExecutions(id: string) {
    setSelectedWorkflowId(id);
    try {
      const envelope = await apiClient.request<WorkflowExecutionItem[]>(endpoints.workflows.executions(id));
      setExecutions((previous) => ({ ...previous, [id]: envelope.data }));
      setError(null);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Failed to load execution history");
    }
  }

  const activeCount = useMemo(() => workflows.filter((workflow) => workflow.status === "ACTIVE").length, [workflows]);
  const automatedCount = useMemo(() => workflows.filter((workflow) => workflow.triggers.some((trigger) => trigger.type !== "MANUAL")).length, [workflows]);
  const totalExecutions = useMemo(() => Object.values(executions).flat().length, [executions]);
  const successRate = useMemo(() => {
    const loadedExecutions = Object.values(executions).flat();
    if (loadedExecutions.length === 0) {
      return "100%";
    }
    const succeeded = loadedExecutions.filter((execution) => execution.status === "COMPLETED").length;
    return `${Math.round((succeeded / loadedExecutions.length) * 100)}%`;
  }, [executions]);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Workflow Automation</h1>
          <p className="mt-1 text-gray-600">Persisted triggers, execution polling, and manual runs now stay aligned with backend state.</p>
        </div>
        <button onClick={() => setShowCreateModal(true)} className="flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-white transition-colors hover:bg-indigo-700">
          <Plus size={18} />
          Create Workflow
        </button>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {loading && <div className="text-sm text-gray-600">Loading workflows...</div>}

      <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
        <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
          <div className="flex items-start justify-between">
            <div>
              <p className="mb-1 text-sm text-gray-600">Active Workflows</p>
              <p className="text-3xl font-bold text-gray-800">{activeCount}</p>
            </div>
            <div className="rounded-lg bg-green-500 p-3"><Play size={24} className="text-white" /></div>
          </div>
        </div>

        <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
          <div className="flex items-start justify-between">
            <div>
              <p className="mb-1 text-sm text-gray-600">Automated Triggers</p>
              <p className="text-3xl font-bold text-gray-800">{automatedCount}</p>
            </div>
            <div className="rounded-lg bg-blue-500 p-3"><Clock3 size={24} className="text-white" /></div>
          </div>
        </div>

        <div className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
          <div className="flex items-start justify-between">
            <div>
              <p className="mb-1 text-sm text-gray-600">Loaded Execution Success</p>
              <p className="text-3xl font-bold text-gray-800">{successRate}</p>
              <p className="mt-1 text-xs text-gray-500">{totalExecutions} executions cached in the UI</p>
            </div>
            <div className="rounded-lg bg-indigo-500 p-3"><CheckSquare size={24} className="text-white" /></div>
          </div>
        </div>
      </div>

      <div className="space-y-6">
        {workflows.map((workflow) => (
          <div key={workflow.id} className="rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
            <button onClick={() => void handleLoadExecutions(workflow.id)} className="w-full text-left">
              <div className="mb-6 flex items-start justify-between gap-4">
                <div>
                  <div className="mb-2 flex items-center gap-3">
                    <h3 className="text-xl font-bold text-gray-800">{workflow.name}</h3>
                    <span className={`rounded-full px-3 py-1 text-xs font-medium ${workflow.status === "ACTIVE" ? "bg-green-100 text-green-700" : workflow.status === "PAUSED" ? "bg-orange-100 text-orange-700" : "bg-slate-100 text-slate-700"}`}>
                      {workflow.status}
                    </span>
                  </div>
                  <p className="text-gray-600">{workflow.steps.join(" -> ") || "No steps configured"}</p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {(workflow.triggers.length === 0 ? [{ id: `${workflow.id}-manual`, type: "MANUAL", scheduleCron: null, eventType: null, provider: null, enabled: true, lastFiredAt: null }] : workflow.triggers).map((trigger) => (
                      <span key={trigger.id} className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700">
                        {trigger.type}: {triggerLabel(trigger)}
                        {trigger.lastFiredAt ? ` · last fired ${formatRelativeTime(trigger.lastFiredAt)}` : ""}
                      </span>
                    ))}
                  </div>
                </div>

                <div className="flex items-center gap-2" onClick={(event) => event.stopPropagation()}>
                  <button onClick={() => void handleRunWorkflow(workflow.id)} className="rounded-lg bg-indigo-100 p-2 transition-colors hover:bg-indigo-200" disabled={busyWorkflowId === workflow.id} title="Run now">
                    <Play size={18} className="text-indigo-700" />
                  </button>
                  <button onClick={() => void handlePatchStatus(workflow)} className={`rounded-lg p-2 transition-colors ${workflow.status === "ACTIVE" ? "bg-orange-100 hover:bg-orange-200" : "bg-green-100 hover:bg-green-200"}`} disabled={busyWorkflowId === workflow.id} title={workflow.status === "ACTIVE" ? "Pause workflow" : "Activate workflow"}>
                    {workflow.status === "ACTIVE" ? <Pause size={18} className="text-orange-700" /> : <Play size={18} className="text-green-700" />}
                  </button>
                  <button onClick={() => void handleDeleteWorkflow(workflow.id)} className="rounded-lg bg-red-100 p-2 transition-colors hover:bg-red-200" title="Delete workflow">
                    <Trash2 size={18} className="text-red-700" />
                  </button>
                </div>
              </div>
            </button>

            <div className="relative">
              <div className="flex items-center justify-between gap-4">
                {workflow.steps.map((step, index) => {
                  const iconMap = [Mail, Bot, CheckSquare, Bell];
                  const Icon = iconMap[index % iconMap.length];
                  const colors = ["bg-blue-500", "bg-indigo-500", "bg-green-500", "bg-orange-500"];

                  return (
                    <div key={`${workflow.id}-${step}-${index}`} className="flex-1">
                      <div className="flex flex-col items-center">
                        <div className={`${colors[index % colors.length]} mb-3 rounded-xl p-4`}><Icon size={24} className="text-white" /></div>
                        <p className="text-center text-sm font-medium text-gray-700">{step}</p>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {selectedWorkflowId === workflow.id && executions[workflow.id] && (
              <div className="mt-6 border-t border-gray-200 pt-4">
                <div className="mb-3 flex items-center justify-between">
                  <h4 className="font-semibold text-gray-800">Execution History</h4>
                  <span className="text-xs text-gray-500">Auto-refreshing every 4 seconds</span>
                </div>
                <div className="space-y-3">
                  {executions[workflow.id].length === 0 && <div className="rounded-lg bg-gray-50 p-3 text-sm text-gray-600">No executions recorded yet.</div>}
                  {executions[workflow.id].map((execution) => (
                    <div key={execution.executionId} className="rounded-lg border border-gray-200 bg-gray-50 p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                        <div>
                          <div className="font-medium text-gray-800">{execution.triggerLabel}</div>
                          <div className="text-gray-500">{execution.executionId}</div>
                        </div>
                        <div className="text-right">
                          <div className={`font-medium ${execution.status === "COMPLETED" ? "text-green-700" : execution.status === "FAILED" ? "text-red-700" : "text-indigo-700"}`}>{execution.status}</div>
                          <div className="text-gray-500">{execution.durationMs}ms · {formatDateTime(execution.startedAt)}</div>
                        </div>
                      </div>
                      {execution.errorMessage && <div className="mt-2 text-sm text-red-700">{execution.errorMessage}</div>}
                      {execution.stepResults.length > 0 && (
                        <div className="mt-3 grid gap-2 md:grid-cols-2">
                          {execution.stepResults.map((stepResult) => (
                            <div key={`${execution.executionId}-${stepResult.index}`} className="rounded-md bg-white p-3 text-sm shadow-sm">
                              <div className="font-medium text-gray-800">{stepResult.name}</div>
                              <div className="text-gray-600">{stepResult.status}</div>
                              <div className="mt-1 text-xs text-gray-500">
                                {stepResult.completedAt ? `Completed ${formatRelativeTime(stepResult.completedAt)}` : stepResult.startedAt ? `Started ${formatRelativeTime(stepResult.startedAt)}` : "Pending"}
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="rounded-xl border border-indigo-200 bg-gradient-to-br from-indigo-50 to-sky-50 p-6">
        <div className="flex items-start gap-4">
          <Bot className="flex-shrink-0 text-indigo-600" size={24} />
          <div>
            <h3 className="mb-2 font-semibold text-gray-800">Workflow Builder Panel</h3>
            <p className="mb-4 text-sm text-gray-600">The workflow card state is now backed by live execution polling, so this builder can attach to a real runtime instead of placeholder data.</p>
            <button onClick={() => setShowBuilder((previous) => !previous)} className="flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm text-white transition-colors hover:bg-indigo-700">
              <Plus size={16} />
              {showBuilder ? "Hide Builder Stub" : "Open Builder Stub"}
            </button>
            {showBuilder && (
              <div className="mt-4 rounded-lg border border-indigo-200 bg-white p-4 text-sm text-gray-700">
                Builder stub: add node editing here once the design layer is ready. Trigger and execution plumbing is already wired through the API.
              </div>
            )}
          </div>
        </div>
      </div>

      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4">
          <div className="w-full max-w-2xl space-y-4 rounded-xl bg-white p-6 shadow-lg">
            <h2 className="text-xl font-bold text-gray-800">Create Workflow</h2>
            <input value={form.name} onChange={(event) => setForm((previous) => ({ ...previous, name: event.target.value }))} placeholder="Workflow name" className="w-full rounded-lg border border-gray-300 px-4 py-2" />
            <textarea value={form.steps} onChange={(event) => setForm((previous) => ({ ...previous, steps: event.target.value }))} placeholder="One step per line" rows={4} className="w-full rounded-lg border border-gray-300 px-4 py-2" />

            <div className="grid gap-4 md:grid-cols-2">
              <label className="space-y-2 text-sm text-gray-700">
                <span className="font-medium">Trigger type</span>
                <select value={form.triggerType} onChange={(event) => setForm((previous) => ({ ...previous, triggerType: event.target.value as TriggerType }))} className="w-full rounded-lg border border-gray-300 px-4 py-2">
                  <option value="MANUAL">Manual only</option>
                  <option value="SCHEDULE">Scheduled</option>
                  <option value="EVENT">Internal event</option>
                  <option value="WEBHOOK">Webhook</option>
                </select>
              </label>

              {form.triggerType === "SCHEDULE" && (
                <label className="space-y-2 text-sm text-gray-700">
                  <span className="font-medium">Cron expression</span>
                  <input value={form.scheduleCron} onChange={(event) => setForm((previous) => ({ ...previous, scheduleCron: event.target.value }))} placeholder="0 */5 * * * *" className="w-full rounded-lg border border-gray-300 px-4 py-2" />
                </label>
              )}

              {form.triggerType === "EVENT" && (
                <label className="space-y-2 text-sm text-gray-700">
                  <span className="font-medium">Event type</span>
                  <input value={form.eventType} onChange={(event) => setForm((previous) => ({ ...previous, eventType: event.target.value }))} placeholder="email.received" className="w-full rounded-lg border border-gray-300 px-4 py-2" />
                </label>
              )}

              {form.triggerType === "WEBHOOK" && (
                <>
                  <label className="space-y-2 text-sm text-gray-700">
                    <span className="font-medium">Provider</span>
                    <input value={form.provider} onChange={(event) => setForm((previous) => ({ ...previous, provider: event.target.value }))} placeholder="Webhook" className="w-full rounded-lg border border-gray-300 px-4 py-2" />
                  </label>
                  <label className="space-y-2 text-sm text-gray-700">
                    <span className="font-medium">Webhook event type</span>
                    <input value={form.eventType} onChange={(event) => setForm((previous) => ({ ...previous, eventType: event.target.value }))} placeholder="document.uploaded" className="w-full rounded-lg border border-gray-300 px-4 py-2" />
                  </label>
                  <label className="space-y-2 text-sm text-gray-700 md:col-span-2">
                    <span className="font-medium">Optional trigger secret override</span>
                    <input value={form.secret} onChange={(event) => setForm((previous) => ({ ...previous, secret: event.target.value }))} placeholder="Leave blank to rely on the integration secret" className="w-full rounded-lg border border-gray-300 px-4 py-2" />
                  </label>
                </>
              )}
            </div>

            <div className="flex justify-end gap-2">
              <button onClick={() => setShowCreateModal(false)} className="rounded-lg border border-gray-300 px-4 py-2">Cancel</button>
              <button onClick={() => void handleCreateWorkflow()} className="rounded-lg bg-indigo-600 px-4 py-2 text-white hover:bg-indigo-700">Create</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
