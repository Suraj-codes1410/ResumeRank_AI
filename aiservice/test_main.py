import os
import pytest
import requests
from fastapi.testclient import TestClient
from unittest.mock import patch, MagicMock

# Set mock token in environment before importing app
os.environ["INTERNAL_SERVICE_TOKEN"] = "test-token-12345"

from main import app

client = TestClient(app)

def test_extract_text_missing_token():
    response = client.post(
        "/internal/extract-text",
        json={"fileUrl": "http://example.com/test.pdf"}
    )
    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid or missing X-Internal-Token header"

def test_extract_text_wrong_token():
    response = client.post(
        "/internal/extract-text",
        json={"fileUrl": "http://example.com/test.pdf"},
        headers={"X-Internal-Token": "wrong-token"}
    )
    assert response.status_code == 401

@patch("main.requests.get")
def test_extract_text_download_404(mock_get):
    mock_response = MagicMock()
    mock_response.status_code = 404
    mock_response.content = b""
    mock_response.headers = {}
    mock_get.return_value = mock_response

    response = client.post(
        "/internal/extract-text",
        json={"fileUrl": "http://example.com/missing.pdf"},
        headers={"X-Internal-Token": "test-token-12345"}
    )
    assert response.status_code == 400
    data = response.json()
    assert data["success"] is False
    assert "Failed to download file" in data["error"]

@patch("main.requests.get")
def test_extract_text_download_exception(mock_get):
    # Raise a RequestException which is caught by requests.RequestException block
    mock_get.side_effect = requests.RequestException("Connection timed out")

    response = client.post(
        "/internal/extract-text",
        json={"fileUrl": "http://example.com/timeout.pdf"},
        headers={"X-Internal-Token": "test-token-12345"}
    )
    assert response.status_code == 400
    data = response.json()
    assert data["success"] is False
    assert "Connection failed while downloading file" in data["error"]

