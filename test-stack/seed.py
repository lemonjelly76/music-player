"""Adds a person to the test server with the fake Spotify connected, ready for the app or Claude to use:
python3 test-stack/seed.py [name]. Reads APP_USERNAME and APP_PASSWORD from the environment.
"""
import base64
import json
import os
import sys
import urllib.request

from mcp import call

SERVER = os.environ.get("TEST_SERVER", "http://localhost:8092")
AUTH = "Basic " + base64.b64encode(f"{os.environ['APP_USERNAME']}:{os.environ['APP_PASSWORD']}".encode()).decode()


def get(path):
    request = urllib.request.Request(SERVER + path, headers={"Authorization": AUTH})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode())


name = sys.argv[1] if len(sys.argv) > 1 else "Tester"
people = {person["name"]: person for person in get("/api/people")}
if name not in people:
    print(call("add_person", {"name": name, "service": "spotify"}))
    people = {person["name"]: person for person in get("/api/people")}
person = people[name]
if not person.get("connected"):
    # The fake sign-in page sends the browser straight back to the server's callback with a code.
    connect_url = get(f"/api/people/{person['id']}/connect")["url"]
    with urllib.request.urlopen(connect_url, timeout=30) as response:
        print("Connected:", response.status == 200)
print(f"{name} is {person['id']} on {SERVER}")
