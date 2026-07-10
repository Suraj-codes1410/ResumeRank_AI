'use client';

import React, { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { Fraunces, Inter } from 'next/font/google';
import ScoreCard from './components/ScoreCard';
import FeatureCard from './components/FeatureCard';

// Load Google Fonts
const fraunces = Fraunces({
  subsets: ['latin'],
  display: 'swap',
  weight: ['400', '500'],
  variable: '--font-fraunces',
});

const inter = Inter({
  subsets: ['latin'],
  display: 'swap',
  weight: ['400'],
  variable: '--font-inter',
});

export default function LandingPage() {
  const [isFeaturesVisible, setIsFeaturesVisible] = useState(false);
  const [isHeroLoaded, setIsHeroLoaded] = useState(false);
  const featuresRef = useRef<HTMLDivElement>(null);

  // Soft entrance trigger on mount for Hero
  useEffect(() => {
    setIsHeroLoaded(true);
  }, []);

  // Slide-up trigger on scroll for Feature Cards
  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsFeaturesVisible(true);
          observer.unobserve(entry.target);
        }
      },
      { threshold: 0.15 }
    );

    if (featuresRef.current) {
      observer.observe(featuresRef.current);
    }

    return () => {
      observer.disconnect();
    };
  }, []);

  // Icons definitions (Tabler-style outline SVGs with strokeWidth 2)
  const parseIcon = (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      className="w-6 h-6"
      viewBox="0 0 24 24"
      strokeWidth="2"
      stroke="currentColor"
      fill="none"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path stroke="none" d="M0 0h24v24H0z" fill="none" />
      <path d="M14 3v4a1 1 0 0 0 1 1h4" />
      <path d="M17 21h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v11a2 2 0 0 1 -2 2z" />
      <line x1="9" y1="12" x2="15" y2="12" />
      <line x1="9" y1="16" x2="13" y2="16" />
    </svg>
  );

  const scoringIcon = (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      className="w-6 h-6"
      viewBox="0 0 24 24"
      strokeWidth="2"
      stroke="currentColor"
      fill="none"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path stroke="none" d="M0 0h24v24H0z" fill="none" />
      <circle cx="12" cy="12" r="9" />
      <path d="M9 12l2 2l4 -4" />
    </svg>
  );

  const shortlistIcon = (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      className="w-6 h-6"
      viewBox="0 0 24 24"
      strokeWidth="2"
      stroke="currentColor"
      fill="none"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path stroke="none" d="M0 0h24v24H0z" fill="none" />
      <line x1="9" y1="6" x2="20" y2="6" />
      <line x1="9" y1="12" x2="20" y2="12" />
      <line x1="9" y1="18" x2="20" y2="18" />
      <line x1="5" y1="6" x2="5" y2="6.01" />
      <line x1="5" y1="12" x2="5" y2="12.01" />
      <line x1="5" y1="18" x2="5" y2="18.01" />
    </svg>
  );

  return (
    <div
      className={`min-h-screen bg-brand-bg text-brand-text-primary flex flex-col font-sans antialiased ${inter.variable} ${fraunces.variable}`}
    >
      {/* 1. Navigation Bar */}
      <nav className="w-full max-w-7xl mx-auto px-6 py-6 flex justify-between items-center border-b border-brand-border/40">
        <Link href="/" className={`text-xl font-bold tracking-tight ${fraunces.className}`}>
          ResumeRank
        </Link>
        
        <div className="hidden md:flex gap-8 text-sm text-brand-text-secondary">
          <Link href="#product" className="hover:text-brand-text-primary transition-colors duration-200">
            Product
          </Link>
          <Link href="#how-it-works" className="hover:text-brand-text-primary transition-colors duration-200">
            How it works
          </Link>
          <Link href="#pricing" className="hover:text-brand-text-primary transition-colors duration-200">
            Pricing
          </Link>
        </div>

        <Link
          href="/signup"
          className="text-xs uppercase tracking-wider font-semibold border border-brand-accent px-4 py-2 text-brand-accent hover:bg-brand-accent/10 transition-all duration-200"
          style={{ borderRadius: 'var(--radius, 6px)' }}
        >
          Get started
        </Link>
      </nav>

      {/* 2 & 3. Hero Section & Signature Element */}
      <main className="flex-grow w-full max-w-7xl mx-auto px-6 py-16 md:py-24 grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
        {/* Left Side: Hero Copy */}
        <div
          className={`lg:col-span-7 space-y-6 transition-all duration-700 ease-out transform ${
            isHeroLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'
          }`}
        >
          {/* Eyebrow Pill */}
          <div className="inline-block border border-brand-border bg-brand-surface/40 px-3.5 py-1 text-xs font-medium tracking-wide text-brand-text-secondary rounded-full">
            For recruiting teams
          </div>

          {/* Headline */}
          <h1 className={`text-4xl sm:text-5xl lg:text-6xl font-medium tracking-tight text-brand-text-primary leading-tight ${fraunces.className}`}>
            Identify the right hire, faster, with explainable AI candidate scoring.
          </h1>

          {/* Supporting Copy */}
          <p className="text-md sm:text-lg leading-relaxed text-brand-text-secondary max-w-2xl">
            Automatically parse resumes, evaluate alignment against target criteria, and review transparent scoring breakdowns that trust human oversight first.
          </p>

          {/* Action CTAs */}
          <div className="flex flex-col sm:flex-row gap-4 pt-4">
            <Link
              href="/signup"
              className="inline-flex justify-center items-center border border-brand-accent px-6 py-3 text-sm font-semibold tracking-wide text-brand-accent hover:bg-brand-accent/10 transition-all duration-200"
              style={{ borderRadius: 'var(--radius, 6px)' }}
            >
              Get started
            </Link>
            <Link
              href="#product"
              className="inline-flex justify-center items-center border border-brand-border px-6 py-3 text-sm font-semibold tracking-wide text-brand-text-secondary hover:text-brand-text-primary hover:border-brand-border/80 transition-all duration-200"
              style={{ borderRadius: 'var(--radius, 6px)' }}
            >
              See a sample scorecard
            </Link>
          </div>
        </div>

        {/* Right Side: Signature Element Card */}
        <div
          className={`lg:col-span-5 flex justify-center lg:justify-end transition-all duration-1000 ease-out transform delay-300 ${
            isHeroLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'
          }`}
        >
          <ScoreCard />
        </div>
      </main>

      {/* 4. Below the fold: Feature cards */}
      <section
        id="product"
        ref={featuresRef}
        className="w-full max-w-7xl mx-auto px-6 py-20 border-t border-brand-border/40"
      >
        <div className="mb-12">
          <h2 className={`text-2xl sm:text-3xl font-medium text-brand-text-primary ${fraunces.className}`}>
            Built for structured evaluation
          </h2>
          <p className="text-sm text-brand-text-secondary mt-2">
            Screen pipeline workloads without losing qualitative control.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <FeatureCard
            icon={parseIcon}
            title="Structure Extraction"
            description="Extract text directly from PDF and DOCX files into formatted candidate fields automatically."
            className={`transition-all duration-700 delay-100 transform ${
              isFeaturesVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-8'
            }`}
          />
          <FeatureCard
            icon={scoringIcon}
            title="Explainable Scoring"
            description="View transparent criteria scoring breakdowns instead of opaque, black-box recommendations."
            className={`transition-all duration-700 delay-200 transform ${
              isFeaturesVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-8'
            }`}
          />
          <FeatureCard
            icon={shortlistIcon}
            title="Ranked Pipelines"
            description="Order candidate rosters instantly by match scores to highlight high-priority matches."
            className={`transition-all duration-700 delay-300 transform ${
              isFeaturesVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-8'
            }`}
          />
        </div>
      </section>
    </div>
  );
}
