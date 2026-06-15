import json
import time
import urllib.request
import urllib.error
import hmac
import hashlib
import base64
import subprocess

# Configurations
JWT_SECRET = "9feb0f02e6ea4e2fdda9605a4e6cabfa0f93e5c369be32edfc120d4221fc9660"
CRBT_SECRET = "BvPHGM8C0ia4uOuxxqPD5DTbWC9F9TWvPStp3pb7ARo0oK2mJ3pd3YG4lxA9i8bj6OTbadwezxgeEByY"
GATEWAY_URL = "http://localhost:18080"

USER_A_MSISDN = "0912345678"  # userId = 5
USER_B_MSISDN = "0911111111"  # userId = 6

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

def generate_crbt_jwt(msisdn, secret):
    header = {"alg": "HS256", "typ": "JWT"}
    now = int(time.time())
    payload = {
        "sub": msisdn,
        "phone": msisdn,
        "status": 1,
        "id": 1,
        "loginType": 1,
        "iat": now,
        "exp": now + 3600
    }
    unsigned_token = base64url_encode(header) + "." + base64url_encode(payload)
    signature = hmac.new(secret.encode('utf-8'), unsigned_token.encode('utf-8'), hashlib.sha256).digest()
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

def seed_redis(msisdn):
    # Seed Redis cache pool for AI music to ensure cache hit
    input_str = "pop:happy:"
    hash_key = hashlib.sha256(input_str.encode('utf-8')).hexdigest()
    pool_key = f"lyria:pool:{hash_key}"
    entry_val = json.dumps({"url": "http://localhost:9000/media-audio/lyria-mock-track-123.mp3", "owner": "0988888888"})
    
    try:
        seen_key = f"lyria:seen:{msisdn}:{hash_key}"
        # Clear old pool entries if any
        subprocess.run(["docker", "exec", "microservice-platform-redis-1", "redis-cli", "-a", "Crbt2026", "DEL", pool_key], check=True, stdout=subprocess.DEVNULL)
        # Clear seen entries for this user to ensure cache hit
        subprocess.run(["docker", "exec", "microservice-platform-redis-1", "redis-cli", "-a", "Crbt2026", "DEL", seen_key], check=True, stdout=subprocess.DEVNULL)
        # Push mock entry
        subprocess.run(["docker", "exec", "microservice-platform-redis-1", "redis-cli", "-a", "Crbt2026", "RPUSH", pool_key, entry_val], check=True, stdout=subprocess.DEVNULL)
    except Exception as e:
        print(f"Warning: Failed to seed Redis: {e}")

