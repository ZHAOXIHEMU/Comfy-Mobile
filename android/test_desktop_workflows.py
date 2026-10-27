import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import uuid

ROOT = Path(__file__).parent
SOURCE = ROOT.parent / "workflows"
spec = importlib.util.spec_from_file_location("desktop_workflows", ROOT / "../computer/custom_nodes/comfy-mobile-batch/workflows.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class DesktopWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        for definition in module.WORKFLOWS.values():
            filename = definition[0]
            (self.directory / filename).write_bytes((SOURCE / filename).read_bytes())
        self.bundle = json.loads((ROOT / "assets/workflows.json").read_text(encoding="utf-8"))

    def plan(self, key, model="base"):
        workflow = self.bundle[key]
        return {"id": str(uuid.uuid4()), "owner": "test-phone", "total": 4, "completed": 0, "workflow": key, "current_seed": "1234", "random": False, "seed": "1234", "seed_node": workflow["seed"], "seed_field": workflow["seedField"], "output": workflow["output"], "template": {"prompt": copy.deepcopy(workflow["prompt"]), "client_id": "test-phone", "extra_data": {"extra_pnginfo": {"comfy_mobile": {"mode": "anime" if key == "anima" else "text"}}}}, "mobile_inputs": {"positive": "一只白猫坐在窗边", "negative": "blurry", "width": 768, "height": 1024, "scale": .5, "image1": "mobile-first.png", "image2": "mobile-second.png", "animaModel": model, "artistOption": "WLOP · wlop"}}

    def update_node(self, key, node_id, index, value):
        path = self.directory / module.WORKFLOWS[key][0]
        graph = json.loads(path.read_text(encoding="utf-8"))
        next(node for node in graph["nodes"] if node["id"] == node_id)["widgets_values"][index] = value
        path.write_text(json.dumps(graph, ensure_ascii=False), encoding="utf-8")

    def test_saved_translation_model_cfg_and_steps_override_old_apk_template(self):
        self.update_node("text", 78, 0, "new-translator.gguf")
        self.update_node("text", 40, 0, 7.5)
        self.update_node("text", 43, 1, 21)
        plan = self.plan("text")
        resolved = module.resolve_plan(plan, self.directory)
        prompt = resolved["template"]["prompt"]
        self.assertEqual(prompt["78"]["inputs"]["model_name"], "new-translator.gguf")
        self.assertEqual(prompt["40"]["inputs"]["cfg"], 7.5)
        self.assertEqual(prompt["43"]["inputs"]["steps"], 21)
        self.assertEqual(resolved["effective_settings"], {"steps": 21, "cfg": 7.5, "translationModel": "new-translator.gguf"})
        self.assertEqual(prompt["80"]["inputs"]["text"], "一只白猫坐在窗边")
        self.assertEqual(prompt["37"]["inputs"]["text"], "blurry")
        self.assertEqual(prompt["10"]["inputs"]["width"], 768)
        self.assertEqual(prompt["10"]["inputs"]["height"], 1024)
        self.assertEqual(prompt["41"]["inputs"]["noise_seed"], 1234)
        self.update_node("text", 40, 0, 9.5)
        next_batch = module.resolve_plan(self.plan("text"), self.directory)
        self.assertEqual(next_batch["template"]["prompt"]["40"]["inputs"]["cfg"], 9.5)
        self.assertEqual(prompt["40"]["inputs"]["cfg"], 7.5)

    def test_image_modes_keep_desktop_sampler_and_phone_images_scale(self):
        for key in ("single", "dual"):
            self.update_node(key, 53, 2, 17)
            self.update_node(key, 53, 3, 2.5)
            self.update_node(key, 121, 0, "other-translator.gguf")
            resolved = module.resolve_plan(self.plan(key), self.directory)
            prompt = resolved["template"]["prompt"]
            self.assertEqual(prompt["53"]["inputs"]["steps"], 17)
            self.assertEqual(prompt["53"]["inputs"]["cfg"], 2.5)
            self.assertEqual(prompt["121"]["inputs"]["model_name"], "other-translator.gguf")
            self.assertEqual(prompt["72"]["inputs"]["image"], "mobile-first.png")
            self.assertEqual(prompt["mobile_width"]["inputs"]["values.b"], .5)
            self.assertEqual(prompt["123"]["inputs"]["text"], "一只白猫坐在窗边")
            if key == "dual":
                self.assertEqual(prompt["90"]["inputs"]["image"], "mobile-second.png")
            else:
                self.assertNotIn("90", prompt)
                self.assertNotIn("image_b", prompt["84"]["inputs"])

    def test_only_anima_model_steps_cfg_have_fixed_presets(self):
        self.update_node("anima", 19, 2, 88)
        self.update_node("anima", 19, 3, 12)
        self.update_node("anima", 49, 0, "latest-translator.gguf")
        for variant, steps, cfg in (("base", 32, 4), ("turbo", 10, 1)):
            resolved = module.resolve_plan(self.plan("anima", variant), self.directory)
            prompt = resolved["template"]["prompt"]
            self.assertEqual(prompt["19"]["inputs"]["steps"], steps)
            self.assertEqual(prompt["19"]["inputs"]["cfg"], cfg)
            self.assertEqual(prompt["49"]["inputs"]["model_name"], "latest-translator.gguf")
            self.assertEqual(prompt["58"]["inputs"]["artist"], "WLOP · wlop")
            self.assertEqual(prompt["11"]["inputs"]["text"], ["58", 0])

    def test_unknown_workflow_cannot_read_arbitrary_paths(self):
        plan = self.plan("text")
        plan["workflow"] = "../../outside"
        with self.assertRaises(KeyError):
            module.resolve_plan(plan, self.directory)


if __name__ == "__main__":
    unittest.main()
