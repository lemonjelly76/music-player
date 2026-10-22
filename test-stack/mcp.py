"""Calls one MCP tool on the test server: python3 test-stack/mcp.py <tool> '<json arguments>'.

Reads APP_USERNAME and APP_PASSWORD from the environment; MCP_URL picks another server.
"""
import base64
import json
import os
import sys
import urllib.request

URL = os.environ.get("MCP_URL", "http://localhost:8092/mcp")
AUTH = "Basic " + base64.b64encode(f"{os.environ['APP_USERNAME']}:{os.environ['APP_PASSWORD']}".encode()).decode()


def post(body, session=None):
    headers = {"Authorization": AUTH, "Content-Type": "application/json",
               "Accept": "application/json, text/event-stream"}
    if session:
        headers["Mcp-Session-Id"] = session
    request = urllib.request.Request(URL, json.dumps(body).encode(), headers)
    with urllib.request.urlopen(request, timeout=300) as response:
        text = response.read().decode()
        session = response.headers.get("Mcp-Session-Id") or session
    for line in text.splitlines():
        if line.startswith("data:"):
            text = line[5:]
    return (json.loads(text) if text.strip() else None), session


def call(tool, arguments):
    _, session = post({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
        "protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "test-stack", "version": "1"}}})
    post({"jsonrpc": "2.0", "method": "notifications/initialized"}, session)
    result, _ = post({"jsonrpc": "2.0", "id": 2, "method": "tools/call",
                      "params": {"name": tool, "arguments": arguments}}, session)
    return result["result"]["content"][0]["text"] if "result" in result else json.dumps(result)


if __name__ == "__main__":
    print(call(sys.argv[1], json.loads(sys.argv[2] if len(sys.argv) > 2 else "{}")))
