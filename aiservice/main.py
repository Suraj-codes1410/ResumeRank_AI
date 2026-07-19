# FastAPI AI Service entry point for ResumeRank
import io
import json
import logging
import os
import re
import requests
from typing import Optional
from fastapi import FastAPI, Depends, Header, HTTPException, status, BackgroundTasks
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from pypdf import PdfReader
import docx
from dotenv import load_dotenv
from pydantic import Field
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate

# Load environment variables
load_dotenv()

app = FastAPI(title="ResumeRank AI Text Extraction Service")


# Authentication Dependency
async def verify_internal_token(
    x_internal_token: Optional[str] = Header(None, alias="X-Internal-Token")
):
    expected_token = os.getenv("INTERNAL_SERVICE_TOKEN")
    if not expected_token:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Server security configuration missing",
        )
    if not x_internal_token or x_internal_token != expected_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing X-Internal-Token header",
        )
    return x_internal_token


class ExtractTextRequest(BaseModel):
    fileUrl: str


def extract_email(text: str) -> Optional[str]:
    email_pattern = r"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"
    match = re.search(email_pattern, text)
    return match.group(0) if match else None


async def run_extraction(file_url: str) -> dict:
    try:
        response = requests.get(file_url, timeout=15)
        if response.status_code != 200:
            return {
                "success": False,
                "error": f"Failed to download file. Status code: {response.status_code}",
                "status_code": 400,
            }
        file_content = response.content
    except requests.RequestException as e:
        return {
            "success": False,
            "error": f"Connection failed while downloading file: {str(e)}",
            "status_code": 400,
        }

    content_type = response.headers.get("Content-Type", "").lower()
    url_path = file_url.split("?")[0].lower()

    is_pdf = "application/pdf" in content_type or url_path.endswith(".pdf")
    is_docx = (
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        in content_type
        or "application/msword" in content_type
        or url_path.endswith(".docx")
    )

    text = ""

    try:
        if is_pdf:
            pdf_file = io.BytesIO(file_content)
            reader = PdfReader(pdf_file)
            extracted_pages = []
            for page in reader.pages:
                page_text = page.extract_text()
                if page_text:
                    extracted_pages.append(page_text)
            text = "\n".join(extracted_pages)
        elif is_docx:
            doc_file = io.BytesIO(file_content)
            doc = docx.Document(doc_file)
            extracted_paragraphs = [p.text for p in doc.paragraphs if p.text]
            text = "\n".join(extracted_paragraphs)
        else:
            return {
                "success": False,
                "error": "Unsupported file format. Only PDF and DOCX files are allowed.",
                "status_code": 400,
            }
    except Exception as e:
        return {
            "success": False,
            "error": f"Failed to extract text from document: {str(e)}",
            "status_code": 400,
        }

    text_stripped = text.strip()
    if not text_stripped or len(text_stripped) < 10:
        return {
            "success": False,
            "error": "No readable text found in the document",
            "status_code": 200,
        }

    detected_email = extract_email(text_stripped)

    return {"success": True, "text": text_stripped, "detectedEmail": detected_email}


@app.post("/internal/extract-text", dependencies=[Depends(verify_internal_token)])
async def extract_text(request: ExtractTextRequest):
    result = await run_extraction(request.fileUrl)
    if not result.get("success", False):
        status_code = result.pop("status_code", 400)
        return JSONResponse(status_code=status_code, content=result)
    return result


# LLM SCORING SCHEMAS & LOGIC


class ScoreResumeRequest(BaseModel):
    resumeText: str
    jobTitle: str
    jobDescription: str
    requiredSkills: list[str]
    niceToHaveSkills: list[str]
    minYearsExperience: Optional[float] = None


class CandidateScoreResponse(BaseModel):
    overallScore: int = Field(..., description="Overall score between 0 and 100")
    skillsScore: int = Field(
        ..., description="Skills alignment score between 0 and 100"
    )
    experienceScore: int = Field(
        ..., description="Experience alignment score between 0 and 100"
    )
    seniorityScore: int = Field(
        ..., description="Seniority alignment score between 0 and 100"
    )
    matchedSkills: list[str] = Field(
        ...,
        description="List of skills that the candidate has which match the job requirements",
    )
    missingSkills: list[str] = Field(
        ...,
        description="List of required or nice to have skills that are missing in the candidate's resume",
    )
    yearsExperienceDetected: Optional[float] = Field(
        None,
        description="The total number of years of experience detected in the resume",
    )
    summary: str = Field(
        ...,
        description="A 1-2 sentence concise summary explaining the scores and the candidate's suitability",
    )


