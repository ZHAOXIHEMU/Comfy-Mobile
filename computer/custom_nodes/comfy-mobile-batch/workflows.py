import copy
import json
from pathlib import Path


WORKFLOWS = {
    "text": ("手机文生图.json", "50", "80", "37", "10", "41", "noise_seed"),
    "single": ("手机图改图 (单图）.json", "29", "123", "85", "82", "53", "seed"),
    "dual": ("手机图改图 (双图）.json", "29", "123", "85", "82", "53", "seed"),
    "anima": ("手机Anima文生图.json", "46", "51", "12", "28", "19", "seed"),
}
ANIMA_MODELS = {"base": ("anima-base-v1.0.safetensors", 32, 4), "turbo": ("anima-turbo-v1.1.safetensors", 10, 1)}


def api_graph(graph, output):
    nodes = {str(node["id"]): node for node in graph["nodes"]}
    links = {link[0]: link for link in graph["links"]}
    prompt = {}

    def visit(node_id):
        node_id = str(node_id)
        node = nodes.get(node_id)
        if node is None or node.get("mode", 0) in (2, 4):
            return False
        if node_id in prompt:
            return True
        item = {"class_type": node["type"], "inputs": {}, "_meta": {"title": node.get("title", node["type"])}}
        prompt[node_id] = item
        values = node.get("widgets_values", [])
        index = 0
        for field in node.get("inputs", []):
            name = field["name"]
            if field.get("widget"):
                if name in ("upload", "preview"):
                    continue
                if index < len(values) and field.get("link") is None:
                    item["inputs"][name] = copy.deepcopy(values[index])
                index += 1
                if name in ("seed", "noise_seed") and index < len(values) and values[index] in ("randomize", "fixed", "increment", "decrement"):
                    index += 1
            if field.get("link") is not None:
                link = links[field["link"]]
                if visit(link[1]):
                    item["inputs"][name] = [str(link[1]), link[2]]
        return True

    visit(output)
    return prompt


def resolve_plan(plan, directory):
    # Old in-flight clients can finish their already captured templates.
    if "mobile_inputs" not in plan:
        return plan
    key = plan["workflow"]
    filename, output, positive, negative, size, seed_node, seed_field = WORKFLOWS[key]
    path = Path(directory) / filename
    graph = json.loads(path.read_text(encoding="utf-8-sig"))
    prompt = api_graph(graph, output)
    mobile = plan["mobile_inputs"]
    if prompt[positive]["class_type"] != "ExcelPromptGlossary":
        raise ValueError("正向提示词入口已改变，请更新手机流程映射")
    prompt[positive]["inputs"]["text"] = mobile["positive"]
    prompt[negative]["inputs"]["prompt" if key in ("single", "dual") else "text"] = mobile.get("negative", "")
    prompt[seed_node]["inputs"][seed_field] = int(plan["current_seed"])
    if key in ("single", "dual"):
        prompt["72"]["inputs"]["image"] = mobile["image1"]
        if key == "dual":
            prompt["90"]["inputs"]["image"] = mobile["image2"]
        for axis, slot in (("width", 1), ("height", 2)):
            node_id = "mobile_" + axis
            prompt[node_id] = {"class_type": "ComfyMathExpression", "inputs": {"expression": "max(64, round(a * b / 8) * 8)", "values.a": ["118", slot], "values.b": float(mobile.get("scale", 1))}}
            prompt[size]["inputs"][axis] = [node_id, 1]
    else:
        prompt[size]["inputs"].update(width=int(mobile["width"]), height=int(mobile["height"]))
    prompt[size]["inputs"]["batch_size"] = 1
    if key == "anima":
        model, steps, cfg = ANIMA_MODELS[mobile.get("animaModel", "base")]
        prompt["44"]["inputs"]["unet_name"] = model
        prompt["19"]["inputs"].update(steps=steps, cfg=cfg)
        if mobile.get("artistOption"):
            prompt["58"]["inputs"]["artist"] = mobile["artistOption"]
    sampler = prompt[seed_node]["inputs"]
    effective = {"steps": sampler.get("steps", prompt.get("43", {}).get("inputs", {}).get("steps")), "cfg": sampler.get("cfg", prompt.get("40", {}).get("inputs", {}).get("cfg"))}
    for node in prompt.values():
        if node["class_type"] == "LlamaCPPModelLoader":
            effective["translationModel"] = node["inputs"]["model_name"]
    resolved = copy.deepcopy(plan)
    resolved["template"]["prompt"] = prompt
    resolved.update(output=output, seed_node=seed_node, seed_field=seed_field, effective_settings={key: value for key, value in effective.items() if value is not None}, desktop_workflow=filename, desktop_modified=str(path.stat().st_mtime_ns))
    return resolved
