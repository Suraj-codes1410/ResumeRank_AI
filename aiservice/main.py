import io
import os
import re
import requests
from typing import Optional
from fastapi import FastAPI, Depends, Header, HTTPException, status
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
async def verify_internal_token(x_internal_token: Optional[str] = Header(None, alias="X-Internal-Token")):
    expected_token = os.getenv("INTERNAL_SERVICE_TOKEN")
    if not expected_token:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Server security configuration missing"
        )
    if not x_internal_token or x_internal_token != expected_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing X-Internal-Token header"
        )
    return x_internal_token

class ExtractTextRequest(BaseModel):
    fileUrl: str

def extract_email(text: str) -> Optional[str]:
    # Simple email pattern extraction
    email_pattern = r'[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}'
    match = re.search(email_pattern, text)
    return match.group(0) if match else None

@app.post("/internal/extract-text", dependencies=[Depends(verify_internal_token)])
async def extract_text(request: ExtractTextRequest):
    # 1. Download file from URL
    try:
        response = requests.get(request.fileUrl, timeout=15)
        if response.status_code != 200:
            return JSONResponse(
                status_code=400,
                content={
                    "success": False,
                    "error": f"Failed to download file. Status code: {response.status_code}"
                }
            )
        file_content = response.content
    except requests.RequestException as e:
        return JSONResponse(
            status_code=400,
            content={
                "success": False,
                "error": f"Connection failed while downloading file: {str(e)}"
            }
        )

    # 2. Detect file type (Content-Type header or file extension)
    content_type = response.headers.get("Content-Type", "").lower()
    url_path = request.fileUrl.split("?")[0].lower()

    is_pdf = "application/pdf" in content_type or url_path.endswith(".pdf")
    is_docx = (
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" in content_type
        or "application/msword" in content_type
        or url_path.endswith(".docx")
    )

    text = ""

    # 3. Extract text content
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
            return JSONResponse(
                status_code=400,
                content={
                    "success": False,
                    "error": "Unsupported file format. Only PDF and DOCX files are allowed."
                }
            )
    except Exception as e:
        return JSONResponse(
            status_code=400,
            content={
                "success": False,
                "error": f"Failed to extract text from document: {str(e)}"
            }
        )

    # 4. Verify text extraction yielded contents
    text_stripped = text.strip()
    if not text_stripped or len(text_stripped) < 10:
        return JSONResponse(
            status_code=200,
            content={
                "success": False,
                "error": "No readable text found in the document"
            }
        )

    # 5. Extract email and return response
    detected_email = extract_email(text_stripped)

    return {
        "success": True,
        "text": text_stripped,
        "detectedEmail": detected_email
    }


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
    skillsScore: int = Field(..., description="Skills alignment score between 0 and 100")
    experienceScore: int = Field(..., description="Experience alignment score between 0 and 100")
    seniorityScore: int = Field(..., description="Seniority alignment score between 0 and 100")
    matchedSkills: list[str] = Field(..., description="List of skills that the candidate has which match the job requirements")
    missingSkills: list[str] = Field(..., description="List of required or nice to have skills that are missing in the candidate's resume")
    yearsExperienceDetected: Optional[float] = Field(None, description="The total number of years of experience detected in the resume")
    summary: str = Field(..., description="A 1-2 sentence concise summary explaining the scores and the candidate's suitability")

def validate_score(score: CandidateScoreResponse) -> bool:
    for s in [score.overallScore, score.skillsScore, score.experienceScore, score.seniorityScore]:
        # Strictly reject values outside 0-100 to ensure downstream mathematical integrity
        if s < 0 or s > 100:
            return False
    if not isinstance(score.matchedSkills, list) or not all(isinstance(x, str) for x in score.matchedSkills):
        return False
    if not isinstance(score.missingSkills, list) or not all(isinstance(x, str) for x in score.missingSkills):
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
            max_tokens=2000
        )
    elif openrouter_key:
        model = os.getenv("OPENROUTER_MODEL", "google/gemini-2.5-flash")
        return ChatOpenAI(
            openai_api_key=openrouter_key,
            openai_api_base="https://openrouter.ai/api/v1",
            model_name=model,
            temperature=0.0,
            max_tokens=2000
        )
    else:
        # Returns a dummy configuration so that the app starts up without crashing on import
        return ChatOpenAI(
            openai_api_key="dummy_key",
            model_name="gpt-4o-mini",
            temperature=0.0,
            max_tokens=2000
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
"""

RETRY_SYSTEM_PROMPT = """You previously returned a response that failed validation constraints.
We require all scores (overallScore, skillsScore, experienceScore, seniorityScore) to be integers strictly between 0 and 100 inclusive.
matchedSkills and missingSkills must be arrays of strings, and summary must be non-empty (1-2 sentences).

Please re-evaluate and return valid scores strictly within the 0 to 100 range.

Job Details:
- Title: {jobTitle}
- Description: {jobDescription}
- Required Skills: {requiredSkills}
- Nice-to-Have Skills: {niceToHaveSkills}
- Minimum Years of Experience Required: {minYearsExperience}

Candidate Resume:
{resumeText}
"""

@app.post("/internal/score-resume", dependencies=[Depends(verify_internal_token)])
async def score_resume(request: ScoreResumeRequest):
    try:
        llm = get_llm()
        structured_llm = llm.with_structured_output(CandidateScoreResponse)
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", SYSTEM_PROMPT),
        ])
        chain = prompt | structured_llm
        
        # 1. First LLM Call
        result = chain.invoke({
            "jobTitle": request.jobTitle,
            "jobDescription": request.jobDescription,
            "requiredSkills": ", ".join(request.requiredSkills),
            "niceToHaveSkills": ", ".join(request.niceToHaveSkills),
            "minYearsExperience": request.minYearsExperience if request.minYearsExperience is not None else "None",
            "resumeText": request.resumeText
        })
        
        if validate_score(result):
            return {"success": True, "score": result.model_dump()}
            
        # 2. Retry ONCE if first call failed validation
        retry_prompt = ChatPromptTemplate.from_messages([
            ("system", RETRY_SYSTEM_PROMPT),
        ])
        retry_chain = retry_prompt | structured_llm
        
        result = retry_chain.invoke({
            "jobTitle": request.jobTitle,
            "jobDescription": request.jobDescription,
            "requiredSkills": ", ".join(request.requiredSkills),
            "niceToHaveSkills": ", ".join(request.niceToHaveSkills),
            "minYearsExperience": request.minYearsExperience if request.minYearsExperience is not None else "None",
            "resumeText": request.resumeText
        })
        
        if validate_score(result):
            return {"success": True, "score": result.model_dump()}
        else:
            return JSONResponse(
                status_code=422,
                content={
                    "success": False,
                    "error": "LLM response failed validation twice. Clamping bounds violated."
                }
            )
            
    except Exception as e:
        return JSONResponse(
            status_code=400,
            content={
                "success": False,
                "error": f"Failed to generate scores via LLM: {str(e)}"
            }
        )

