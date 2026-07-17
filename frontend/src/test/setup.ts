import "@testing-library/jest-dom";
import { vi } from "vitest";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useSearchParams: vi.fn(() => ({
    get: () => null,
  })),
  useRouter: vi.fn(() => ({
    push: vi.fn(),
    replace: vi.fn(),
    prefetch: vi.fn(),
  })),
}));

// Mock next/font/google
vi.mock("next/font/google", () => ({
  Fraunces: () => ({
    className: "mock-fraunces",
    variable: "--font-fraunces",
    style: { fontFamily: "Fraunces" },
  }),
  Inter: () => ({
    className: "mock-inter",
    variable: "--font-inter",
    style: { fontFamily: "Inter" },
  }),
  JetBrains_Mono: () => ({
    className: "mock-mono",
    variable: "--font-mono",
    style: { fontFamily: "JetBrains_Mono" },
  }),
}));
