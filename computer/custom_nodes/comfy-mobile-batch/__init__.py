import asyncio
import copy
import logging
import time
from pathlib import Path

from aiohttp import web
import execution
import folder_paths
from server import PromptServer

from .manager import BatchManager
from .workflows import resolve_plan


class ComfyBackend:
    def __init__(self, server):
        self.server = server

    def prepare(self, plan):
        return resolve_plan(plan, Path(folder_paths.get_user_directory()) / "default" / "workflows")

    def snapshot(self, pid):
        queue = self.server.prompt_queue
        history = queue.get_history(prompt_id=pid).get(pid)
        running, pending = queue.get_current_queue_volatile()
        return history, any(item[1] == pid for item in running), any(item[1] == pid for item in pending)

    def cancel(self, pid):
        self.server.prompt_queue.delete_queue_item(lambda item: item[1] == pid)
        self.server.prompt_queue.interrupt_if_running(pid)

    async def submit(self, job, pid, seed):
        history, running, pending = self.snapshot(pid)
        if history or running or pending:
            return
        body = copy.deepcopy(job["template"])
        body["prompt"][job["seed_node"]]["inputs"][job["seed_field"]] = seed
        meta = body["extra_data"]["extra_pnginfo"]["comfy_mobile"]
        meta.update(batch_index=job["completed"] + 1, batch_total=job["total"], seed=str(seed))
        body = self.server.trigger_on_prompt(body)
        prompt = body["prompt"]
        self.server.node_replace_manager.apply_replacements(prompt)
        valid = await execution.validate_prompt(pid, prompt, None)
        if not valid[0]:
            raise ValueError(str(valid[1]))
        extra = body["extra_data"]
        extra["client_id"] = body["client_id"]
        extra["create_time"] = int(time.time() * 1000)
        number = self.server.number
        self.server.number += 1
        self.server.prompt_queue.put((number, pid, prompt, extra, valid[2], {}))


server = PromptServer.instance
manager = BatchManager(Path(folder_paths.get_user_directory()) / "comfy_mobile" / "batches.json", ComfyBackend(server))


@server.routes.get("/comfy-mobile/capabilities")
async def capabilities(request):
    return web.json_response({"batch_api": 2, "computer_owned": True, "desktop_workflows": True})


@server.routes.post("/comfy-mobile/batches")
async def start(request):
    try:
        return web.json_response(await manager.start(await request.json()))
    except (KeyError, ValueError, TypeError, OSError) as error:
        return web.json_response({"error": str(error)}, status=400)


@server.routes.get("/comfy-mobile/batches/{batch_id}")
async def status(request):
    try:
        return web.json_response(await manager.status(request.match_info["batch_id"], request.query["owner"]))
    except KeyError:
        return web.json_response({"error": "批次不存在"}, status=404)


@server.routes.post("/comfy-mobile/batches/{batch_id}/control")
async def control(request):
    try:
        body = await request.json()
        return web.json_response(await manager.control(request.match_info["batch_id"], body["owner"], body["version"], body["command"]))
    except (KeyError, ValueError, TypeError) as error:
        return web.json_response({"error": str(error)}, status=400)


async def run():
    while True:
        try:
            await manager.tick()
        except OSError:
            logging.exception("Comfy Mobile: cannot persist batch state")
        await asyncio.sleep(0.5)


task = None


async def startup(app):
    global task
    task = asyncio.create_task(run())


async def cleanup(app):
    task.cancel()
    await asyncio.gather(task, return_exceptions=True)


server.app.on_startup.append(startup)
server.app.on_cleanup.append(cleanup)
NODE_CLASS_MAPPINGS = {}
NODE_DISPLAY_NAME_MAPPINGS = {}
