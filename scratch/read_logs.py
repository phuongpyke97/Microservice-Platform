import subprocess
import json

def main():
    try:
        out = subprocess.check_output(["docker", "compose", "logs", "--tail", "100", "crbt-campaign-service"])
        for line in out.decode('utf-8').splitlines():
            if "UPDATE-LIBRARY-ITEM-DIY-ERR" in line:
                idx = line.find("{")
                if idx != -1:
                    json_str = line[idx:]
                    try:
                        data = json.loads(json_str)
                        print("=== log entry ===")
                        print("Message:", data.get("message"))
                        print("Exception details:")
                        # Hibernating logs usually put stack trace in a field or message
                        # We can just print the whole parsed dict nicely
                        for k, v in data.items():
                            if k not in ["@timestamp", "logger_name", "thread_name", "context"]:
                                print(f"  {k}: {str(v)[:300]}")
                    except Exception as ex:
                        print("Failed to parse json:", ex)
                        print(line[:200])
                else:
                    print(line[:200])
    except Exception as e:
        print("Error reading logs:", e)

if __name__ == "__main__":
    main()
