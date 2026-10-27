import asyncio
import copy
import json
import os
import secrets
import uuid
from pathlib import Path


TERMINAL = {"done", "error", "cancelled"}


class BatchManager:
    def __init__(self, path, backend):
        self.path = Path(path)
        self.backend = backend
        self.jobs = json.loads(self.path.read_text(encoding="utf-8")) if self.path.exists() else {}
        self.lock = asyncio.Lock()

    def save(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_suffix(".tmp")
        with temporary.open("w", encoding="utf-8") as stream:
            json.dump(self.jobs, stream, ensure_ascii=False, separators=(",", ":"))
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, self.path)

    def owned(self, batch_id, owner):
        job = self.jobs[batch_id]
        if job["owner"] != owner:
            raise KeyError(batch_id)
        return job

    def public(self, job):
        return copy.deepcopy({key: value for key, value in job.items() if key not in {"template", "owner"}})

    async def start(self, plan):
        batch_id = str(uuid.UUID(plan["id"]))
        owner = str(plan["owner"])
        total, completed = int(plan["total"]), int(plan.get("completed", 0))
        if not owner or len(owner) > 128 or not 1 <= total <= 1024 or not 0 <= completed <= total:
            raise ValueError("批次参数无效")
        async with self.lock:
            if batch_id in self.jobs:
                return self.public(self.owned(batch_id, owner))
            plan = self.backend.prepare(plan)
            template = copy.deepcopy(plan["template"])
            template["prompt"][plan["seed_node"]]["inputs"][plan["seed_field"]]
            job = {
                "id": batch_id, "owner": owner, "total": total, "completed": completed,
                "template": template, "seed_node": plan["seed_node"], "seed_field": plan["seed_field"],
                "random": bool(plan["random"]), "fixed_seed": str(plan["seed"]),
                "current_seed": str(plan["current_seed"]), "output": str(plan["output"]),
                "state": "between", "desired": "run", "control_version": 0,
                "prompt_id": None, "outputs": [], "error": "", "error_code": "", "missing": 0,
            }
            self.jobs[batch_id] = job
            for key in ("effective_settings", "desktop_workflow", "desktop_modified"):
                if key in plan:
                    job[key] = plan[key]
            self.save()
            return self.public(job)

    async def control(self, batch_id, owner, version, command):
        if command not in {"pause", "resume", "cancel"}:
            raise ValueError("批次操作无效")
        async with self.lock:
            job = self.owned(batch_id, owner)
            if int(version) <= job["control_version"] or job["state"] in TERMINAL:
                return self.public(job)
            job["control_version"] = int(version)
            job["desired"] = {"resume": "run", "pause": "pause", "cancel": "cancel"}[command]
            self.save()
            await self.advance(job)
            return self.public(job)

    async def status(self, batch_id, owner):
        async with self.lock:
            return self.public(self.owned(batch_id, owner))

    async def tick(self):
        async with self.lock:
            for job in list(self.jobs.values()):
                if job["state"] not in TERMINAL and job["state"] != "paused":
                    try:
                        await self.advance(job)
                    except (OSError, asyncio.TimeoutError):
                        # A temporary local I/O failure does not turn a batch into a pause.
                        continue
                    except Exception as error:
                        job["state"] = "error"
                        job["error"] = str(error)
                        lower = str(error).lower()
                        job["error_code"] = "oom" if "out of memory" in lower or "outofmemory" in lower else "execution"
                        self.save()

    async def advance(self, job):
        if job["state"] in TERMINAL:
            return
        pid = job["prompt_id"]
        if pid:
            history, running, pending = self.backend.snapshot(pid)
            if history and history.get("status", {}).get("completed"):
                status = history["status"]
                if status["status_str"] == "error":
                    if job["desired"] != "cancel":
                        raise ValueError(json.dumps(status.get("messages", []), ensure_ascii=False))
                else:
                    images = history.get("outputs", {}).get(job["output"], {}).get("images", [])
                    if not images:
                        raise ValueError("任务结束但没有输出图片")
                    if not any(output["prompt_id"] == pid for output in job["outputs"]):
                        job["outputs"].append({"prompt_id": pid, "index": job["completed"] + 1, "seed": job["current_seed"], "images": copy.deepcopy(images)})
                        job["completed"] += 1
                job["prompt_id"] = None
                job["current_seed"] = None
                self.save()
            elif history and history.get("status", {}).get("status_str") == "error":
                if job["desired"] != "cancel":
                    raise ValueError(json.dumps(history["status"].get("messages", []), ensure_ascii=False))
                job["prompt_id"] = None
            elif job["desired"] == "cancel":
                self.backend.cancel(pid)
                _, running, pending = self.backend.snapshot(pid)
                if running or pending:
                    job["state"] = "cancelling"
                    self.save()
                    return
                job["prompt_id"] = None
            elif running or pending:
                changed = job["missing"] != 0 or job["state"] != ("running" if running else "queued")
                job["missing"] = 0
                job["state"] = "running" if running else "queued"
                if changed:
                    self.save()
                return
            else:
                job["missing"] += 1
                if job["missing"] < 5:
                    return
                # ComfyUI's queue/history are volatile across a computer restart.
                job["prompt_id"] = None
                job["missing"] = 0
                self.save()
        if job["desired"] == "cancel":
            job["state"] = "cancelled"
        elif job["completed"] >= job["total"]:
            job["state"] = "done"
        elif job["desired"] == "pause":
            job["state"] = "paused"
        else:
            index = job["completed"] + 1
            seed = job["current_seed"]
            if seed is None:
                seed = str(secrets.randbits(32)) if job["random"] else job["fixed_seed"]
            pid = str(uuid.uuid5(uuid.UUID(job["id"]), str(index)))
            job["prompt_id"] = pid
            job["current_seed"] = seed
            job["state"] = "submitting"
            self.save()
            await self.backend.submit(job, pid, int(seed))
            job["state"] = "queued"
        self.save()
