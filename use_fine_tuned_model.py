"""FastAPI micro-service that exposes the fine-tuned GPT model for OCR predictions."""

from __future__ import annotations

import argparse
import base64
import mimetypes
from pathlib import Path
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from openai import OpenAI


client = OpenAI()

FINE_TUNED_MODEL_ID = "ft:gpt-4o-2024-08-06:imiva:transitionalcyrilic-2k:BiUNqPur"
ALLOWED_CHARACTERS_PROMPT = """You are an OCR system, expert in extracting text from images. Return only the character in the green box. You must respond only with one single character. The response must contain only one charactr from the following set: abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZăâîșțĂÂÎȘȚ0123456789.,;:!?-()[]{}'"   Şə̀́̂̄̆̈ΡζθνρχЅІЇЈЌЏАБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЩЪЬЮЯабвгдежзийклмнопрстуфхцчшщъыьюяѐёєѕіїјџѠѡѢѣѦѧѨѩѪѫѮѯѱѲѳѴѵѸѹҪҫӗӡӧԁԑ–—"“"„ꋋꙆꙇꙊꙋꙏꙗꙜꙞꙟ꙼ꚀꚁꚎꚏꚖꚗꟅ"""
USER_PROMPT_FOR_IMAGE = ""
MAX_ATTEMPTS = 3


class ImagePayload(BaseModel):
    data: str
    mime_type: Optional[str] = None


class PredictRequest(BaseModel):
    primary_image: ImagePayload
    context_images: List[ImagePayload] = []
    user_prompt: Optional[str] = None


class PredictResponse(BaseModel):
    prediction: str
    raw_attempts: List[str]
    attempts: int


def _to_data_url(image: ImagePayload, default_mime: str = "image/png") -> str:
    if image.data.startswith("data:"):
        return image.data
    mime = image.mime_type or default_mime
    return f"data:{mime};base64,{image.data}"


def _normalize_prediction(raw_text: str) -> str:
    if not raw_text:
        return ""
    flattened = raw_text.strip().replace("\n", "").replace(" ", "")
    return flattened


def _call_model(image_urls: List[str], user_prompt: str | None) -> str:
    message_content = []
    prompt = user_prompt if user_prompt is not None else USER_PROMPT_FOR_IMAGE
    if prompt:
        message_content.append({"type": "text", "text": prompt})
    for url in image_urls:
        message_content.append({"type": "image_url", "image_url": {"url": url}})

    response = client.chat.completions.create(
        model=FINE_TUNED_MODEL_ID,
        messages=[
            {"role": "system", "content": ALLOWED_CHARACTERS_PROMPT},
            {"role": "user", "content": message_content},
        ],
        temperature=1.0,
        max_tokens=5,
        top_p=1.0,
    )

    if not response.choices or not response.choices[0].message:
        raise RuntimeError("Modelul nu a returnat niciun răspuns.")
    content = response.choices[0].message.content or ""
    return content


def _predict_with_retries(image_urls: List[str], user_prompt: str | None) -> PredictResponse:
    raw_attempts: List[str] = []
    sanitized = ""

    for attempt in range(1, MAX_ATTEMPTS + 1):
        raw = _call_model(image_urls, user_prompt)
        raw_attempts.append(raw)
        sanitized = _normalize_prediction(raw)

        if len(sanitized) == 1:
            return PredictResponse(prediction=sanitized, raw_attempts=raw_attempts, attempts=attempt)

    if sanitized:
        trimmed = sanitized[0]
        return PredictResponse(prediction=trimmed, raw_attempts=raw_attempts, attempts=MAX_ATTEMPTS)

    raise RuntimeError("Modelul nu a putut furniza niciun caracter valid.")


app = FastAPI(title="Fine-tuned OCR Service", version="1.0.0")


@app.get("/health", summary="Health check")
def health_check() -> dict:
    return {"status": "ok"}


@app.post("/predict", response_model=PredictResponse, summary="Predict a single character")
def predict(request: PredictRequest) -> PredictResponse:
    if not request.primary_image or not request.primary_image.data:
        raise HTTPException(status_code=400, detail="Primary image is required.")

    try:
        image_urls = [_to_data_url(request.primary_image)]
        image_urls.extend(_to_data_url(img) for img in request.context_images)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    try:
        response = _predict_with_retries(image_urls, request.user_prompt)
    except RuntimeError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    return response


def encode_image_to_base64_url(image_path_str: str) -> Optional[str]:
    image_path = Path(image_path_str)
    if not image_path.exists():
        return None
    mime_type, _ = mimetypes.guess_type(image_path)
    if mime_type is None:
        return None
    data = image_path.read_bytes()
    return f"data:{mime_type};base64,{base64.b64encode(data).decode('utf-8')}"


def _cli_predict(image_path: Path) -> None:
    data_url = encode_image_to_base64_url(str(image_path))
    if not data_url:
        raise SystemExit("Nu s-a putut citi imaginea specificată.")

    request = PredictRequest(primary_image=ImagePayload(data=data_url))
    response = _predict_with_retries([data_url], request.user_prompt)
    print("Predicție:", response.prediction)
    print("Încercări brute:", response.raw_attempts)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Local predictor pentru testare rapidă.")
    parser.add_argument("image", type=Path, help="Calea către imaginea cu caracterul țintă")
    args = parser.parse_args()
    _cli_predict(args.image)