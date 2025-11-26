from fastapi import FastAPI, BackgroundTasks
from fastapi.responses import HTMLResponse
from fastapi.middleware.cors import CORSMiddleware
import asyncio
import random

app = FastAPI()

# Allow browser access
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# In-memory store for slow column results
slow_results = {}

# Simulate heavy computation
async def compute_slow_value(row_id: int):
    await asyncio.sleep(random.uniform(2, 5))  # simulate expensive work
    slow_results[row_id] = f"SlowValue-{row_id}"

@app.get("/rows")
async def get_rows(background_tasks: BackgroundTasks):
    """
    Returns rows with FAST columns only.
    Slow columns are computed asynchronously.
    """
    rows = []
    for i in range(1, 11):
        rows.append({
            "id": i,
            "name": f"Item {i}",          # fast column
            "price": round(random.random() * 100, 2),  # fast column
            "slow_value": None            # placeholder
        })
        background_tasks.add_task(compute_slow_value, i)

    return rows

@app.get("/slow-value/{row_id}")
async def get_slow_value(row_id: int):
    """
    Returns slow column value if ready.
    """
    return {"id": row_id, "slow_value": slow_results.get(row_id)}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=True)
