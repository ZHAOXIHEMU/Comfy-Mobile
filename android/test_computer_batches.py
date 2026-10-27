import asyncio
import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest
import uuid

ROOT = Path(__file__).parent
spec = importlib.util.spec_from_file_location("mobile_manager", ROOT / "../computer/custom_nodes/comfy-mobile-batch/manager.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
BatchManager = module.BatchManager


class Backend:
    def prepare(self, plan):
        return plan

    def __init__(self):
        self.running = set()
        self.history = {}
        self.submitted = []
        self.cancelled = []

    def snapshot(self, pid):
        return self.history.get(pid), pid in self.running, False

    async def submit(self, job, pid, seed):
        self.running.add(pid)
        self.submitted.append((pid, seed))

    def complete(self, pid, error=False):
        self.running.discard(pid)
        self.history[pid] = {"status": {"completed": not error, "status_str": "error" if error else "success", "messages": ["CUDA out of memory"] if error else []}, "outputs": {"50": {"images": [{"filename": pid + ".png", "subfolder": "", "type": "output"}]}}}

    def cancel(self, pid):
        self.cancelled.append(pid)
        self.running.discard(pid)


class ComputerBatchTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "batches.json"
        self.backend = Backend()
        self.manager = BatchManager(self.path, self.backend)
        self.plan = {"id": str(uuid.uuid4()), "owner": "phone-client", "total": 4, "completed": 0, "seed_node": "41", "seed_field": "noise_seed", "random": False, "seed": "1234", "current_seed": "1234", "output": "50", "template": {"prompt": {"41": {"inputs": {"noise_seed": 1234}}}}}

    async def command(self, version, command):
        return await self.manager.control(self.plan["id"], "phone-client", version, command)

    async def test_entire_batch_continues_without_phone_polling(self):
        await self.manager.start(self.plan)
        for _ in range(6):
            await self.manager.tick()
            for pid in list(self.backend.running):
                self.backend.complete(pid)
        result = await self.manager.status(self.plan["id"], "phone-client")
        self.assertEqual(result["state"], "done")
        self.assertEqual(len(result["outputs"]), 4)
        self.assertEqual(len(self.backend.submitted), 4)
        self.assertTrue(all(seed == 1234 for _, seed in self.backend.submitted))

    async def test_idempotent_start_and_computer_restart(self):
        await self.manager.start(self.plan)
        await self.manager.tick()
        await self.manager.start(self.plan)
        self.manager = BatchManager(self.path, self.backend)
        await self.manager.tick()
        self.assertEqual(len(self.backend.submitted), 1)
        self.backend.complete(self.backend.submitted[0][0])
        await self.manager.tick()
        self.assertEqual(len(self.backend.submitted), 2)
        self.assertEqual(len(self.manager.jobs[self.plan["id"]]["outputs"]), 1)

    async def test_pause_finishes_current_then_resume_and_cancel(self):
        await self.manager.start(self.plan)
        await self.manager.tick()
        await self.command(1, "pause")
        self.assertEqual(len(self.backend.submitted), 1)
        self.backend.complete(self.backend.submitted[0][0])
        await self.manager.tick()
        self.assertEqual(self.manager.jobs[self.plan["id"]]["state"], "paused")
        await self.manager.tick()
        self.assertEqual(len(self.backend.submitted), 1)
        await self.command(2, "resume")
        self.assertEqual(len(self.backend.submitted), 2)
        self.backend.running.add("unrelated-prompt")
        await self.command(3, "cancel")
        for _ in range(5):
            await self.manager.tick()
        self.assertEqual(len(self.backend.submitted), 2)
        self.assertEqual(self.manager.jobs[self.plan["id"]]["state"], "cancelled")
        self.assertEqual(self.backend.cancelled, [self.backend.submitted[1][0]])
        self.assertIn("unrelated-prompt", self.backend.running)
        self.assertEqual(len(self.manager.jobs[self.plan["id"]]["outputs"]), 1)

    async def test_out_of_order_commands_cannot_resume_cancelled_batch(self):
        await self.manager.start(self.plan)
        await self.command(1, "pause")
        await self.command(3, "resume")
        await self.command(2, "pause")
        self.assertEqual(self.manager.jobs[self.plan["id"]]["desired"], "run")
        await self.command(4, "cancel")
        await self.command(5, "resume")
        self.assertEqual(self.manager.jobs[self.plan["id"]]["state"], "cancelled")

    async def test_oom_stops_remaining_and_is_reported(self):
        await self.manager.start(self.plan)
        await self.manager.tick()
        self.backend.complete(self.backend.submitted[0][0], error=True)
        await self.manager.tick()
        self.assertEqual(self.manager.jobs[self.plan["id"]]["error_code"], "oom")
        self.assertEqual(self.manager.jobs[self.plan["id"]]["state"], "error")
        self.assertEqual(len(self.backend.submitted), 1)

    async def test_random_seeds_and_owner_boundary(self):
        self.plan["random"] = True
        await self.manager.start(self.plan)
        with self.assertRaises(KeyError):
            await self.manager.status(self.plan["id"], "different-phone")
        for _ in range(6):
            await self.manager.tick()
            for pid in list(self.backend.running):
                self.backend.complete(pid)
        self.assertEqual(len({pid for pid, _ in self.backend.submitted}), 4)
        self.assertGreater(len({seed for _, seed in self.backend.submitted}), 1)
        self.assertNotIn("template", await self.manager.status(self.plan["id"], "phone-client"))

    async def test_cleared_volatile_queue_recovers_same_reserved_id(self):
        await self.manager.start(self.plan)
        await self.manager.tick()
        first = self.backend.submitted[0]
        self.backend.running.clear()
        self.manager = BatchManager(self.path, self.backend)
        for _ in range(5):
            await self.manager.tick()
        self.assertEqual(self.backend.submitted[-1], first)


if __name__ == "__main__":
    unittest.main()