def validate_score(score: CandidateScoreResponse) -> bool:
    for s in [
        score.overallScore,
        score.skillsScore,
        score.experienceScore,
        score.seniorityScore,
    ]:
        if s < 0 or s > 100:
            return False
    if not isinstance(score.matchedSkills, list) or not all(
        isinstance(x, str) for x in score.matchedSkills
    ):
        return False
    if not isinstance(score.missingSkills, list) or not all(
        isinstance(x, str) for x in score.missingSkills
    ):
        return False
    if not score.summary or not score.summary.strip():
        return False
    return True


def get_llm():
    openai_key = os.getenv("OPENAI_API_KEY")
    openrouter_key = os.getenv("OPENROUTER_API_KEY")

    if openai_key:
        model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
        return ChatOpenAI(
            openai_api_key=openai_key,
            model_name=model,
            temperature=0.0,
            max_tokens=2000,
        )
    elif openrouter_key:
        model = os.getenv("OPENROUTER_MODEL", "google/gemini-2.5-flash")
        return ChatOpenAI(
            openai_api_key=openrouter_key,
            openai_api_base="https://openrouter.ai/api/v1",
            model_name=model,
            temperature=0.0,
            max_tokens=1024,
        )
    else:
        return ChatOpenAI(
            openai_api_key="dummy_key",
            model_name="gpt-4o-mini",
            temperature=0.0,
            max_tokens=2000,
        )


SYSTEM_PROMPT = """You are an expert technical recruiter analyzing a candidate's resume for a specific job posting.
Your task is to evaluate the candidate's alignment with the job details, identify matched/missing skills, estimate years of experience, and score them on four categories (overall, skills, experience, seniority) each on a scale of 0 to 100.

Job Details:
- Title: {jobTitle}
- Description: {jobDescription}
- Required Skills: {requiredSkills}
- Nice-to-Have Skills: {niceToHaveSkills}
- Minimum Years of Experience Required: {minYearsExperience}

Evaluate this Candidate Resume:
{resumeText}

You MUST respond with ONLY a valid JSON object (no markdown, no extra text) in exactly this format:
{{
  "overallScore": <int 0-100>,
  "skillsScore": <int 0-100>,
  "experienceScore": <int 0-100>,
  "seniorityScore": <int 0-100>,
  "matchedSkills": ["skill1", "skill2"],
  "missingSkills": ["skill1", "skill2"],
  "yearsExperienceDetected": <float or null>,
  "summary": "1-2 sentence summary of the candidate's suitability"
}}
"""


def parse_score_json(text: str) -> Optional[CandidateScoreResponse]:
    """Parse LLM text response into a CandidateScoreResponse."""
    cleaned = text.strip()
    # Strip markdown code fences if present
    if cleaned.startswith("```"):
        lines = cleaned.split("\n")
        lines = [line for line in lines if not line.strip().startswith("```")]
        cleaned = "\n".join(lines).strip()
    try:
        data = json.loads(cleaned)
        return CandidateScoreResponse(**data)
    except (json.JSONDecodeError, Exception):
        return None


async def run_scoring(
    resume_text: str,
    job_title: str,
    job_description: str,
    required_skills: list[str],
    nice_to_have_skills: list[str],
    min_years_exp: Optional[float],
) -> dict:
    try:
        llm = get_llm()

        prompt = ChatPromptTemplate.from_messages(
            [
                ("system", SYSTEM_PROMPT),
            ]
        )
        chain = prompt | llm

        invoke_params = {
            "jobTitle": job_title,
            "jobDescription": job_description,
            "requiredSkills": ", ".join(required_skills),
            "niceToHaveSkills": ", ".join(nice_to_have_skills),
            "minYearsExperience": (
                min_years_exp if min_years_exp is not None else "None"
            ),
            "resumeText": resume_text,
        }

        ai_message = chain.invoke(invoke_params)
        result = parse_score_json(ai_message.content)

        if result and validate_score(result):
            return {"success": True, "score": result.model_dump()}

        # Retry once if parsing or validation failed
        ai_message = chain.invoke(invoke_params)
        result = parse_score_json(ai_message.content)

        if result and validate_score(result):
            return {"success": True, "score": result.model_dump()}
        else:
            return {
                "success": False,
                "error": "LLM response failed validation twice.",
                "status_code": 422,
            }

    except Exception as e:
        return {
            "success": False,
            "error": f"Failed to generate scores via LLM: {str(e)}",
            "status_code": 400,
        }