def main():
    print("=== STARTING EDIT PERMISSIONS INTEGRATION TEST ===")
    
    admin_token = generate_admin_jwt()
    user_a_token = generate_crbt_jwt(USER_A_MSISDN, CRBT_SECRET)
    user_b_token = generate_crbt_jwt(USER_B_MSISDN, CRBT_SECRET)
    
    admin_headers = {"Authorization": f"Bearer {admin_token}"}
    user_a_headers = {"X-CRBT-Token": user_a_token}
    user_b_headers = {"X-CRBT-Token": user_b_token}
    
    # Subscribe both users
    print("\n--- Subscribing User A and User B ---")
    send_request(f"{GATEWAY_URL}/api/campaigns/subscribe", "POST", headers=user_a_headers, data={"packageId": 10})
    send_request(f"{GATEWAY_URL}/api/campaigns/subscribe", "POST", headers=user_b_headers, data={"packageId": 10})
    print("Subscribed successfully.")
    
    # 1. AI MUSIC TESTS
    print("\n=== PHASE 1: AI MUSIC EDIT PERMISSIONS ===")
    
    # Generate AI music for User A
    print("Generating AI music for User A...")
    seed_redis(USER_A_MSISDN)
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/generate?genre=pop&mood=happy", "POST", headers=user_a_headers)
    assert status == 200, f"User A generate failed: {status}"
    
    # Get User A's item ID
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library", "GET", headers=user_a_headers)
    assert status == 200 and len(res.get("data", [])) > 0, "Failed to get User A library"
    ai_item_a = res["data"][0]
    ai_item_a_id = ai_item_a["id"]
    print(f"User A AI music ID: {ai_item_a_id}, title: {ai_item_a['title']}")
    
    # Generate AI music for User B
    print("Generating AI music for User B...")
    seed_redis(USER_B_MSISDN)
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/generate?genre=pop&mood=happy", "POST", headers=user_b_headers)
    assert status == 200, f"User B generate failed: {status}"
    
    # Get User B's item ID
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library", "GET", headers=user_b_headers)
    assert status == 200 and len(res.get("data", [])) > 0, "Failed to get User B library"
    ai_item_b = res["data"][0]
    ai_item_b_id = ai_item_b["id"]
    print(f"User B AI music ID: {ai_item_b_id}, title: {ai_item_b['title']}")
    
    # Test case 1: User A edits User A's own AI music -> Should Succeed
    print("\n[TC-1] User A edits User A's AI music (should succeed)...")
    edit_req = {"title": "User A Own Edited AI Title"}
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library/{ai_item_a_id}", "PUT", headers=user_a_headers, data=edit_req)
    print(f"Status: {status}, Response: {res}")
    assert status == 200, f"Expected 200 but got {status}"
    assert res.get("data", {}).get("title") == "User A Own Edited AI Title", "Title did not match"
    print("TC-1 PASSED!")
    
    # Test case 2: User A attempts to edit User B's AI music -> Should fail with 403 Forbidden
    print("\n[TC-2] User A attempts to edit User B's AI music (should fail 403)...")
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library/{ai_item_b_id}", "PUT", headers=user_a_headers, data={"title": "Hacked Title"})
    print(f"Status: {status}, Response: {res}")
    assert status == 403, f"Expected 403 but got {status}"
    print("TC-2 PASSED!")
    
    # Test case 3: Admin edits User B's AI music via standard edit endpoint -> Should Succeed
    print("\n[TC-3] Admin edits User B's AI music via standard edit API (should succeed)...")
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library/{ai_item_b_id}", "PUT", headers=admin_headers, data={"title": "Admin Edited User B AI Title"})
    print(f"Status: {status}, Response: {res}")
    assert status == 200, f"Expected 200 but got {status}"
    assert res.get("data", {}).get("title") == "Admin Edited User B AI Title", "Title did not match"
    print("TC-3 PASSED!")
    
    # Test case 4: Admin edits User B's AI music via admin edit endpoint -> Should Succeed
    print("\n[TC-4] Admin edits User B's AI music via admin edit API (should succeed)...")
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/admin/music-items/{ai_item_b_id}", "PUT", headers=admin_headers, data={"title": "Admin Double Override AI Title"})
    print(f"Status: {status}, Response: {res}")
    assert status == 200, f"Expected 200 but got {status}"
    assert res.get("data", {}).get("title") == "Admin Double Override AI Title", "Title did not match"
    print("TC-4 PASSED!")
    
    # Verify User B sees the admin-edited title
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library", "GET", headers=user_b_headers)
    assert res["data"][0]["title"] == "Admin Double Override AI Title", "User B did not see admin changes"
    print("AI verify completed successfully.")
    
    # 2. DIY MUSIC TESTS
    print("\n=== PHASE 2: DIY MUSIC EDIT PERMISSIONS ===")
    
    # Create a completed DIY job for User B (userId = 6) using the Admin API
    print("Creating completed DIY job for User B via Admin API...")
    diy_req = {
        "prompt": "DIY user B prompt",
        "voiceId": "voice1",
        "type": "DIY",
        "audioFileKey": "http://localhost:9000/media-audio/diy_b.mp3",
        "vocalStart": 0.0,
        "vocalEnd": 45.0,
        "title": "DIY User B Title",
        "msisdn": USER_B_MSISDN
    }
    # Call downstream admin endpoint directly to bypass Campaign service limit / explicitly specify userId=6
    status, res = send_request(f"{GATEWAY_URL}/api/audio/admin?userId=6", "POST", headers=admin_headers, data=diy_req)
    assert status == 200, f"Failed to create DIY job: {status}, response: {res}"
    diy_item_b_id = f"DIY_{res['data']['id']}"
    print(f"User B DIY music ID: {diy_item_b_id}, title: {res['data']['title']}")
    
    # Create a completed DIY job for User A (userId = 5)
    print("Creating completed DIY job for User A via Admin API...")
    diy_req["msisdn"] = USER_A_MSISDN
    status, res = send_request(f"{GATEWAY_URL}/api/audio/admin?userId=5", "POST", headers=admin_headers, data=diy_req)
    assert status == 200
    diy_item_a_id = f"DIY_{res['data']['id']}"
    print(f"User A DIY music ID: {diy_item_a_id}, title: {res['data']['title']}")
    
    # Test case 5: User A edits User A's own DIY music -> Should Succeed
    print("\n[TC-5] User A edits User A's DIY music (should succeed)...")
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library/{diy_item_a_id}", "PUT", headers=user_a_headers, data={"title": "User A Own Edited DIY Title"})
    print(f"Status: {status}, Response: {res}")
    assert status == 200, f"Expected 200 but got {status}"
    assert res.get("data", {}).get("title") == "User A Own Edited DIY Title", "Title did not match"
    print("TC-5 PASSED!")
    
    # Test case 6: User A attempts to edit User B's DIY music -> Should fail with 403 Forbidden
    print("\n[TC-6] User A attempts to edit User B's DIY music (should fail 403)...")
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library/{diy_item_b_id}", "PUT", headers=user_a_headers, data={"title": "Hacked DIY Title"})
    print(f"Status: {status}, Response: {res}")
    assert status == 403, f"Expected 403 but got {status}"
    print("TC-6 PASSED!")
    
    # Test case 7: Admin edits User B's DIY music via standard edit endpoint -> Should Succeed
    print("\n[TC-7] Admin edits User B's DIY music via standard edit API (should succeed)...")
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library/{diy_item_b_id}", "PUT", headers=admin_headers, data={"title": "Admin Edited User B DIY Title"})
    print(f"Status: {status}, Response: {res}")
    assert status == 200, f"Expected 200 but got {status}"
    assert res.get("data", {}).get("title") == "Admin Edited User B DIY Title", "Title did not match"
    print("TC-7 PASSED!")
    
    # Test case 8: Admin edits User B's DIY music via admin edit endpoint -> Should Succeed
    print("\n[TC-8] Admin edits User B's DIY music via admin edit API (should succeed)...")
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/admin/music-items/{diy_item_b_id}", "PUT", headers=admin_headers, data={"title": "Admin Double Override DIY Title"})
    print(f"Status: {status}, Response: {res}")
    assert status == 200, f"Expected 200 but got {status}"
    assert res.get("data", {}).get("title") == "Admin Double Override DIY Title", "Title did not match"
    print("TC-8 PASSED!")
    
    # Verify User B sees the admin-edited title for DIY
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library", "GET", headers=user_b_headers)
    print(f"DEBUG: User B My Library response: {res}")
    diy_found = False
    for item in res.get("data", []):
        if item["id"] == diy_item_b_id:
            assert item["title"] == "Admin Double Override DIY Title", "User B did not see admin changes"
            diy_found = True
            break
    assert diy_found, "DIY item not found in User B library"
    print("DIY verify completed successfully.")
    
    # Cleanup test items
    print("\nCleaning up created items...")
    send_request(f"{GATEWAY_URL}/api/campaigns/admin/music-items/{ai_item_a_id}?hard=true", "DELETE", headers=admin_headers)
    send_request(f"{GATEWAY_URL}/api/campaigns/admin/music-items/{ai_item_b_id}?hard=true", "DELETE", headers=admin_headers)
    send_request(f"{GATEWAY_URL}/api/campaigns/admin/music-items/{diy_item_a_id}?hard=true", "DELETE", headers=admin_headers)
    send_request(f"{GATEWAY_URL}/api/campaigns/admin/music-items/{diy_item_b_id}?hard=true", "DELETE", headers=admin_headers)
    print("Cleanup complete.")
    
    print("\n=== ALL EDIT PERMISSION TESTS PASSED! ===")

if __name__ == "__main__":
    main()
