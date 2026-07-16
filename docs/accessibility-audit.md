# Frontend Accessibility Audit

This document compiles the findings and verification results of a comprehensive Web Content Accessibility Guidelines (WCAG 2.1 AA) compliance audit performed on the ResumeRank React/Next.js frontend.

---

## 1. Focus Indicator Standard Alignment

### The Problem with Color-Only Border Changes
We audited inputs using the styling class `focus:outline-none focus:border-brand-accent`. While this constitutes a visual change, it **does not** satisfy the `2px outline + 2px offset` design token specification for the following reasons:
1. **Low Visual Prominence**: A 1px border color change is extremely subtle (especially in dark mode on high-resolution screens) and is easily missed by users with low vision or cognitive impairments.
2. **Contrast Failures**: The contrast change between the default border color and the focused border color does not meet the minimum 3.0:1 contrast ratio change requirements without an outline wrapper.
3. **Inconsistency**: Other interactive components (like links, checkboxes, and buttons) either lack focus indicators entirely or default to unstyled browser outlines.

### Agreed Focus Ring Standard
Across the entire application, every interactive element consistently implements the following focus style on `:focus-visible`:
```css
focus-visible:ring-2 focus-visible:ring-brand-accent focus-visible:ring-offset-2 focus-visible:ring-offset-brand-bg focus-visible:outline-none
```
* **Status**: **IMPLEMENTED & VERIFIED** across all inputs, textareas, selects, checkboxes, buttons, and links.
* **Browser Support & Fallback**: `:has(:focus-visible)` is used to style the container of the absolute-positioned file input. This is fully supported in evergreen browsers (Chrome 105+, Edge 105+, Safari 15.4+, Firefox 121+). In legacy unsupported browsers, the focus ring degrades gracefully (focus remains functional, but no container border ring is displayed).

---

## 2. Complete Audit Findings & Status

### Category A: Keyboard Navigation Gaps

Interactive flows must be fully executable using only `Tab`, `Shift+Tab`, `Enter`, `Space`, and `Esc`.

| File & Line Reference | Element / Code | Issue Description | Status | Resolution |
| :--- | :--- | :--- | :--- | :--- |
| [job-postings/[id]/page.tsx:L1246](file:///C:/Users/Suraj/OneDrive/Desktop/ResumeRank/ResumeRank_AI/frontend/src/app/job-postings/%5Bid%5D/page.tsx#L1246) | `<div onClick={() => router.push('/candidates/...')} ...>` | The candidate row is clickable but rendered as a non-semantic, non-tabbable `div` container. Keyboard users could not navigate to detailed scorecard views. | **FIXED** | Wrapped the candidate name text inside a semantic Next.js `<Link>` pointing to `/candidates/${candidate.id}`, making it natively focusable and keyboard-accessible. |

---

### Category B: Focus Indicator Gaps (Non-compliant outline/rings)

Interactive elements that either lack focus rings completely or use the non-compliant border-color-only focus state.

* **Navigation Links**: **FIXED**. Standard focus rings applied on focus-visible to redirect links, breadcrumbs, candidate details navigation, and external file URLs.
* **Form Inputs & Selects**: **FIXED**. Capped standard focus ring classes on all input fields, textareas, and select components.
* **Checkboxes**: **FIXED**. Upgraded checkbox styling classes to use `focus-visible:ring-offset-2` constraint rings.
* **Buttons**: **FIXED**. Standard focus rings applied to all form submit/cancel actions, tabs, filters, and audit logs timeline.
* **Dropzone input**: **FIXED**. Added container border highlighting via `:has(:focus-visible)` when the inner file input gets focused.

---

### Category C: Modal / Dialog Focus Trapping Gaps

| File & Line Reference | Modal Overlay / Window | Issue Description | Status | Resolution |
| :--- | :--- | :--- | :--- | :--- |
| [job-postings/[id]/page.tsx:L1343](file:///C:/Users/Suraj/OneDrive/Desktop/ResumeRank/ResumeRank_AI/frontend/src/app/job-postings/%5Bid%5D/page.tsx#L1343) | **Archive Confirmation Modal** | Custom React modal overlay. Had no keyboard focus trap (tab escaped to underlying layout), no listener to close on `Esc`, and lacked semantic markup attributes. | **FIXED** | Implemented a focus-trap `useEffect` hook, wired a keydown listener for `Esc` to close, and added `role="dialog"`, `aria-modal="true"`, and `aria-labelledby="archive-modal-title"`. |
| [job-postings/[id]/page.tsx:L499](file:///C:/Users/Suraj/OneDrive/Desktop/ResumeRank/ResumeRank_AI/frontend/src/app/job-postings/%5Bid%5D/page.tsx#L499) | **Bulk Reject Action** | Calls native `window.confirm()`. | **COMPLIANT** | Handled natively by browser client. |

---

### Category D: ARIA and State Labels Gaps

| File & Line Reference | Element / Action | Issue Description | Status | Resolution |
| :--- | :--- | :--- | :--- | :--- |
| [candidates/[id]/page.tsx:L501](file:///C:/Users/Suraj/OneDrive/Desktop/ResumeRank/ResumeRank_AI/frontend/src/app/candidates/%5Bid%5D/page.tsx#L501) | `<button onClick={() => setIsHistoryOpen(!isHistoryOpen)} ...>` | Disclosure header button for the pipeline log timeline. It lacked `aria-expanded` attributes. | **FIXED** | Added `aria-expanded={isHistoryOpen}` and `aria-controls="status-history-content"` to the trigger button. |

---

### Category E: Form Input Label Gaps

Form inputs that rely solely on placeholders to indicate context are invisible to screen readers operating in form fields modes.

* **Search Candidates**: **FIXED** (Already had label visually matched; verified for focus).
* **Skill Filter**: **FIXED**. Added `<label htmlFor="filter-skill-input" className="sr-only">Filter by skill</label>`.
* **Minimum Score**: **FIXED**. Added `<label htmlFor="filter-min-score-input" className="sr-only">Minimum score</label>`.
* **Bulk Status Dropdown**: **FIXED**. Added `<label htmlFor="bulk-status-select" className="sr-only">Bulk change status</label>`.
* **Resume Upload Zone**: **FIXED**. Added `<label htmlFor="resume-file-input" className="sr-only">Choose PDF or DOCX files to upload</label>`.

---

## 3. Automated Test Verification

A dedicated Vitest integration test has been added to [job-posting-detail.test.tsx](file:///C:/Users/Suraj/OneDrive/Desktop/ResumeRank/ResumeRank_AI/frontend/src/app/job-postings/%5Bid%5D/job-posting-detail.test.tsx):
* **Test Case**: `supports full keyboard Tab/Space/Enter flow for candidate selection and bulk status rejection`
* **Test Outcome**: **PASSED** (Total suite run: 28/28 passing tests).
