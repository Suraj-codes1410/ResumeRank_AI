// Next.js Landing Page Entry for ResumeRank
import React from "react";
import type { Metadata } from "next";
import LandingPageClient from "./landing-page-client";

export const metadata: Metadata = {
  title: "ResumeRank - Explainable AI Candidate Screening & Scoring",
  description:
    "Automatically parse resumes and evaluate candidates against structured criteria with transparent, explainable scoring. Try ResumeRank for free today.",
  openGraph: {
    title: "ResumeRank - Explainable AI Candidate Screening & Scoring",
    description:
      "Automatically parse resumes and evaluate candidates against structured criteria with transparent, explainable scoring. Try ResumeRank for free today.",
    url: "https://resumerank.ai/",
    type: "website",
    siteName: "ResumeRank",
    images: [
      {
        url: "https://resumerank.ai/opengraph-image",
        width: 1200,
        height: 630,
        alt: "ResumeRank - Explainable AI Candidate Screening",
      },
    ],
  },
  twitter: {
    card: "summary_large_image",
    title: "ResumeRank - Explainable AI Candidate Screening & Scoring",
    description:
      "Automatically parse resumes and evaluate candidates against structured criteria with transparent, explainable scoring. Try ResumeRank for free today.",
    images: ["https://resumerank.ai/opengraph-image"],
  },
};

export default function LandingPage() {
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: "ResumeRank",
    applicationCategory: "BusinessApplication",
    operatingSystem: "Web",
    offers: {
      "@type": "Offer",
      price: "0",
      priceCurrency: "USD",
    },
  };

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <LandingPageClient />
    </>
  );
}
