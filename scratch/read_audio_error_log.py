import subprocess
import json

print("Reading recent error logs from audio-generation-service...")

try:
    res = subprocess.run(
        ["docker", "logs", "microservice-platform-audio-generation-service-1"],
        capture_output=True,
        text=True
    )
    lines = res.stdout.splitlines()
    error_count = 0
    # Search from the end
    for line in reversed(lines):
        if '"level":"ERROR"' in line or '"Exception"' in line:
            try:
                data = json.loads(line)
                print("\n--- ERROR ENTRY ---")
                print(f"Timestamp: {data.get('@timestamp')}")
                print(f"Logger: {data.get('logger_name')}")
                print(f"Level: {data.get('level')}")
                print(f"Message: {data.get('message')}")
                stack = data.get('stack_trace')
                if stack:
                    print("Stack Trace (Top 15 lines):")
                    for s_line in stack.splitlines()[:15]:
                        print(s_line)
                else:
                    print(f"Data: {json.dumps(data, indent=2)}")
                error_count += 1
                if error_count >= 5:
                    break
            except Exception as ex:
                print(f"Failed to parse line: {ex}")
                print(line[:300])
except Exception as e:
    print(f"Error: {e}")
