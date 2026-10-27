import json
import os
import re
from pathlib import Path

from aiohttp import web
from server import PromptServer

BASE = Path(__file__).resolve().parent
ARTISTS = json.loads((BASE / 'artists.json').read_text(encoding='utf-8'))
BY_OPTION = {a['option']: a for a in ARTISTS}
BY_ID = {a['id']: a for a in ARTISTS}
DISABLED = '不添加画师（原文直通）'


def pinned_ids():
    path = BASE / 'pinned.json'
    if not path.is_file():
        return []
    return [item for item in json.loads(path.read_text(encoding='utf-8')) if item in BY_ID]


class AnimaArtistGallery:
    @classmethod
    def INPUT_TYPES(cls):
        return {'required': {'prompt': ('STRING', {'forceInput': True}),
                             'artist': ([DISABLED] + list(BY_OPTION), {'default': ARTISTS[0]['option']})}}

    RETURN_TYPES = ('STRING', 'STRING')
    RETURN_NAMES = ('TEXT', 'ARTIST_TAG')
    FUNCTION = 'prepend'
    CATEGORY = 'text/Anima'
    DESCRIPTION = '选择画师，在已翻译的英文前添加 @artist, 。例图是本机Anima同提示词测试，并非画师原作。'

    def prepend(self, prompt, artist):
        if artist == DISABLED:
            return prompt, ''
        if artist not in BY_OPTION:
            raise ValueError('画师选项已失效，请从画师图库重新选择。')
        entry = BY_OPTION[artist]
        tag = '@' + entry['name']
        visual = entry.get('visual_features', '').strip()
        prefix = tag + ', ' + (visual + ' ' if visual else '')
        body = prompt.lstrip()
        if body.casefold().startswith(prefix.casefold()):
            return prompt, tag
        match = re.match(r'^\s*' + re.escape(tag) + r'\s*[,，]\s*', prompt, re.I)
        if match:
            body = prompt[match.end():]
        return prefix + body, tag



@PromptServer.instance.routes.get('/anima_artist_gallery/catalog')
async def artist_catalog(request):
    rows = []
    pinned = pinned_ids()
    for item in ARTISTS:
        path = BASE / 'previews' / (item['id'] + '.png')
        rows.append(dict(item, pinned=item['id'] in pinned, preview_url='/anima_artist_gallery/preview/' + item['id'] if path.is_file() else None,
                         preview_version=path.stat().st_mtime_ns if path.is_file() else 0))
    return web.json_response(rows, headers={'Cache-Control': 'no-store'})


@PromptServer.instance.routes.post('/anima_artist_gallery/pin')
async def pin_artist(request):
    body = await request.json()
    artist_id = body.get('id')
    if artist_id not in BY_ID or type(body.get('pinned')) is not bool:
        raise web.HTTPBadRequest(text='Invalid artist or pinned state')
    pinned = pinned_ids()
    if body['pinned'] and artist_id not in pinned:
        pinned.append(artist_id)
    elif not body['pinned'] and artist_id in pinned:
        pinned.remove(artist_id)
    temporary = BASE / 'pinned.json.tmp'
    temporary.write_text(json.dumps(pinned), encoding='utf-8')
    os.replace(temporary, BASE / 'pinned.json')
    return web.json_response({'id': artist_id, 'pinned': body['pinned']})


@PromptServer.instance.routes.get('/anima_artist_gallery/preview/{artist_id}')
async def artist_preview(request):
    artist_id = request.match_info['artist_id']
    if artist_id not in BY_ID:
        raise web.HTTPNotFound()
    path = BASE / 'previews' / (artist_id + '.png')
    if not path.is_file():
        raise web.HTTPNotFound()
    return web.FileResponse(path, headers={'Cache-Control': 'no-cache'})


NODE_CLASS_MAPPINGS = {'AnimaArtistGallery': AnimaArtistGallery}
NODE_DISPLAY_NAME_MAPPINGS = {'AnimaArtistGallery': 'Anima 画师选择器 · 本地例图库'}
WEB_DIRECTORY = './web'
