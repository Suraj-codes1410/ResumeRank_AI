import { ImageResponse } from "next/og";

export const alt = "ResumeRank - Explainable AI Candidate Screening";
export const size = {
  width: 1200,
  height: 630,
};
export const contentType = "image/png";

export default async function Image() {
  return new ImageResponse(
    <div
      style={{
        background: "#10141A",
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "80px",
        border: "12px solid #C99A52",
        fontFamily: "sans-serif",
      }}
    >
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        {/* Logo Brand Tag */}
        <div
          style={{
            fontSize: "24px",
            fontWeight: "bold",
            color: "#C99A52",
            letterSpacing: "0.1em",
            textTransform: "uppercase",
            marginBottom: "20px",
            border: "2px solid #C99A52",
            padding: "6px 16px",
            borderRadius: "4px",
          }}
        >
          ResumeRank
        </div>

        {/* Heading */}
        <div
          style={{
            fontSize: "52px",
            fontWeight: "bold",
            color: "#E8E6E1",
            textAlign: "center",
            lineHeight: 1.2,
            maxWidth: "900px",
            marginBottom: "24px",
          }}
        >
          Identify the Right Hire, Faster, with Explainable AI Candidate
          Scoring.
        </div>

        {/* Subheading */}
        <div
          style={{
            fontSize: "22px",
            color: "#9A9690",
            textAlign: "center",
            lineHeight: 1.5,
            maxWidth: "800px",
            display: "flex",
          }}
        >
          Parse resumes, verify alignment against target criteria, and review
          transparent scoring breakdowns with explainable AI.
        </div>
      </div>

      {/* Decorative Badge Footer */}
      <div
        style={{
          display: "flex",
          marginTop: "48px",
          gap: "16px",
        }}
      >
        <div
          style={{
            fontSize: "14px",
            fontWeight: "semibold",
            color: "#649987",
            background: "rgba(100, 153, 135, 0.1)",
            padding: "6px 16px",
            borderRadius: "100px",
            border: "1px solid rgba(100, 153, 135, 0.3)",
          }}
        >
          Explainable AI
        </div>
        <div
          style={{
            fontSize: "14px",
            fontWeight: "semibold",
            color: "#C99A52",
            background: "rgba(201, 154, 82, 0.1)",
            padding: "6px 16px",
            borderRadius: "100px",
            border: "1px solid rgba(201, 154, 82, 0.3)",
          }}
        >
          Recruiting Automation
        </div>
      </div>
    </div>,
    {
      ...size,
    },
  );
}
