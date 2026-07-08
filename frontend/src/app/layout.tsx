import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Learning Buddy · 智能学习伙伴",
  description: "多智能体学习助手 — Orchestrator + 5 个专职 Agent 协作",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
