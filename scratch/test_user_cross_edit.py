import json
import time
import urllib.request
import urllib.error
import hmac
import hashlib
import base64
import subprocess

JWT_SECRET = "9feb0f02e6ea4e2fdda9605a4e6cabfa0f93e5c369be32edfc120d4221fc9660"
CRBT_SECRET = "BvPHGM8C0ia4uOuxxqPD5DTbWC9F9TWvPStp3pb7ARo0oK2mJ3pd3YG4lxA9i8bj6OTbadwezxgeEByY"
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

def generate_crbt_jwt(user_id, msisdn):
    header = {"alg": "HS256", "typ": "JWT"}
    now = int(time.time())
    payload = {
        "sub": msisdn,
        "phone": msisdn,
        "status": 1,
        "id": user_id,
        "loginType": 1,
        "iat": now,
        "exp": now + 3600
    }
    unsigned_token = base64url_encode(header) + "." + base64url_encode(payload)
    signature = hmac.new(CRBT_SECRET.encode('utf-8'), unsigned_token.encode('utf-8'), hashlib.sha256).digest()
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
    print("=== CMS USER OWNERSHIP & CROSS-EDIT SECURITY TEST ===")
    
    # 1. Generate tokens
    admin_token = generate_admin_jwt()
    usera_token = generate_crbt_jwt(user_id=5, msisdn="0911111111")
    userb_token = generate_crbt_jwt(user_id=6, msisdn="0922222222")
    
    admin_headers = {"Authorization": f"Bearer {admin_token}"}
    usera_headers = {"X-CRBT-Token": usera_token}
    userb_headers = {"X-CRBT-Token": userb_token}
    
    # Seed Redis cache to guarantee a Cache Hit for generating AI music for User A
    print("\n[Setup] Seeding Redis cache for pop:happy:...")
    input_str = "pop:happy:"
    hash_key = hashlib.sha256(input_str.encode('utf-8')).hexdigest()
    pool_key = f"lyria:pool:{hash_key}"
    entry_val = json.dumps({"url": "http://localhost:9000/media-audio/usera_ai_track.mp3", "owner": "0988888888"})
    try:
        # Clear seen key to guarantee cache hit
        seen_key_a = f"lyria:seen:0911111111:{hash_key}"
        seen_key_b = f"lyria:seen:0922222222:{hash_key}"
        subprocess.run(["docker", "exec", "microservice-platform-redis-1", "redis-cli", "-a", "Crbt2026", "DEL", pool_key], check=True)
        subprocess.run(["docker", "exec", "microservice-platform-redis-1", "redis-cli", "-a", "Crbt2026", "DEL", seen_key_a], check=True)
        subprocess.run(["docker", "exec", "microservice-platform-redis-1", "redis-cli", "-a", "Crbt2026", "DEL", seen_key_b], check=True)
        subprocess.run(["docker", "exec", "microservice-platform-redis-1", "redis-cli", "-a", "Crbt2026", "RPUSH", pool_key, entry_val], check=True)
        print("Redis seed complete.")
    except Exception as e:
        print(f"Warning: Failed to seed Redis: {e}")

    # Setup: Ensure User A & User B are subscribed
    print("\n[Setup] Subscribing User A...")
    send_request(f"{GATEWAY_URL}/api/campaigns/subscribe", "POST", headers=usera_headers, data={"packageId": 10})
    print("[Setup] Subscribing User B...")
    send_request(f"{GATEWAY_URL}/api/campaigns/subscribe", "POST", headers=userb_headers, data={"packageId": 10})
    
    # Step 1: Create AI music for User A
    print("\n--- Step 1: User A generates an AI music track ---")
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/generate?genre=pop&mood=happy", "POST", headers=usera_headers)
    print(f"Status: {status}")
    assert status == 200, f"Failed to generate AI track, got status {status}: {res}"
    
    # Find the AI track ID in User A's library
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library", "GET", headers=usera_headers)
    assert status == 200 and len(res.get("data", [])) > 0, "AI item not in User A's library"
    ai_item = [x for x in res["data"] if x.get("source") == "AI"][0]
    ai_id = ai_item["id"]
    print(f"User A generated AI track ID: {ai_id}, title: {ai_item['title']}")
    
    # Step 2: Create DIY music for User A
    print("\n--- Step 2: User A generates a DIY music track ---")
    diy_payload = {
        "prompt": "User A DIY Tone prompt",
        "voiceId": "voice_1",
        "type": "DIY",
        "audioFileKey": "http://localhost:9000/media-audio/usera_diy.mp3",
        "vocalStart": 0.0,
        "vocalEnd": 10.0,
        "title": "User A DIY Track"
    }
    # User level submit DIY tone is POST to /api/audio-jobs but wait, we need to pass X-CRBT-Token so gateway resolves user
    status, res = send_request(f"{GATEWAY_URL}/api/audio/", "POST", headers=usera_headers, data=diy_payload)
    print(f"Status: {status}")
    assert status == 202, f"Failed to submit DIY job, got {status}: {res}"
    diy_id = f"DIY_{res['data']['id']}"
    print(f"User A submitted DIY track ID: {diy_id}")
    
    # Step 3: User A updates their own tracks (AI & DIY)
    print("\n--- Step 3: User A updates their own tracks ---")
    
    # 3.1 User A updates AI track
    update_req = {"title": "User A Updated AI Title"}
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library/{ai_id}", "PUT", headers=usera_headers, data=update_req)
    print(f"User A updating AI track - Status: {status}")
    assert status == 200, f"Expected 200 but got {status}"
    print(f"Response title: {res['data']['title']}")
    
    # 3.2 User A updates DIY track
    update_req = {"title": "User A Updated DIY Title"}
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library/{diy_id}", "PUT", headers=usera_headers, data=update_req)
    print(f"User A updating DIY track - Status: {status}")
    assert status == 200, f"Expected 200 but got {status}"
    print(f"Response title: {res['data']['title']}")
    
    # Step 4: Cross-user edit security (User B attempts to edit User A's tracks)
    print("\n--- Step 4: Security test - User B attempts to edit User A's tracks ---")
    
    # 4.1 User B tries to update User A's AI track
    update_req = {"title": "Hacked AI Title"}
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library/{ai_id}", "PUT", headers=userb_headers, data=update_req)
    print(f"User B updating User A's AI track - Status (Expected 403): {status}")
    print(f"Response: {res}")
    assert status == 403, f"Security Breach! Expected 403 but got {status}"
    
    # 4.2 User B tries to update User A's DIY track
    update_req = {"title": "Hacked DIY Title"}
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/my-library/{diy_id}", "PUT", headers=userb_headers, data=update_req)
    print(f"User B updating User A's DIY track - Status (Expected 403): {status}")
    print(f"Response: {res}")
    assert status == 403, f"Security Breach! Expected 403 but got {status}"
    print("Cross-user edit security checks passed successfully!")
    
    # Step 5: Admin edits User A's tracks
    print("\n--- Step 5: Admin edits User A's tracks ---")
    
    # 5.1 Admin updates AI track
    update_req = {"title": "Admin Overridden AI Title"}
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/admin/music-items/{ai_id}", "PUT", headers=admin_headers, data=update_req)
    print(f"Admin updating User A's AI track - Status: {status}")
    assert status == 200, f"Expected 200 but got {status}"
    print(f"Response title: {res['data']['title']}")
    
    # 5.2 Admin updates DIY track
    update_req = {"title": "Admin Overridden DIY Title"}
    status, res = send_request(f"{GATEWAY_URL}/api/campaigns/admin/music-items/{diy_id}", "PUT", headers=admin_headers, data=update_req)
    print(f"Admin updating User A's DIY track - Status: {status}")
    assert status == 200, f"Expected 200 but got {status}"
    print(f"Response title: {res['data']['title']}")
    
    # Cleanup: Hard delete User A's tracks via Admin
    print("\n[Cleanup] Deleting test records...")
    send_request(f"{GATEWAY_URL}/api/campaigns/admin/music-items/{ai_id}?hard=true", "DELETE", headers=admin_headers)
    send_request(f"{GATEWAY_URL}/api/campaigns/admin/music-items/{diy_id}?hard=true", "DELETE", headers=admin_headers)
    print("Cleanup complete!")
    
    print("\n=== ALL CROSS-USER OWNERSHIP AND EDIT SECURITY CHECKS PASSED! ===")

if __name__ == "__main__":
    main()
