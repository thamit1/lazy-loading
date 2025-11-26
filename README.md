# Lazy Loading in Data Tables: Two Proof-of-Concept Implementations

## Introduction

When building data-driven user interfaces, you often face a common challenge: some columns in a table are fast to compute (fetched from a database or cache), while others require expensive backend operations (complex calculations, third-party API calls, or aggregations). Users want to see the table *immediately* with the fast columns, but waiting for slow columns to finish can make the entire page feel sluggish.

This project demonstrates two different architectural approaches to solve this problem: **lazy loading**. Both approaches allow the UI to render fast columns instantly and populate slow columns as they become available, giving users a sense of progress and responsiveness while the backend continues working.

## The Problem: Fast Columns and Slow Columns

Imagine a product table with columns like **ID**, **Name**, and **Price** that come from the database instantly, but a **Profit Margin** column that requires real-time pricing calculations or external API calls. Without lazy loading, the user waits for *all* columns, watching a loading spinner. With lazy loading, the table appears in milliseconds, and the user sees the missing column fill in progressively—a much better experience.

## Two Approaches to Lazy Loading

This project includes two fundamentally different implementations of lazy loading:

1. **Two-Request Model with Polling**: The client makes an initial request to get fast columns, then polls the server repeatedly asking "is the slow column ready yet?" until the data appears.

2. **One-Request Streaming Model with Server-Sent Events (SSE)**: The client opens a single, persistent connection, and the server pushes data to the client in stages—first the fast columns, then the slow columns—over the same connection.

Each approach has distinct advantages and trade-offs. Let's explore both in detail.

---

## Approach 1: Two-Request Model (Polling)

### How It Works

In the two-request model, the flow is straightforward:

1. **Initial Request**: The browser fetches the endpoint `/rows`, which immediately returns all rows with *only the fast columns populated*. The slow columns are set to `null`.
2. **Background Computation**: The server schedules background tasks to compute the slow column values asynchronously.
3. **Polling Loop**: The JavaScript client starts a polling interval (e.g., every 1 second) for each row, calling `/slow-value/{id}` to check if the value is ready.
4. **Gradual Updates**: As slow values complete, the client updates the corresponding cells in the table. The polling interval for that row stops.

### Architecture

```
Client                                 Server
  |                                      |
  +------- GET /rows -------->           |
  |                                      | → Returns fast columns
  |  <------ Fast Rows -------+          | → Schedules background tasks
  |                            |         | (slow computation in progress)
  |                            |         |
  +--- GET /slow-value/1 ---->|         |
  |  (poll every 1 sec)        |   ← Not ready yet
  |                            |         |
  +--- GET /slow-value/1 ---->|         |
  |  (poll every 1 sec)        |   ← Not ready yet
  |                            |         |
  +--- GET /slow-value/1 ---->|         |
  |  (poll every 1 sec)        +---- Ready! ------>
  |  (stop polling for 1)              |
  |                                    |
```

### When to Use Two-Request Model

- **Simpler, easier to debug**: Each request/response is independent; no stateful connections.
- **Better browser compatibility**: Polling works in all browsers, including older ones.
- **Clearer error handling**: If one poll fails, the next one retries naturally.
- **Lighter server state**: The server doesn't maintain long-lived connections.
- **Suitable for slower/flaky networks**: Polling is more resilient to timeouts than a streaming connection.

### Tradeoffs

- **Higher latency variance**: With polling every 1 second, you might miss a value by up to 1 second.
- **More network requests**: Each poll is a separate HTTP request, consuming bandwidth and creating server load.
- **Client complexity**: You must manage polling intervals per row and clean them up.
- **Wasted requests**: Many polls return "not ready yet," adding noise to your network traffic.

---

## Approach 2: One-Request Streaming Model (Server-Sent Events)

### How It Works

In the SSE (Server-Sent Events) model, everything happens over a single, persistent connection:

1. **Open Connection**: The browser opens a connection to `/stream`, which returns a text stream with `Content-Type: text/event-stream`.
2. **Receive Fast Columns**: The server immediately sends a `fast` event containing all rows with fast columns populated.
3. **Compute Slow Columns**: The server continues its computation.
4. **Send Slow Columns**: Once slow columns are ready, the server sends a `slow` event with the slow values.
5. **Close Connection**: The server sends a `done` event, and the client closes the connection.

### Architecture

