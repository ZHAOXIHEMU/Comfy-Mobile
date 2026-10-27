"""Real HTTP integration with production addon routes and Android engine; synthetic inference only."""
import asyncio
import binascii
import copy
import importlib.util
import json
from pathlib import Path
import struct
import sys
import tempfile
import types
import zlib
from aiohttp import web

ROOT = Path(__file__).parent


class Queue:
    def __init__(self):
        self.pending = []
        self.running = []
        self.history = {}

    def get_history(self, prompt_id):
        return {prompt_id: self.history[prompt_id]} if prompt_id in self.history else {}

    def get_current_queue_volatile(self):
        return self.running[:], self.pending[:]

    def put(self, item):
        self.pending.append(item)

    def delete_queue_item(self, predicate):
        self.pending = [item for item in self.pending if not predicate(item)]

    def interrupt_if_running(self, pid):
        for item in self.running[:]:
            if item[1] == pid:
                self.running.remove(item)
                self.history[pid] = {"status": {"completed": False, "status_str": "error", "messages": ["interrupted"]}}

    async def infer(self):
        while True:
            if self.pending:
                item = self.pending.pop(0)
                self.running.append(item)
                await asyncio.sleep(0.08)
                if item in self.running:
                    self.running.remove(item)
                    self.history[item[1]] = {"status": {"completed": True, "status_str": "success"}, "outputs": {item[4][0]: {"images": [{"filename": item[1] + ".png", "type": "output", "subfolder": ""}]}}}
            await asyncio.sleep(0.01)


async def validate(pid, prompt, targets):
    output = "50" if "50" in prompt else "29"
    return True, None, [output], {}


def png():
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", binascii.crc32(kind + data) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 2, 2, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(b"\0\xff\0\0\xff\0\0" * 2)) + chunk(b"IEND", b"")


async def main():
    with tempfile.TemporaryDirectory() as directory:
        bundle = json.loads((ROOT / "assets/workflows.json").read_text(encoding="utf-8"))
        names = {"text": "手机文生图.json", "single": "手机图改图 (单图）.json", "dual": "手机图改图 (双图）.json", "anima": "手机Anima文生图.json"}
        workflow_directory = Path(directory) / "default" / "workflows"
        workflow_directory.mkdir(parents=True)
        for key, filename in names.items():
            nodes, links = [], []
            for node_id, item in bundle[key]["prompt"].items():
                fields, values = [], []
                for name, value in item["inputs"].items():
                    if isinstance(value, list) and len(value) == 2 and isinstance(value[0], str):
                        number = len(links) + 1
                        links.append([number, value[0], value[1], node_id, len(fields), "*"])
                        fields.append({"name": name, "link": number})
                    else:
                        fields.append({"name": name, "link": None, "widget": {"name": name}})
                        values.append(value)
                nodes.append({"id": node_id, "type": item["class_type"], "inputs": fields, "widgets_values": values})
            (workflow_directory / filename).write_text(json.dumps({"nodes": nodes, "links": links}, ensure_ascii=False), encoding="utf-8")
        queue = Queue()
        app = web.Application()
        server = types.SimpleNamespace(app=app, routes=web.RouteTableDef(), loop=asyncio.get_running_loop(), prompt_queue=queue, number=0, node_replace_manager=types.SimpleNamespace(apply_replacements=lambda prompt: None), trigger_on_prompt=lambda body: body)
        sys.modules["server"] = types.SimpleNamespace(PromptServer=types.SimpleNamespace(instance=server))
        sys.modules["execution"] = types.SimpleNamespace(validate_prompt=validate)
        sys.modules["folder_paths"] = types.SimpleNamespace(get_user_directory=lambda: directory)
        path = ROOT / "../computer/custom_nodes/comfy-mobile-batch/__init__.py"
        spec = importlib.util.spec_from_file_location("mobile_integration_plugin", path, submodule_search_locations=[str(path.parent)])
        plugin = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = plugin
        spec.loader.exec_module(plugin)
        app.add_routes(server.routes)
        app.router.add_get("/view", lambda request: web.Response(body=png(), content_type="image/png"))
        runner = web.AppRunner(app)
        await runner.setup()
        site = web.TCPSite(runner, "127.0.0.1", 0)
        await site.start()
        port = site._server.sockets[0].getsockname()[1]
        inference = asyncio.create_task(queue.infer())
        java = Path("C:/Program Files/Java/jdk-21.0.10/bin/java.exe")
        try:
            process = await asyncio.create_subprocess_exec(str(java), "-Dfile.encoding=UTF-8", "-cp", "build/native-tests;build/json-tests.jar", "com.comfymobile.app.RemoteBatchHttpTest", f"http://127.0.0.1:{port}", cwd=ROOT)
            code = await process.wait()
            if code:
                raise RuntimeError(f"HTTP integration failed: {code}")
        finally:
            plugin.task.cancel()
            inference.cancel()
            await asyncio.gather(plugin.task, inference, return_exceptions=True)
            await runner.cleanup()


if __name__ == "__main__":
    asyncio.run(main())
