import subprocess
import json

trace_id = "f24b8592-6d4b-40b4-bda4-c6e5314af84c"
print(f"Reading logs for traceId: {trace_id}")

try:
    res = subprocess.run(
        ["docker", "logs", "microservice-platform-crbt-campaign-service-1"],
        capture_output=True,
        text=True
    )
    for line in res.stdout.splitlines():
        if trace_id in line:
            try:
                data = json.loads(line)
                print("--- LOG ENTRY ---")
                print(f"Timestamp: {data.get('@timestamp')}")
                print(f"Logger: {data.get('logger_name')}")
                print(f"Level: {data.get('level')}")
                print(f"Message: {data.get('message')}")
                stack = data.get('stack_trace')
                if stack:
                    print("Stack Trace:")
                    print(stack)
                else:
                    # check other fields
                    print(f"Data: {json.dumps(data, indent=2)}")
            except Exception as ex:
                print(f"Failed to parse line as JSON: {ex}")
                print(line)
except Exception as e:
    print(f"Error reading docker logs: {e}")
