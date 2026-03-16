import { Upload, FileText, File, Image, FilePlus, Search, Bot } from "lucide-react";
import { useState } from "react";

export default function Documents() {
  const [selectedDoc, setSelectedDoc] = useState<number | null>(null);
  const [question, setQuestion] = useState("");
  const [aiAnswer, setAiAnswer] = useState("");

  const documents = [
    {
      id: 1,
      name: "Annual_Report_2024.pdf",
      type: "PDF",
      size: "2.4 MB",
      uploadDate: "2024-03-15",
      icon: FileText,
    },
    {
      id: 2,
      name: "Marketing_Strategy.docx",
      type: "DOCX",
      size: "856 KB",
      uploadDate: "2024-03-14",
      icon: File,
    },
    {
      id: 3,
      name: "Product_Mockups.png",
      type: "PNG",
      size: "1.2 MB",
      uploadDate: "2024-03-13",
      icon: Image,
    },
    {
      id: 4,
      name: "Financial_Projections.xlsx",
      type: "XLSX",
      size: "654 KB",
      uploadDate: "2024-03-12",
      icon: FileText,
    },
    {
      id: 5,
      name: "Employee_Handbook.pdf",
      type: "PDF",
      size: "3.1 MB",
      uploadDate: "2024-03-10",
      icon: FileText,
    },
    {
      id: 6,
      name: "Project_Proposal.docx",
      type: "DOCX",
      size: "1.8 MB",
      uploadDate: "2024-03-08",
      icon: File,
    },
  ];

  const handleAskAI = () => {
    if (!question.trim()) return;

    setAiAnswer(
      `Based on the document "${documents.find((d) => d.id === selectedDoc)?.name}", ${question.toLowerCase().includes("summary") ? "here's a summary: This document outlines key initiatives and strategic objectives for the upcoming quarter, with emphasis on market expansion and operational efficiency." : "the information you're looking for can be found in Section 3, pages 12-15. The document indicates positive trends with a 23% increase in key metrics compared to the previous period."}`
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">Document Intelligence</h1>
          <p className="text-gray-600 mt-1">Upload, manage, and analyze documents with AI</p>
        </div>
        <button className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2">
          <Upload size={18} />
          Upload Document
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-bold text-gray-800">Documents</h2>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={18} />
                <input
                  type="text"
                  placeholder="Search documents..."
                  className="pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {documents.map((doc) => {
                const Icon = doc.icon;
                return (
                  <div
                    key={doc.id}
                    onClick={() => setSelectedDoc(doc.id)}
                    className={`p-4 border-2 rounded-lg cursor-pointer transition-all ${
                      selectedDoc === doc.id
                        ? "border-indigo-500 bg-indigo-50"
                        : "border-gray-200 hover:border-gray-300 bg-white"
                    }`}
                  >
                    <div className="flex items-start gap-3">
                      <div className="p-3 bg-gray-100 rounded-lg">
                        <Icon size={24} className="text-gray-600" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="font-medium text-gray-800 truncate">{doc.name}</p>
                        <div className="flex items-center gap-2 mt-1">
                          <span className="text-xs text-gray-500">{doc.type}</span>
                          <span className="text-xs text-gray-400">•</span>
                          <span className="text-xs text-gray-500">{doc.size}</span>
                        </div>
                        <p className="text-xs text-gray-400 mt-1">{doc.uploadDate}</p>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {selectedDoc && (
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
              <h2 className="text-xl font-bold text-gray-800 mb-4">Document Viewer</h2>
              <div className="bg-gray-50 rounded-lg p-8 border-2 border-dashed border-gray-300 text-center">
                <FilePlus size={48} className="text-gray-400 mx-auto mb-3" />
                <p className="text-gray-600">Document preview for:</p>
                <p className="font-medium text-gray-800 mt-1">
                  {documents.find((d) => d.id === selectedDoc)?.name}
                </p>
              </div>
            </div>
          )}
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <div className="flex items-center gap-2 mb-4">
            <Bot size={24} className="text-indigo-600" />
            <h2 className="text-xl font-bold text-gray-800">Ask AI About Document</h2>
          </div>

          {!selectedDoc ? (
            <div className="text-center py-8">
              <p className="text-gray-500">Select a document to ask questions</p>
            </div>
          ) : (
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Selected Document:
                </label>
                <p className="text-sm text-indigo-600 font-medium">
                  {documents.find((d) => d.id === selectedDoc)?.name}
                </p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Your Question:
                </label>
                <textarea
                  value={question}
                  onChange={(e) => setQuestion(e.target.value)}
                  placeholder="What is this document about?"
                  className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
                  rows={4}
                />
              </div>

              <button
                onClick={handleAskAI}
                className="w-full px-4 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
              >
                Ask AI
              </button>

              {aiAnswer && (
                <div className="bg-gradient-to-br from-indigo-50 to-purple-50 rounded-lg p-4 border border-indigo-200">
                  <div className="flex items-start gap-3">
                    <Bot className="text-indigo-600 flex-shrink-0" size={20} />
                    <div>
                      <p className="font-medium text-gray-800 mb-1">AI Answer:</p>
                      <p className="text-sm text-gray-700">{aiAnswer}</p>
                    </div>
                  </div>
                </div>
              )}

              <div className="border-t border-gray-200 pt-4">
                <p className="text-sm font-medium text-gray-700 mb-2">Suggested Questions:</p>
                <div className="space-y-2">
                  {[
                    "Summarize this document",
                    "What are the key points?",
                    "Extract important dates",
                  ].map((suggestion, index) => (
                    <button
                      key={index}
                      onClick={() => setQuestion(suggestion)}
                      className="w-full text-left px-3 py-2 bg-gray-50 hover:bg-gray-100 rounded-lg text-sm text-gray-700 transition-colors"
                    >
                      {suggestion}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
