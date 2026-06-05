import json
import time
import urllib.request
import urllib.error
import hmac
import hashlib
import base64

# Configurations
JWT_SECRET = "9feb0f02e6ea4e2fdda9605a4e6cabfa0f93e5c369be32edfc120d4221fc9660"
GATEWAY_URL = "http://localhost:18080"

def base64url_encode(data):
    if isinstance(data, dict):
        data = json.dumps(data, separators=(',', ':')).encode('utf-8')
    return base64.urlsafe_b64encode(data).decode('utf-8').rstrip('=')

def generate_admin_jwt():
    header = {"alg": "HS256", "typ": "JWT"}
    now = int(time.time())
    payload = {
        "sub": "1",
        "email": "admin@platform.com",
        "roles": ["ROLE_ADMIN"],
        "iat": now,
        "exp": now + 3600
    }
    unsigned_token = base64url_encode(header) + "." + base64url_encode(payload)
    signature = hmac.new(JWT_SECRET.encode('utf-8'), unsigned_token.encode('utf-8'), hashlib.sha256).digest()
    return unsigned_token + "." + base64url_encode(signature)

def generate_user_jwt():
    header = {"alg": "HS256", "typ": "JWT"}
    now = int(time.time())
    payload = {
        "sub": "5",
        "email": "user@platform.com",
        "roles": ["ROLE_USER"],
        "iat": now,
        "exp": now + 3600
    }
    unsigned_token = base64url_encode(header) + "." + base64url_encode(payload)
    signature = hmac.new(JWT_SECRET.encode('utf-8'), unsigned_token.encode('utf-8'), hashlib.sha256).digest()
    return unsigned_token + "." + base64url_encode(signature)

