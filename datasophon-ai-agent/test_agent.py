#!/usr/bin/env python3
"""Test script for datasophon-ai-agent service at localhost:18090"""

import requests
import json
import sys

BASE_URL = "http://localhost:18090"
AGENT_TOKEN = "change-me"

def test_health():
    """Test /health endpoint"""
    print("=" * 60)
    print("TEST 1: GET /health")
    print("=" * 60)
    try:
        resp = requests.get(f"{BASE_URL}/health", timeout=5)
        print(f"Status: {resp.status_code}")
        print(f"Body: {resp.text}")
        print(f"PASS\n")
        return True
    except Exception as e:
        print(f"FAIL: {e}\n")
        return False

def test_debug():
    """Test /debug endpoint"""
    print("=" * 60)
    print("TEST 2: GET /debug")
    print("=" * 60)
    try:
        resp = requests.get(f"{BASE_URL}/debug", timeout=10)
        print(f"Status: {resp.status_code}")
        print(f"Body: {resp.text}")
        print(f"PASS\n")
        return True
    except Exception as e:
        print(f"FAIL: {e}\n")
        return False

def test_chat_no_auth():
    """Test /agent/chat without auth - should return 401"""
    print("=" * 60)
    print("TEST 3: POST /agent/chat (no auth, expect 401)")
    print("=" * 60)
    payload = {
        "messages": [{"role": "user", "content": "hello"}]
    }
    try:
        resp = requests.post(
            f"{BASE_URL}/agent/chat",
            json=payload,
            headers={"Content-Type": "application/json"},
            timeout=10
        )
        print(f"Status: {resp.status_code}")
        print(f"Body: {resp.text}")
        if resp.status_code == 401:
            print("PASS - Got expected 401 unauthorized\n")
            return True
        else:
            print(f"FAIL - Expected 401, got {resp.status_code}\n")
            return False
    except Exception as e:
        print(f"FAIL: {e}\n")
        return False

def test_chat_with_auth():
    """Test /agent/chat with auth - should send SSE stream"""
    print("=" * 60)
    print("TEST 4: POST /agent/chat (with auth, expect SSE stream)")
    print("=" * 60)
    payload = {
        "messages": [{"role": "user", "content": "list clusters"}]
    }
    try:
        resp = requests.post(
            f"{BASE_URL}/agent/chat",
            json=payload,
            headers={
                "Content-Type": "application/json",
                "X-Agent-Token": AGENT_TOKEN
            },
            stream=True,
            timeout=30
        )
        print(f"Status: {resp.status_code}")
        print(f"Content-Type: {resp.headers.get('Content-Type')}")
        print(f"Cache-Control: {resp.headers.get('Cache-Control')}")
        print("Stream response:")
        
        for line in resp.iter_lines():
            if line:
                decoded = line.decode('utf-8')
                print(f"  {decoded}")
        
        print("PASS\n")
        return True
    except Exception as e:
        print(f"FAIL: {e}\n")
        return False

def test_chat_simple():
    """Simple test without streaming - check if we get any response at all"""
    print("=" * 60)
    print("TEST 5: Simple POST /agent/chat (non-streaming)")
    print("=" * 60)
    payload = {
        "messages": [{"role": "user", "content": "list clusters"}]
    }
    try:
        headers = {
            "Content-Type": "application/json",
            "X-Agent-Token": AGENT_TOKEN
        }
        resp = requests.post(
            f"{BASE_URL}/agent/chat",
            json=payload,
            headers=headers,
            timeout=10
        )
        print(f"Status: {resp.status_code}")
        print(f"Content-Type: {resp.headers.get('Content-Type')}")
        print(f"Response length: {len(resp.content)} bytes")
        print(f"First 200 chars: {resp.text[:200]}")
        
        if len(resp.text) > 0:
            print("PASS - Got response\n")
            return True
        else:
            print("FAIL - Empty response\n")
            return False
    except requests.exceptions.ReadTimeout:
        print("FAIL: Read timeout\n")
        return False
    except Exception as e:
        print(f"FAIL: {e}\n")
        return False

def main():
    """Run all tests"""
    print("\nDatasophon AI Agent Test Suite")
    print(f"Base URL: {BASE_URL}")
    print(f"Agent Token: {'*' * 8}")
    print("\n")
    
    results = []
    results.append(("health", test_health()))
    results.append(("debug", test_debug()))
    results.append(("chat_no_auth", test_chat_no_auth()))
    results.append(("chat_with_auth", test_chat_with_auth()))
    results.append(("chat_simple", test_chat_simple()))
    
    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    passed = sum(1 for _, result in results if result)
    total = len(results)
    for test_name, result in results:
        status = "PASS" if result else "FAIL"
        print(f"{test_name:20s} : {status}")
    print(f"\nTotal: {passed}/{total} passed")
    
    if passed == total:
        print("\nAll tests passed!")
        sys.exit(0)
    else:
        print("\nSome tests failed!")
        sys.exit(1)

if __name__ == "__main__":
    main()