```
Client                              Server
  |                                   |
  +--- GET /stream (SSE) ---------->  |
  |                                   | Sends "fast" event
  |  <---- event: fast -------+       | (fast columns immediately)
  |     [rows with fast data]  |      |
  |                            |      | (server computing slow columns)
  |                            |      |
  |                            |      | Sends "slow" event
  |  <---- event: slow -------+       |
  |     [slow column values]   |      |
  |                            |      |
  |                            |      | Sends "done" event
  |  <---- event: done -------+       |
  |                                   |
  | (client closes connection)        |
  |                                   |
```

### When to Use One-Request Streaming Model

- **Lower latency**: No polling delay; the client receives data as soon as it's ready.
- **Efficient**: A single connection handles all data delivery; no wasted polling requests.
- **Natural flow**: The data pipeline matches the server-side computation order.
- **Real-time feel**: Immediate feedback on fast columns, then progressive updates on slow columns.
- **Reduced server load**: One connection per client instead of many short requests.

### Tradeoffs

- **Connection state**: The server must maintain the connection until the response is complete.
- **Network reliability**: A broken connection requires manual retry logic (EventSource supports auto-reconnect, but you must handle it).
- **Less compatible**: SSE is not supported in older browsers (IE 11 and below), though modern browsers handle it well.
- **Slightly more complex on the server**: You must manage async generators and ensure cleanup on disconnect.

---

## Project Structure

```
lazy-loading/
├── using-two-requests/
│   ├── main.py              # FastAPI server (polling approach)
│   └── index.html           # Client (polls for slow columns)
│
├── using-one-request-sse/
│   ├── main_sse.py          # FastAPI server (SSE streaming)
│   └── templates/
│       └── index.html       # Client (listens to SSE events)
│
└── README.md                # This file
```

---

## Installation and Setup

### Prerequisites

- Python 3.8 or higher
- pip (Python package manager)

### Install Dependencies

Both implementations use FastAPI and Uvicorn. Install them with:

```bash
pip install fastapi uvicorn
```

Alternatively, if you have a `requirements.txt` file:

```bash
pip install -r requirements.txt
```

---

## Running the Two-Request Model (Polling)

### Start the Server

Navigate to the `using-two-requests` directory and run:

```bash
cd using-two-requests
python main.py
```

You should see:

```
INFO:     Uvicorn running on http://127.0.0.1:8000
INFO:     Application startup complete
```

### Open in Browser

Open your web browser and navigate to:

```
http://127.0.0.1:8000
```

The page should serve `index.html` automatically. If not, check your browser console (F12) for errors.

### What You'll See

1. The table appears almost instantly with **ID**, **Name**, **Price** (the fast columns).
2. The **Slow Column** cells show "Loading..." in gray italic text.
3. Over the next 2–5 seconds, the slow values appear one by one as the server finishes computing them and the client's polling picks them up.

---

## Running the One-Request Streaming Model (SSE)

### Start the Server

Navigate to the `using-one-request-sse` directory and run:

```bash
cd using-one-request-sse
python main_sse.py
```

You should see:

```
INFO:     Uvicorn running on http://127.0.0.1:8000
INFO:     Application startup complete
```

### Open in Browser

Open your web browser and navigate to:

```
http://127.0.0.1:8000
```

The page should serve the HTML automatically. If not, ensure `templates/index.html` exists and is in the correct location.

### What You'll See

1. The table appears almost instantly with **ID**, **Name**, **Price** (the fast columns), sent via the `fast` event.
2. The **Slow Column** cells show "Loading..." in gray italic text.
3. After ~3 seconds, all slow values appear at once, sent via the `slow` event.
4. The browser console logs confirm the event sequence: "fast event received," "slow event received," "done event received. Closing SSE connection."

---

## Expected Behavior

### Two-Request Model

- **Timeline**: 
  - `t=0ms`: Page loads, `/rows` request sent.
  - `t=50ms`: Fast columns render.
  - `t=1000ms`: First poll for slow values.
  - `t=2000–5000ms`: Slow values arrive randomly as backend tasks complete; client picks them up on next poll.

- **Network**: Multiple requests to `/slow-value/{id}`.

- **User Experience**: Fast initial render, then gradual filling in of slow columns. The table fills row-by-row and column-by-column as polling succeeds.

### One-Request Streaming Model

- **Timeline**:
  - `t=0ms`: Page loads, `/stream` connection opened.
  - `t=10ms`: `fast` event received; all fast columns render.
  - `t=3000ms`: `slow` event received; all slow columns populate at once.
  - `t=3010ms`: `done` event received; connection closed.

- **Network**: Single connection; two chunked HTTP responses (fast event + slow event).

- **User Experience**: Extremely fast initial render with fast columns, then a single "refresh" as all slow columns appear together. Fewer network round trips.