def send_request(url, method="GET", headers=None, data=None):
    if headers is None:
        headers = {}
    
    req_data = None
    if data is not None:
        req_data = json.dumps(data).encode('utf-8')
        headers["Content-Type"] = "application/json"
        
    req = urllib.request.Request(url, data=req_data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as response:
            res_body = response.read().decode('utf-8')
            return response.status, json.loads(res_body)
    except urllib.error.HTTPError as e:
        err_body = e.read().decode('utf-8')
        try:
            return e.code, json.loads(err_body)
        except:
            return e.code, err_body
    except Exception as e:
        return 500, str(e)

def main():
    print("=== CMS ADMIN LIVE TEST START ===")
    
    admin_token = generate_admin_jwt()
    user_token = generate_user_jwt()
    
    admin_headers = {"Authorization": f"Bearer {admin_token}"}
    user_headers = {"Authorization": f"Bearer {user_token}"}
    
    base_admin_url = f"{GATEWAY_URL}/api/campaigns/admin/music-items"
    
    suffix = int(time.time())
    ai_title = f"Admin Created AI Track {suffix}"
    diy_title = f"Admin Created DIY Track {suffix}"
    diy_updated_title = f"Admin Overridden DIY Title {suffix}"
    
    # 1. Security Check: Normal user should get 403 Forbidden
    print("\n--- Step 1: Security check with normal User token ---")
    status, res = send_request(base_admin_url, "GET", headers=user_headers)
    print(f"Status (expected 403): {status}")
    print(f"Response: {res}")
    assert status == 403, f"Expected 403 but got {status}"
    print("Security check passed!")
    
    # 2. Get initial list as admin
    print("\n--- Step 2: Query initial list as Admin ---")
    status, res = send_request(base_admin_url, "GET", headers=admin_headers)
    print(f"Status: {status}")
    print(f"Initial items count: {len(res.get('data', [])) if status == 200 else 'Error'}")
    assert status == 200, f"Expected 200 but got {status}"
    
    # 3. Create AI music item as Admin
    print("\n--- Step 3: Create AI music item as Admin ---")
    ai_req = {
        "title": ai_title,
        "source": "AI",
        "tags": ["pop", "chill"],
        "audioUrl": "http://localhost:9000/media-audio/admin_ai_track.mp3"
    }
    status, res = send_request(base_admin_url, "POST", headers=admin_headers, data=ai_req)
    print(f"Status: {status}")
    print(f"Response: {res}")
    assert status == 200, f"Expected 200 but got {status}"
    ai_item = res.get("data", {})
    ai_id = ai_item.get("id")
    print(f"Created AI Item ID: {ai_id}")
    
    # 4. Create DIY music item as Admin
    print("\n--- Step 4: Create DIY music item as Admin ---")
    diy_req = {
        "title": diy_title,
        "source": "DIY",
        "tags": ["diy", "mixed"],
        "audioUrl": "http://localhost:9000/media-audio/admin_diy_track.mp3"
    }
    status, res = send_request(base_admin_url, "POST", headers=admin_headers, data=diy_req)
    print(f"Status: {status}")
    print(f"Response: {res}")
    assert status == 200, f"Expected 200 but got {status}"
    diy_item = res.get("data", {})
    diy_id = diy_item.get("id")
    print(f"Created DIY Item ID: {diy_id}")
    
    # 5. Search with filters
    print("\n--- Step 5: Filter Search ---")
    
    # 5.1 Filter by AI source
    print("\n  Filter by source=AI:")
    status, res = send_request(f"{base_admin_url}?source=AI", "GET", headers=admin_headers)
    print(f"  AI Items count: {len(res.get('data', []))}")
    assert any(x.get("id") == ai_id for x in res.get("data", [])), "AI item not found in filtered list"
    
    # 5.2 Filter by DIY source
    print("\n  Filter by source=DIY:")
    status, res = send_request(f"{base_admin_url}?source=DIY", "GET", headers=admin_headers)
    print(f"  DIY Items count: {len(res.get('data', []))}")
    assert any(x.get("id") == diy_id for x in res.get("data", [])), "DIY item not found in filtered list"
    
    # 5.3 Search by title string
    print(f"\n  Search by search='{diy_title}':")
    status, res = send_request(f"{base_admin_url}?search={diy_title.replace(' ', '+')}", "GET", headers=admin_headers)
    print(f"  Search Items count: {len(res.get('data', []))}")
    assert len(res.get("data", [])) > 0 and res["data"][0].get("id") == diy_id, "Should find the DIY item"
    
    # 6. Update DIY music item title
    print("\n--- Step 6: Update DIY music item title ---")
    update_req = {
        "title": diy_updated_title,
        "source": "DIY",
        "audioUrl": "http://localhost:9000/media-audio/admin_diy_track_v2.mp3"
    }
    status, res = send_request(f"{base_admin_url}/{diy_id}", "PUT", headers=admin_headers, data=update_req)
    print(f"Status: {status}")
    print(f"Response: {res}")
    assert status == 200, "Expected 200"
    
    # Get details to verify update
    print("\n  Verify details of updated DIY item:")
    status, res = send_request(f"{base_admin_url}/{diy_id}", "GET", headers=admin_headers)
    print(f"  Title: {res.get('data', {}).get('title')}")
    assert res.get("data", {}).get("title") == diy_updated_title, "Title was not updated successfully"
    
    # 7. Delete both items
    print("\n--- Step 7: Delete both items ---")
    
    print(f"  Deleting AI item: {ai_id}")
    status, res = send_request(f"{base_admin_url}/{ai_id}?hard=true", "DELETE", headers=admin_headers)
    print(f"  Status: {status}")
    
    print(f"  Deleting DIY item: {diy_id}")
    status, res = send_request(f"{base_admin_url}/{diy_id}?hard=true", "DELETE", headers=admin_headers)
    print(f"  Status: {status}")
    
    # 8. Verify deletion
    print("\n--- Step 8: Verify deletion ---")
    status, res = send_request(f"{base_admin_url}?search={suffix}", "GET", headers=admin_headers)
    print(f"  Search matching items count: {len(res.get('data', []))}")
    assert len(res.get("data", [])) == 0, "Expected all deleted items to be cleared"
    
    print("\n=== CMS ADMIN LIVE TEST COMPLETE: ALL PASSED! ===")

if __name__ == "__main__":
    main()
