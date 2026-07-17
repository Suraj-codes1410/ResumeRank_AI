"use client";

import React from "react";

interface FeatureCardProps {
  icon: React.ReactNode;
  title: string;
  description: string;
  className?: string;
}

export default function FeatureCard({
  icon,
  title,
  description,
  className = "",
}: FeatureCardProps) {
  return (
    <div
      className={`bg-brand-surface border border-brand-border p-6 rounded-xl transition-all duration-300 hover:border-brand-border/80 flex flex-col items-start ${className}`}
    >
      <div className="w-10 h-10 flex items-center justify-center rounded-lg bg-neutral-900 border border-brand-border mb-4 text-brand-accent">
        {icon}
      </div>
      <h3 className="text-md font-medium text-brand-text-primary mb-2">
        {title}
      </h3>
      <p className="text-sm leading-relaxed text-brand-text-secondary">
        {description}
      </p>
    </div>
  );
}