---

## Comparison Table

| Aspect | Two-Request (Polling) | One-Request (SSE) |
|--------|----------------------|-------------------|
| **Number of Requests** | 1 initial + N polling requests | 1 (persistent connection) |
| **Latency to Fast Columns** | ~50 ms | ~10 ms |
| **Latency to Slow Columns** | 1–6 seconds (depends on poll interval) | ~3 seconds (deterministic) |
| **Total Page Load Time** | ~1–6 seconds | ~3 seconds |
| **Network Efficiency** | Moderate (wasted polling requests) | High (single connection) |
| **Browser Compatibility** | Excellent (all browsers) | Good (IE 11 and below not supported) |
| **Server Complexity** | Low (stateless) | Medium (manages connections) |
| **Error Handling** | Simple (retry on next poll) | Manual retry logic needed |
| **Best For** | Older browsers, flaky networks, simpler logic | Modern browsers, efficiency, predictable timing |

---

## Troubleshooting

### "Port 8000 is already in use"

If port 8000 is occupied, you can run the server on a different port:

```bash
# For polling model
python main.py  # and edit main.py to change port

# For SSE model
python main_sse.py  # and edit main_sse.py to change port
```

Or, use Uvicorn directly:

```bash
uvicorn main:app --port 8001 --host 127.0.0.1
```

### Windows Encoding Issues (UTF-8 vs. ANSI)

On Windows, if you see encoding errors when opening HTML files, ensure the files are saved as UTF-8 (not ANSI). In most editors:

- VS Code: Bottom right → "UTF-8"
- PyCharm: File → File Encoding → UTF-8
- Notepad++: Encoding → Encode in UTF-8 without BOM

### SSE Connection Closes Unexpectedly

**EventSource auto-reconnect**: The browser's EventSource API has built-in reconnection logic. If the connection drops, it will automatically reconnect with exponential backoff (1s, 2s, 4s, etc.). You can see this in the console.

**To disable auto-reconnect** (if you want to handle reconnection yourself), listen for the `error` event:

```javascript
evtSource.addEventListener("error", () => {
    evtSource.close();  // Stop auto-reconnect
    // Implement your own reconnection logic
});
```

### SSE Not Showing in Browser (Blank Page)

1. **Open the browser console** (F12 → Console) to check for JavaScript errors.
2. **Check the server logs**: Ensure the server started without errors.
3. **Check the Network tab** (F12 → Network): Look for the `/stream` request. It should show as a streaming response with a 200 status.
4. **Ensure the HTML file exists**: For SSE, verify that `templates/index.html` is in the correct path relative to `main_sse.py`.

### Polling Not Picking Up Slow Values

1. **Check server logs**: Verify that background tasks are being scheduled.
2. **Increase poll frequency**: In `index.html` (polling version), change the interval from 1000 ms to 500 ms or less (though this increases network load).
3. **Check browser console**: Look for fetch errors.

### CORS Errors (Blocked by Browser)

If you're running the server on one port and loading HTML from another, you may see CORS errors. The polling version includes CORS middleware to allow cross-origin requests. The SSE version does not, since the same origin serves both HTML and API.

---

## Summary and Recommendations

Both lazy-loading approaches solve the same problem but in different ways. Here's when to use each:

**Use the Two-Request (Polling) Model when:**
- You need broad browser compatibility, including older clients.
- Your network is unreliable and you prefer many retryable requests.
- Your backend doesn't support long-lived connections (e.g., serverless functions).
- Simplicity and statelessness on the server are priorities.

**Use the One-Request Streaming (SSE) Model when:**
- You're targeting modern browsers (Chrome, Firefox, Safari, Edge).
- You want minimal network overhead and lower latency.
- Your backend supports long-lived connections.
- You can handle connection lifecycle management.

**In practice**, for most modern web applications, the SSE approach (One-Request Streaming) is the better choice. It's more efficient, faster, and results in a smoother user experience. However, if you're building for broad compatibility or have infrastructure constraints, the polling approach is proven and reliable.

Both implementations in this project are production-ready proof-of-concepts. Feel free to adapt them to your specific needs, add error handling, implement caching, or combine elements from both approaches.

---

## Further Reading and Resources

- [MDN: Server-Sent Events (EventSource)](https://developer.mozilla.org/en-US/docs/Web/API/EventSource)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
- [HTTP Streaming and Chunked Transfer Encoding](https://en.wikipedia.org/wiki/Chunked_transfer_encoding)
- [Web Performance: Perceived Performance and User Experience](https://web.dev/performance/)

---

**Happy lazy loading!** 🚀
