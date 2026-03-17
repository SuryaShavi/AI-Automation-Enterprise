Design a modern, user-friendly enterprise SaaS web application interface for a product called:

"AI Enterprise Automation Platform"

This platform is an internal AI-powered automation system that helps organizations automate tasks, process emails, understand documents, generate reports, and assist employees through an AI chatbot.

The design must be clean, professional, and suitable for a real enterprise product similar to tools like Notion, Slack, Linear, or Retool.

Target frontend framework for development:
React + Vite + TailwindCSS + Node

The UI should be modular and component-based so it can be easily converted into React components.

Use a modern enterprise dashboard layout with:

LEFT SIDEBAR NAVIGATION
TOP NAVBAR
MAIN CONTENT AREA

Color Theme:
Primary: Indigo (#6366F1)
Background: Light gray (#F9FAFB)
Sidebar: Dark (#111827)
Cards: White
Accent colors:
Green (#22C55E)
Blue (#3B82F6)
Orange (#F59E0B)
Red (#EF4444)

Typography:
Inter or Poppins
Minimal, modern, high readability.

The interface must support the following modules because they will connect to backend microservices and AI systems.

-----------------------------------

MAIN NAVIGATION SIDEBAR

Include the following sections:

• Dashboard
• AI Assistant
• Email Automation
• Tasks
• Documents
• Reports
• Notifications
• Workflow Automation
• Integrations
• Settings

Each item should have icons and clear labels.

-----------------------------------

TOP NAVBAR

Include:

• Global search bar
• AI quick command input
• Notification bell
• User profile avatar
• Organization switcher
• Dark/light mode toggle

-----------------------------------

PAGE 1 — LOGIN / AUTHENTICATION

Design a modern login page.

Features:
• Email input
• Password input
• Login button
• Forgot password
• Sign up option
• Company branding area
• Illustration for AI automation theme

-----------------------------------

PAGE 2 — DASHBOARD

Dashboard should show system overview.

Sections:

Welcome card
"Welcome back, [User]"

Key metrics cards:

• Total Tasks
• Pending Tasks
• Completed Tasks
• Documents Uploaded
• AI Requests Today

Activity Feed

Recent system activity:
• Task created
• Document uploaded
• Report generated
• AI chat request

AI Insights Panel

Example:

"AI detected 5 new tasks from emails today."

Recent Notifications

System health status card.

-----------------------------------

PAGE 3 — AI ASSISTANT

Create a chatbot interface similar to ChatGPT.

Layout:

Left panel:
Chat history

Main panel:
Chat conversation window

Features:

• message bubbles
• system responses
• typing indicator
• input text box
• send button
• suggested prompts

Suggested prompts:

"What tasks are pending?"
"Summarize today's emails"
"Generate weekly report"
"Explain HR policy"

Include ability to attach files.

-----------------------------------

PAGE 4 — EMAIL AUTOMATION

Email automation dashboard.

Sections:

Email summary cards

• Emails processed
• Tasks detected
• Pending review

Email list view

Columns:

• Sender
• Subject
• AI summary
• Detected tasks
• Status

Email preview panel with AI-generated summary.

-----------------------------------

PAGE 5 — TASK MANAGEMENT

Task management page.

Include:

Create Task button

Task table with columns:

• Task name
• Description
• Assigned user
• Priority
• Deadline
• Status
• Actions

Include status badges:

Pending
In Progress
Completed

Priority colors:

Low
Medium
High
Urgent

Add kanban board option:

• To Do
• In Progress
• Completed

-----------------------------------

PAGE 6 — DOCUMENT INTELLIGENCE

Document management page.

Features:

Upload Document button

Document cards showing:

• document name
• type
• upload date
• size

Document viewer panel.

Include "Ask AI about this document" section.

Question input box.

Show AI answers referencing document sections.

-----------------------------------

PAGE 7 — REPORTS & ANALYTICS

Reports dashboard.

Charts:

• Weekly productivity
• Task completion rate
• AI usage analytics
• Team activity

Use:

Bar chart
Line chart
Pie chart

Include:

Generate Report button.

Report preview panel.

-----------------------------------

PAGE 8 — NOTIFICATIONS

Notification center.

List of notifications.

Examples:

• New task created
• Report generated
• Document uploaded
• AI task extracted

Each notification includes:

icon
timestamp
status label

-----------------------------------

PAGE 9 — WORKFLOW AUTOMATION

Workflow builder UI.

Allow users to view automation flows.

Example:

Email Received → AI Task Extraction → Task Created → Notification Sent

Show workflow nodes connected visually.

Include:

Create Workflow button.

-----------------------------------

PAGE 10 — INTEGRATIONS

Integration settings.

Show connection options for:

• Email (Gmail / Outlook)
• Slack
• Google Drive
• API integrations

Each integration card includes:

Connect button
Status indicator.

-----------------------------------

PAGE 11 — SETTINGS

User settings.

Sections:

Profile

• Name
• Email
• Role

Security

• Change password
• Two-factor authentication

Notifications

• Email alerts
• System alerts

-----------------------------------

COMPONENT DESIGN RULES

Use reusable UI components:

Sidebar
Navbar
Cards
Tables
Charts
Forms
Buttons
Modals
Notifications
Chat interface

Use modern UI patterns:

rounded cards
soft shadows
subtle hover animations
clean spacing
consistent iconography.

-----------------------------------

RESPONSIVE DESIGN

Design layouts for:

Desktop
Tablet
Mobile

-----------------------------------

EXPORT STRUCTURE

Design should easily map to React components.

Suggested structure:

components/
layout/
pages/
hooks/
services/

Use Tailwind utility classes.

-----------------------------------

The design should look like a real enterprise SaaS product ready for production use.