@app.post("/internal/score-resume", dependencies=[Depends(verify_internal_token)])
async def score_resume(request: ScoreResumeRequest):
    result = await run_scoring(
        resume_text=request.resumeText,
        job_title=request.jobTitle,
        job_description=request.jobDescription,
        required_skills=request.requiredSkills,
        nice_to_have_skills=request.niceToHaveSkills,
        min_years_exp=request.minYearsExperience,
    )
    if not result.get("success", False):
        status_code = result.pop("status_code", 400)
        return JSONResponse(status_code=status_code, content=result)
    return result


# ORCHESTRATION LAYER


class ProcessResumeRequest(BaseModel):
    candidateId: str
    fileUrl: str
    jobTitle: str
    jobDescription: str
    requiredSkills: list[str]
    niceToHaveSkills: list[str]
    minYearsExperience: Optional[float] = None


logger = logging.getLogger("uvicorn.error")


async def send_webhook_callback(url: str, payload: dict, headers: dict):
    try:
        # Send HTTP POST call to Spring Boot
        response = requests.post(url, json=payload, headers=headers, timeout=15)
        if response.status_code not in [200, 201, 202, 204]:
            logger.error(
                f"Webhook callback to Spring Boot failed with status: {response.status_code}. Response: {response.text}"
            )
        else:
            logger.info(
                f"Webhook callback sent successfully. Status: {response.status_code}"
            )
    except Exception as e:
        logger.error(f"Failed to connect to Spring Boot webhook at {url}: {str(e)}")


async def background_process_resume(
    candidate_id: str,
    file_url: str,
    job_title: str,
    job_description: str,
    required_skills: list[str],
    nice_to_have_skills: list[str],
    min_years_exp: Optional[float],
):
    webhook_url = os.getenv(
        "SPRING_WEBHOOK_URL", "http://localhost:8081/api/internal/ai-webhook"
    )
    internal_token = os.getenv("INTERNAL_SERVICE_TOKEN")

    headers = {"Content-Type": "application/json", "X-Internal-Token": internal_token}

    # 1. Run extraction
    extraction_result = await run_extraction(file_url)
    if not extraction_result.get("success", False):
        error_msg = extraction_result.get("error", "Unknown extraction error")
        payload = {
            "candidateId": candidate_id,
            "success": False,
            "error": f"Extraction failed: {error_msg}",
        }
        await send_webhook_callback(webhook_url, payload, headers)
        return

    extracted_text = extraction_result.get("text")

    # 2. Run scoring
    scoring_result = await run_scoring(
        resume_text=extracted_text,
        job_title=job_title,
        job_description=job_description,
        required_skills=required_skills,
        nice_to_have_skills=nice_to_have_skills,
        min_years_exp=min_years_exp,
    )

    if not scoring_result.get("success", False):
        error_msg = scoring_result.get("error", "Unknown scoring error")
        payload = {
            "candidateId": candidate_id,
            "success": False,
            "error": f"Scoring failed: {error_msg}",
        }
        await send_webhook_callback(webhook_url, payload, headers)
        return

    # 3. Success callback
    payload = {
        "candidateId": candidate_id,
        "success": True,
        "score": scoring_result.get("score"),
    }
    await send_webhook_callback(webhook_url, payload, headers)


@app.post(
    "/internal/process-resume",
    status_code=status.HTTP_202_ACCEPTED,
    dependencies=[Depends(verify_internal_token)],
)
async def process_resume(
    request: ProcessResumeRequest, background_tasks: BackgroundTasks
):
    background_tasks.add_task(
        background_process_resume,
        request.candidateId,
        request.fileUrl,
        request.jobTitle,
        request.jobDescription,
        request.requiredSkills,
        request.niceToHaveSkills,
        request.minYearsExperience,
    )
    return {"message": "Resume processing accepted"}