@patch("main.PdfReader")
@patch("main.requests.get")
def test_extract_text_valid_pdf(mock_get, mock_pdf_reader):
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.content = b"fake pdf content"
    mock_response.headers = {"Content-Type": "application/pdf"}
    mock_get.return_value = mock_response

    # Mock pypdf reader pages and text extraction
    mock_page = MagicMock()
    mock_page.extract_text.return_value = "This is a resume for a developer. Contact: candidate@example.com"
    
    mock_reader_instance = MagicMock()
    mock_reader_instance.pages = [mock_page]
    mock_pdf_reader.return_value = mock_reader_instance

    response = client.post(
        "/internal/extract-text",
        json={"fileUrl": "http://example.com/my-resume.pdf"},
        headers={"X-Internal-Token": "test-token-12345"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True
    assert "candidate@example.com" in data["text"]
    assert data["detectedEmail"] == "candidate@example.com"

@patch("main.PdfReader")
@patch("main.requests.get")
def test_extract_text_empty_or_scanned_pdf(mock_get, mock_pdf_reader):
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.content = b"fake pdf content"
    mock_response.headers = {"Content-Type": "application/pdf"}
    mock_get.return_value = mock_response

    # Mock empty text extraction (e.g. scanned image PDF)
    mock_page = MagicMock()
    mock_page.extract_text.return_value = "   " # whitespaces only
    
    mock_reader_instance = MagicMock()
    mock_reader_instance.pages = [mock_page]
    mock_pdf_reader.return_value = mock_reader_instance

    response = client.post(
        "/internal/extract-text",
        json={"fileUrl": "http://example.com/scanned.pdf"},
        headers={"X-Internal-Token": "test-token-12345"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["success"] is False
    assert data["error"] == "No readable text found in the document"

@patch("main.docx.Document")
@patch("main.requests.get")
def test_extract_text_valid_docx(mock_get, mock_docx_document):
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.content = b"fake docx content"
    mock_response.headers = {"Content-Type": "application/vnd.openxmlformats-officedocument.wordprocessingml.document"}
    mock_get.return_value = mock_response

    # Mock python-docx document paragraphs
    mock_p1 = MagicMock()
    mock_p1.text = "Resume text in DOCX"
    mock_p2 = MagicMock()
    mock_p2.text = "Email: docx-candidate@example.com"
    
    mock_doc_instance = MagicMock()
    mock_doc_instance.paragraphs = [mock_p1, mock_p2]
    mock_docx_document.return_value = mock_doc_instance

    response = client.post(
        "/internal/extract-text",
        json={"fileUrl": "http://example.com/resume.docx"},
        headers={"X-Internal-Token": "test-token-12345"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True
    assert "docx-candidate@example.com" in data["text"]
    assert data["detectedEmail"] == "docx-candidate@example.com"


# LLM SCORING TESTS

@patch("main.ChatPromptTemplate.from_messages")
def test_score_resume_success(mock_from_messages):
    mock_chain = MagicMock()
    mock_from_messages.return_value = mock_chain
    mock_chain.__or__.return_value = mock_chain

    from main import CandidateScoreResponse
    mock_chain.invoke.return_value = CandidateScoreResponse(
        overallScore=85,
        skillsScore=90,
        experienceScore=80,
        seniorityScore=85,
        matchedSkills=["Python", "SQL"],
        missingSkills=["AWS"],
        yearsExperienceDetected=5.0,
        summary="Strong candidate with good python skills."
    )

    response = client.post(
        "/internal/score-resume",
        json={
            "resumeText": "Python developer resume text here...",
            "jobTitle": "Python Dev",
            "jobDescription": "Build backend APIs with Python and SQL",
            "requiredSkills": ["Python", "SQL"],
            "niceToHaveSkills": ["AWS"],
            "minYearsExperience": 3
        },
        headers={"X-Internal-Token": "test-token-12345"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True
    score = data["score"]
    assert score["overallScore"] == 85
    assert score["skillsScore"] == 90
    assert score["matchedSkills"] == ["Python", "SQL"]
    assert score["summary"] == "Strong candidate with good python skills."

@patch("main.ChatPromptTemplate.from_messages")
def test_score_resume_invalid_score_validation(mock_from_messages):
    mock_chain = MagicMock()
    mock_from_messages.return_value = mock_chain
    mock_chain.__or__.return_value = mock_chain

    from main import CandidateScoreResponse
    # Return 150 which is out of range (>100)
    mock_chain.invoke.return_value = CandidateScoreResponse(
        overallScore=150,
        skillsScore=90,
        experienceScore=80,
        seniorityScore=85,
        matchedSkills=["Python"],
        missingSkills=[],
        yearsExperienceDetected=3.0,
        summary="Overscored candidate."
    )

    response = client.post(
        "/internal/score-resume",
        json={
            "resumeText": "Python developer resume text here...",
            "jobTitle": "Python Dev",
            "jobDescription": "Build backend APIs with Python and SQL",
            "requiredSkills": ["Python", "SQL"],
            "niceToHaveSkills": ["AWS"],
            "minYearsExperience": 3
        },
        headers={"X-Internal-Token": "test-token-12345"}
    )
    assert response.status_code == 422
    data = response.json()
    assert data["success"] is False
    assert "LLM response failed validation twice" in data["error"]

@patch("main.ChatPromptTemplate.from_messages")
def test_score_resume_exception_handled(mock_from_messages):
    mock_chain = MagicMock()
    mock_from_messages.return_value = mock_chain
    mock_chain.__or__.return_value = mock_chain

    # Mock chain.invoke to raise an exception (like connection or API exception)
    mock_chain.invoke.side_effect = Exception("LLM connection timed out")

    response = client.post(
        "/internal/score-resume",
        json={
            "resumeText": "Python developer resume text here...",
            "jobTitle": "Python Dev",
            "jobDescription": "Build backend APIs with Python and SQL",
            "requiredSkills": ["Python", "SQL"],
            "niceToHaveSkills": ["AWS"],
            "minYearsExperience": 3
        },
        headers={"X-Internal-Token": "test-token-12345"}
    )
    assert response.status_code == 400
    data = response.json()
    assert data["success"] is False
    assert "Failed to generate scores via LLM" in data["error"]

