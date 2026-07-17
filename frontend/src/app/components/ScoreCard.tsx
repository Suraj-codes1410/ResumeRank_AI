"use client";

import React from "react";
import { JetBrains_Mono } from "next/font/google";

const jetbrainsMono = JetBrains_Mono({
  subsets: ["latin"],
  weight: ["400"],
});

export default function ScoreCard() {
  const matchedSkills = ["Python", "SQL", "Data Pipelines", "PySpark"];
  const missingSkills = ["AWS Lambda", "Terraform"];

  return (
    <div className="w-full max-w-md bg-brand-surface border border-brand-border p-6 rounded-xl shadow-2xl relative overflow-hidden transition-all duration-300">
      {/* Card Header */}
      <div className="flex justify-between items-start mb-6">
        <div>
          <div className="text-xs uppercase tracking-wider text-brand-text-secondary mb-1">
            Active Screening
          </div>
          <h3 className="text-lg font-medium text-brand-text-primary">
            Candidate #9842-X
          </h3>
          <p className="text-xs text-brand-text-secondary mt-0.5">
            Senior Data Engineer Role
          </p>
        </div>

        {/* Circular Overall Score */}
        <div className="relative flex items-center justify-center w-16 h-16">
          <svg
            className="w-full h-full transform -rotate-90"
            viewBox="0 0 36 36"
          >
            <path
              className="text-neutral-800"
              strokeWidth="2.5"
              stroke="currentColor"
              fill="none"
              d="M18 2.0845
                a 15.9155 15.9155 0 0 1 0 31.831
                a 15.9155 15.9155 0 0 1 0 -31.831"
            />
            <path
              className="text-brand-accent transition-all duration-500"
              strokeDasharray="87, 100"
              strokeWidth="2.5"
              strokeLinecap="round"
              stroke="currentColor"
              fill="none"
              d="M18 2.0845
                a 15.9155 15.9155 0 0 1 0 31.831
                a 15.9155 15.9155 0 0 1 0 -31.831"
            />
          </svg>
          <div
            className={`absolute text-md font-bold text-brand-accent ${jetbrainsMono.className}`}
          >
            87
          </div>
        </div>
      </div>

      {/* Sub-Score Bars */}
      <div className="space-y-4 mb-6">
        <div>
          <div className="flex justify-between text-xs mb-1.5">
            <span className="text-brand-text-secondary">
              Technical Skill Match
            </span>
            <span className={`text-brand-accent ${jetbrainsMono.className}`}>
              92%
            </span>
          </div>
          <div className="h-1 bg-neutral-800 rounded-full overflow-hidden">
            <div
              className="h-full bg-brand-accent rounded-full"
              style={{ width: "92%" }}
            ></div>
          </div>
        </div>

        <div>
          <div className="flex justify-between text-xs mb-1.5">
            <span className="text-brand-text-secondary">
              Relevant Experience
            </span>
            <span className={`text-brand-accent ${jetbrainsMono.className}`}>
              84%
            </span>
          </div>
          <div className="h-1 bg-neutral-800 rounded-full overflow-hidden">
            <div
              className="h-full bg-brand-accent rounded-full"
              style={{ width: "84%" }}
            ></div>
          </div>
        </div>

        <div>
          <div className="flex justify-between text-xs mb-1.5">
            <span className="text-brand-text-secondary">
              Seniority Alignment
            </span>
            <span className={`text-brand-accent ${jetbrainsMono.className}`}>
              85%
            </span>
          </div>
          <div className="h-1 bg-neutral-800 rounded-full overflow-hidden">
            <div
              className="h-full bg-brand-accent rounded-full"
              style={{ width: "85%" }}
            ></div>
          </div>
        </div>
      </div>

      {/* Matched vs Missing Skills */}
      <div className="space-y-3 pt-4 border-t border-brand-border/60">
        <div>
          <div className="text-xs text-brand-text-secondary mb-2">
            Matched Skills
          </div>
          <div className="flex flex-wrap gap-1.5">
            {matchedSkills.map((skill) => (
              <span
                key={skill}
                className={`text-[10px] uppercase font-bold tracking-wider px-2 py-1 rounded bg-brand-accent-secondary/10 border border-brand-accent-secondary/30 text-brand-accent-secondary ${jetbrainsMono.className}`}
              >
                {skill}
              </span>
            ))}
          </div>
        </div>

        <div>
          <div className="text-xs text-brand-text-secondary mb-2">
            Missing Skills
          </div>
          <div className="flex flex-wrap gap-1.5">
            {missingSkills.map((skill) => (
              <span
                key={skill}
                className={`text-[10px] uppercase font-bold tracking-wider px-2 py-1 rounded bg-neutral-800/40 border border-neutral-700/40 text-brand-text-secondary/70 ${jetbrainsMono.className}`}
              >
                {skill}
              </span>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
