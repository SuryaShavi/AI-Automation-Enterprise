import { useEffect, useMemo, useState } from "react";
import { Plus, Play, Pause, Trash2, Mail, Bot, CheckSquare, Bell } from "lucide-react";
import { apiClient } from "../../api/client";
import { endpoints } from "../../api/endpoints";
import type { WorkflowExecutionItem, WorkflowItem } from "../../api/contracts";

interface WorkflowForm {
  name: string;
  steps: string;
}

const DEFAULT_FORM: WorkflowForm = { name: "", steps: "" };

export default function WorkflowAutomation() {
  const [workflows, setWorkflows] = useState<WorkflowItem[]>([]);
  const [executions, setExecutions] = useState<Record<string, WorkflowExecutionItem[]>>({});
  const [selectedWorkflowId, setSelectedWorkflowId] = useState<string | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showBuilder, setShowBuilder] = useState(false);
  const [form, setForm] = useState<WorkflowForm>(DEFAULT_FORM);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function loadWorkflows() {
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
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadWorkflows();

    return () => {
      active = false;
    };
  }, []);

  async function handleCreateWorkflow() {
    if (!form.name.trim()) {
      return;
    }

    try {
      const envelope = await apiClient.request<WorkflowItem>(endpoints.workflows.create, {
        method: "POST",
        body: {
          name: form.name,
          steps: form.steps.split("\n").map((step) => step.trim()).filter(Boolean),
        },
      });

      setWorkflows((previous) => [envelope.data, ...previous]);
      setForm(DEFAULT_FORM);
      setShowCreateModal(false);
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "Failed to create workflow");
    }
  }

  async function handlePatchStatus(workflow: WorkflowItem) {
    const nextStatus = workflow.status === "ACTIVE" ? "PAUSED" : "ACTIVE";

    try {
      const envelope = await apiClient.request<WorkflowItem>(endpoints.workflows.status(workflow.id), {
        method: "PATCH",
        body: { status: nextStatus },
      });

      setWorkflows((previous) => previous.map((item) => (item.id === workflow.id ? envelope.data : item)));
    } catch (statusError) {
      setError(statusError instanceof Error ? statusError.message : "Failed to update workflow status");
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
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Failed to load execution history");
    }
  }

  const activeCount = useMemo(() => workflows.filter((workflow) => workflow.status === "ACTIVE").length, [workflows]);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Workflow Automation</h1>
          <p className="text-gray-600 mt-1">Create and manage automated workflows</p>
        </div>
        <button onClick={() => setShowCreateModal(true)} className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2">
          <Plus size={18} />
          Create Workflow
        </button>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {loading && <div className="text-sm text-gray-600">Loading workflows...</div>}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-gray-600 text-sm mb-1">Active Workflows</p>
              <p className="text-3xl font-bold text-gray-800">{activeCount}</p>
            </div>
            <div className="bg-green-500 p-3 rounded-lg"><Play size={24} className="text-white" /></div>
          </div>
        </div>

        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-gray-600 text-sm mb-1">Total Executions</p>
              <p className="text-3xl font-bold text-gray-800">{Object.values(executions).flat().length}</p>
            </div>
            <div className="bg-indigo-500 p-3 rounded-lg"><CheckSquare size={24} className="text-white" /></div>
          </div>
        </div>

        <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-gray-600 text-sm mb-1">Success Rate</p>
              <p className="text-3xl font-bold text-gray-800">98.5%</p>
            </div>
            <div className="bg-blue-500 p-3 rounded-lg"><Bot size={24} className="text-white" /></div>
          </div>
        </div>
      </div>

      <div className="space-y-6">
        {workflows.map((workflow) => (
          <div key={workflow.id} className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
            <button onClick={() => void handleLoadExecutions(workflow.id)} className="w-full text-left">
              <div className="flex items-start justify-between mb-6">
                <div>
                  <div className="flex items-center gap-3 mb-2">
                    <h3 className="text-xl font-bold text-gray-800">{workflow.name}</h3>
                    <span className={`px-3 py-1 rounded-full text-xs font-medium ${workflow.status === "ACTIVE" ? "bg-green-100 text-green-700" : "bg-orange-100 text-orange-700"}`}>
                      {workflow.status === "ACTIVE" ? "Active" : "Paused"}
                    </span>
                  </div>
                  <p className="text-gray-600">{workflow.steps.join(" -> ")}</p>
                </div>

                <div className="flex items-center gap-2" onClick={(event) => event.stopPropagation()}>
                  <button onClick={() => void handlePatchStatus(workflow)} className={`p-2 rounded-lg transition-colors ${workflow.status === "ACTIVE" ? "bg-orange-100 hover:bg-orange-200" : "bg-green-100 hover:bg-green-200"}`}>
                    {workflow.status === "ACTIVE" ? <Pause size={18} className="text-orange-700" /> : <Play size={18} className="text-green-700" />}
                  </button>
                  <button onClick={() => void handleDeleteWorkflow(workflow.id)} className="p-2 bg-red-100 hover:bg-red-200 rounded-lg transition-colors">
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
                        <div className={`${colors[index % colors.length]} p-4 rounded-xl mb-3`}><Icon size={24} className="text-white" /></div>
                        <p className="text-sm font-medium text-gray-700 text-center">{step}</p>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {selectedWorkflowId === workflow.id && executions[workflow.id] && (
              <div className="mt-6 border-t border-gray-200 pt-4">
                <h4 className="font-semibold text-gray-800 mb-2">Execution History</h4>
                <div className="space-y-2">
                  {executions[workflow.id].map((execution) => (
                    <div key={execution.executionId} className="text-sm text-gray-600 bg-gray-50 p-3 rounded-lg">
                      {execution.executionId} - {execution.status} - {execution.durationMs}ms
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="bg-gradient-to-br from-indigo-50 to-purple-50 rounded-xl p-6 border border-indigo-200">
        <div className="flex items-start gap-4">
          <Bot className="text-indigo-600 flex-shrink-0" size={24} />
          <div>
            <h3 className="font-semibold text-gray-800 mb-2">Create Custom Workflow</h3>
            <p className="text-sm text-gray-600 mb-4">Build custom automation workflows with the visual workflow builder panel.</p>
            <button onClick={() => setShowBuilder((previous) => !previous)} className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors text-sm flex items-center gap-2">
              <Plus size={16} />
              Open Workflow Builder
            </button>
            {showBuilder && (
              <div className="mt-4 p-4 bg-white rounded-lg border border-indigo-200 text-sm text-gray-700">
                Workflow builder placeholder: drag-and-drop builder can be mounted here.
              </div>
            )}
          </div>
        </div>
      </div>

      {showCreateModal && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-lg w-full max-w-lg p-6 space-y-4">
            <h2 className="text-xl font-bold text-gray-800">Create Workflow</h2>
            <input value={form.name} onChange={(event) => setForm((previous) => ({ ...previous, name: event.target.value }))} placeholder="Workflow name" className="w-full px-4 py-2 border border-gray-300 rounded-lg" />
            <textarea value={form.steps} onChange={(event) => setForm((previous) => ({ ...previous, steps: event.target.value }))} placeholder="One step per line" rows={4} className="w-full px-4 py-2 border border-gray-300 rounded-lg" />
            <div className="flex justify-end gap-2">
              <button onClick={() => setShowCreateModal(false)} className="px-4 py-2 border border-gray-300 rounded-lg">Cancel</button>
              <button onClick={() => void handleCreateWorkflow()} className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700">Create</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
