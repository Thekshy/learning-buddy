"use client";

import { useState } from "react";
import { CheckCircle2, XCircle, Loader2, Send } from "lucide-react";
import { quizApi, ApiError } from "@/lib/api";
import type { QuizQuestion, GradeResponse } from "@/lib/types";

/**
 * 答题卡片:渲染题目 → 用户作答 → 提交判分 → 展示对错 + 解析。
 *
 * 从 assistant 消息的 detail 里解析出 generateQuiz 工具的产出。
 * quizId 存在时支持判分(写 attempt + 错题本 + 掌握度);
 * quizId 为 null 时只展示题目(落库失败兜底)。
 */
export default function QuizCard({
  quizId,
  questions,
}: {
  quizId: number | null;
  questions: QuizQuestion[];
}) {
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<GradeResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);

  function setAns(qIndex: number, val: string) {
    setAnswers((a) => ({ ...a, [qIndex]: val }));
  }

  async function submit() {
    if (quizId == null) {
      setErr("该练习未落库,无法判分(仅展示)");
      return;
    }
    setBusy(true);
    setErr(null);
    try {
      const body = {
        answers: questions.map((q, i) => ({
          questionId: (q as QuizQuestion & { questionId?: number }).questionId ?? i,
          userAnswer: answers[i] ?? "",
        })),
      };
      const res = await quizApi.grade(quizId, body);
      setResult(res);
    } catch (e: unknown) {
      setErr(e instanceof ApiError ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mt-3 border border-slate-200 rounded-xl overflow-hidden bg-slate-50">
      <div className="bg-brand-50 px-4 py-2 border-b border-slate-200 flex items-center gap-2">
        <span className="text-sm font-semibold text-brand-700">📝 练习题</span>
        {quizId != null && (
          <span className="text-xs text-slate-400 ml-auto">quiz #{quizId}</span>
        )}
      </div>

      <div className="p-4 space-y-4">
        {questions.map((q, i) => (
          <QuestionRow
            key={i}
            index={i}
            question={q}
            selected={answers[i]}
            onSelect={(v) => setAns(i, v)}
            graded={findGraded(result, i, q)}
          />
        ))}
      </div>

      {err && <p className="px-4 pb-2 text-rose-500 text-xs">{err}</p>}

      {result ? (
        <div className="px-4 py-3 bg-white border-t border-slate-200 flex items-center gap-3 text-sm">
          <CheckCircle2 className="w-4 h-4 text-emerald-500" />
          <span>
            得分:{result.summary.correct}/{result.summary.total}(
            {result.summary.accuracy}%)
          </span>
          <button
            onClick={() => {
              setResult(null);
              setAnswers({});
            }}
            className="ml-auto text-xs text-brand-600 hover:underline"
          >
            重做
          </button>
        </div>
      ) : (
        <div className="px-4 py-3 bg-white border-t border-slate-200">
          <button
            onClick={submit}
            disabled={busy || quizId == null}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700 disabled:opacity-50 transition"
          >
            {busy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
            提交答案
          </button>
        </div>
      )}
    </div>
  );
}

/** 单题渲染:选择题(选项)/ 填空题(输入框)+ 判分后展示对错 + 解析 */
function QuestionRow({
  index,
  question,
  selected,
  onSelect,
  graded,
}: {
  index: number;
  question: QuizQuestion;
  selected: string | undefined;
  onSelect: (v: string) => void;
  graded: GradeResponse["graded"][number] | null;
}) {
  const isChoice = question.options != null && question.options.length > 0;

  return (
    <div>
      <div className="text-sm font-medium text-slate-700 mb-2">
        {index + 1}. {question.stem}
      </div>

      {isChoice ? (
        <div className="space-y-1">
          {question.options!.map((opt) => {
            const picked = selected === opt;
            const showCorrect = graded != null && opt === graded.correctAnswer;
            const showWrong = graded != null && picked && !graded.correct;
            return (
              <label
                key={opt}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-lg cursor-pointer text-sm border transition ${
                  showCorrect
                    ? "bg-emerald-50 border-emerald-300 text-emerald-700"
                    : showWrong
                      ? "bg-rose-50 border-rose-300 text-rose-700"
                      : picked
                        ? "bg-brand-50 border-brand-300"
                        : "bg-white border-slate-200 hover:border-slate-300"
                }`}
              >
                <input
                  type="radio"
                  name={`q-${index}`}
                  checked={picked}
                  onChange={() => onSelect(opt)}
                  disabled={graded != null}
                  className="accent-brand-600"
                />
                {opt}
                {showCorrect && <CheckCircle2 className="w-3.5 h-3.5 ml-auto" />}
                {showWrong && <XCircle className="w-3.5 h-3.5 ml-auto" />}
              </label>
            );
          })}
        </div>
      ) : (
        <input
          value={selected ?? ""}
          onChange={(e) => onSelect(e.target.value)}
          disabled={graded != null}
          placeholder="输入答案"
          className={`w-full px-3 py-1.5 rounded-lg border text-sm outline-none transition ${
            graded
              ? graded.correct
                ? "bg-emerald-50 border-emerald-300"
                : "bg-rose-50 border-rose-300"
              : "border-slate-200 focus:border-brand-500"
          }`}
        />
      )}

      {graded && (
        <div className="mt-2 text-xs text-slate-500 bg-slate-100 p-2 rounded-lg">
          {graded.correct ? "✓ 正确" : `✗ 正确答案:${graded.correctAnswer}`}
          {graded.analysis && <div className="mt-1">{graded.analysis}</div>}
        </div>
      )}
    </div>
  );
}

/** 从判分结果里按题目序号找对应的 graded 项 */
function findGraded(
  result: GradeResponse | null,
  qIndex: number,
  q: QuizQuestion,
): GradeResponse["graded"][number] | null {
  if (!result) return null;
  const qid = (q as QuizQuestion & { questionId?: number }).questionId ?? qIndex;
  return result.graded.find((g) => g.questionId === qid || g.questionId === qIndex) ?? null;
}
