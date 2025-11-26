from fastapi import FastAPI
from fastapi.responses import StreamingResponse, HTMLResponse
import asyncio
import json
from datetime import datetime

app = FastAPI()

@app.get("/", response_class=HTMLResponse)
async def home():
    with open("templates/index.html", encoding="utf-8") as f:
        return f.read()

async def stream_rows():
    print(f"{datetime.now().isoformat()} - Request received", flush=True)
    try:
        # 1️⃣ Send fast columns immediately
        fast_rows = [
            {"id": i, "name": f"Item {i}", "price": i * 10}
            for i in range(1, 6)
        ]
        yield f"event: fast\ndata: {json.dumps(fast_rows)}\n\n"
        print(f"{datetime.now().isoformat()} - Sent fast attributes", flush=True)

        # Simulate slow computation
        await asyncio.sleep(3)
        print(f"{datetime.now().isoformat()} - Finished slow computation", flush=True)
        # 2️⃣ Send slow columns
        slow_values = [
            {"id": i, "slow_value": f"Computed-{i}"}
            for i in range(1, 6)
        ]
        yield f"event: slow\ndata: {json.dumps(slow_values)}\n\n"
        print(f"{datetime.now().isoformat()} - Sent slow attributes", flush=True)
        # 3️⃣ Send done event
        yield "event: done\ndata: finished\n\n"
        print(f"{datetime.now().isoformat()} - Sent done event", flush=True)
    finally:
        # This runs when the async generator is closed (client disconnect or response finished).
        # Avoid awaiting inside finally because the generator may be closing; just log immediately.
        print(f"{datetime.now().isoformat()} - Request process complete. Closing stream", flush=True)

@app.get("/stream")
async def stream():
    return StreamingResponse(stream_rows(), media_type="text/event-stream")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main_sse:app", host="127.0.0.1", port=8000, reload=True)
    