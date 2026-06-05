import hmac
import hashlib
import base64
import json
import time
import urllib.request
import urllib.error
import subprocess

# Configurations
MSISDN = "0912345678"
SECRET = "BvPHGM8C0ia4uOuxxqPD5DTbWC9F9TWvPStp3pb7ARo0oK2mJ3pd3YG4lxA9i8bj6OTbadwezxgeEByY"
GATEWAY_URL = "http://localhost:18080"

def base64url_encode(data):
    if isinstance(data, dict):
        data = json.dumps(data, separators=(',', ':')).encode('utf-8')
    return base64.urlsafe_b64encode(data).decode('utf-8').rstrip('=')

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

def main():
    print("=== LIVE INTEGRATION TEST START ===")
    
    # 1. Generate CRBT token
    token = generate_crbt_jwt(MSISDN, SECRET)
    headers = {"X-CRBT-Token": token}
    print(f"Generated X-CRBT-Token for MSISDN {MSISDN} successfully.")

    # 2. Seed Redis Cache Pool to guarantee a CACHE HIT (avoiding real Gemini API call)
    # pop:happy: -> SHA256 is e7172ab01c238b7e2894bbfb13f1737e6f6a7d0e408ec25f9c490a618d3632cf
    # Let's verify input "pop:happy:"
    input_str = "pop:happy:"
    hash_key = hashlib.sha256(input_str.encode('utf-8')).hexdigest()
    pool_key = f"lyria:pool:{hash_key}"
    entry_val = json.dumps({"url": "http://localhost:9000/media-audio/lyria-mock-track-123.mp3", "owner": "0988888888"})
    
    # Seed it via docker command
    try:
        seen_key = f"lyria:seen:{MSISDN}:{hash_key}"
        print(f"Seeding mock audio in Redis pool for key: {pool_key} and clearing seen key: {seen_key}...")
        # Clear old pool entries if any
        subprocess.run(["docker", "exec", "microservice-platform-redis-1", "redis-cli", "-a", "Crbt2026", "DEL", pool_key], check=True)
        # Clear seen entries for this user to ensure cache hit
        subprocess.run(["docker", "exec", "microservice-platform-redis-1", "redis-cli", "-a", "Crbt2026", "DEL", seen_key], check=True)
        # Push mock entry
        subprocess.run(["docker", "exec", "microservice-platform-redis-1", "redis-cli", "-a", "Crbt2026", "RPUSH", pool_key, entry_val], check=True)
        print("Redis seed complete.")
    except Exception as e:
        print(f"Warning: Failed to seed Redis via docker command: {e}. If already seeded, continuing...")

    # 3. Register user and subscribe to TRIAL package (which gives credits)
    print("\n--- Step 1: Subscribing to TRIAL Package ---")
    sub_url = f"{GATEWAY_URL}/api/campaigns/subscribe"
    status, res = send_request(sub_url, "POST", headers=headers, data={"packageId": 10})

    print(f"Status: {status}")
    print(f"Response: {json.dumps(res, indent=2)}")

    # 4. Check initial My Library
    print("\n--- Step 2: Fetching Empty Library ---")
    lib_url = f"{GATEWAY_URL}/api/campaigns/my-library"
    status, res = send_request(lib_url, "GET", headers=headers)
    print(f"Status: {status}")
    print(f"Response: {json.dumps(res, indent=2)}")

    # 5. Generate AI music (guaranteed Cache Hit on pop:happy:)
    print("\n--- Step 3: Generating AI Music (Cache Hit) ---")
    gen_url = f"{GATEWAY_URL}/api/campaigns/generate?genre=pop&mood=happy"
    status, res = send_request(gen_url, "POST", headers=headers)
    print(f"Status: {status}")
    print(f"Response: {json.dumps(res, indent=2)}")

    # 6. Fetch Library again (should contain 1 item with dynamic name)
    print("\n--- Step 4: Fetching Library with Generated Song ---")
    status, res = send_request(lib_url, "GET", headers=headers)
    print(f"Status: {status}")
    print(f"Response: {json.dumps(res, indent=2)}")
    
    unified_id = None
    if status == 200 and res.get("success") and res.get("data"):
        items = res["data"]
        if items:
            unified_id = items[0]["id"]
            print(f"Successfully found generated item ID: {unified_id}")

    # 7. Delete the item from Library
    if unified_id:
        print(f"\n--- Step 5: Deleting Song {unified_id} from Library ---")
        del_url = f"{GATEWAY_URL}/api/campaigns/my-library/{unified_id}"
        status, res = send_request(del_url, "DELETE", headers=headers)
        print(f"Status: {status}")
        print(f"Response: {json.dumps(res, indent=2)}")

        # 8. Verify library is empty again
        print("\n--- Step 6: Verifying Library is Empty ---")
        status, res = send_request(lib_url, "GET", headers=headers)
        print(f"Status: {status}")
        print(f"Response: {json.dumps(res, indent=2)}")

    print("\n=== LIVE INTEGRATION TEST COMPLETE ===")

if __name__ == "__main__":
    main()
