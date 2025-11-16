# OCR Training Automation

## Pornire server Python (FastAPI)

Pentru a porni serverul API care rulează modelul OCR:

```powershell
uvicorn use_fine_tuned_model:app --reload --host 0.0.0.0 --port 8000
```

Sau doar pe localhost:

```powershell
uvicorn use_fine_tuned_model:app --reload
```

Serverul va fi disponibil la `http://localhost:8000`
Health check: `http://localhost:8000/health`

## Rulare aplicație Java

```powershell
mvn clean compile exec:java -Dexec.mainClass="org.example.Main"
```
