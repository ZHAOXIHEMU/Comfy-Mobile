import { app } from '../../scripts/app.js';
import { api } from '../../scripts/api.js';

const disabled = '不添加画师（原文直通）';
const imgUrl = (a) => a.preview_url ? api.apiURL(a.preview_url) + '?v=' + a.preview_version : '';
async function catalog() {
    const r = await api.fetchApi('/anima_artist_gallery/catalog');
    if (!r.ok) throw new Error('画师图库未加载，请重启ComfyUI并刷新网页。');
    return r.json();
}

function openGallery(node, widget, rows, refresh) {
    const dialog = document.createElement('dialog');
    dialog.className = 'anima-artist-dialog';
    Object.assign(dialog.style, { width: 'min(1120px, 94vw)', maxHeight: '90dvh', boxSizing: 'border-box', background: '#20252e', color: '#eef0f6', border: '1px solid #64748b', borderRadius: '14px', padding: '18px' });
    const bar = document.createElement('div');
    Object.assign(bar.style, { display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' });
    const title = document.createElement('strong'); title.textContent = 'Anima 画师选择器';
    const search = document.createElement('input'); search.placeholder = '搜索中文名、外文名或标签…'; search.setAttribute('aria-label', '搜索画师');
    Object.assign(search.style, { flex: '1', minWidth: '180px', padding: '10px', fontSize: '16px', background: '#10151e', color: '#fff', border: '1px solid #657184', borderRadius: '7px' });
    const close = document.createElement('button'); close.textContent = '关闭'; close.onclick = () => dialog.close();
    const pinnedOnly = document.createElement('button'); pinnedOnly.textContent = '只看置顶';
    let onlyPinned = false;
    pinnedOnly.onclick = () => { onlyPinned = !onlyPinned; pinnedOnly.textContent = onlyPinned ? '显示全部' : '只看置顶'; draw(); };
    bar.append(title, search, pinnedOnly, close);
    const note = document.createElement('p');
    note.textContent = '点击卡片选择；☆置顶后排在前面，电脑与手机共用。例图是本机 Anima 的同参数测试，并非画师原作。';
    Object.assign(note.style, { color: '#b8c8d9', fontSize: '13px', lineHeight: '1.5' });
    const grid = document.createElement('div');
    Object.assign(grid.style, { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: '12px', overflowY: 'auto', maxHeight: 'calc(90dvh - 160px)', padding: '3px' });
    function choose(value) {
        widget.value = value; widget.callback?.(value); node.setDirtyCanvas(true, true);
        refresh(); dialog.close();
    }
    function draw() {
        grid.replaceChildren();
        const q = search.value.trim().toLowerCase();
        const sorted = [...rows].sort((a, b) => Number(b.pinned) - Number(a.pinned));
        const options = onlyPinned ? sorted.filter(a => a.pinned) : [{ option: disabled, label: '不添加画师', name: '保留翻译原文' }, ...sorted];
        for (const a of options) {
            if (q && !(a.label + ' ' + a.name + ' ' + (a.visual_features || '')).toLowerCase().includes(q)) continue;
            const card = document.createElement('button'); card.type = 'button'; card.title = a.option;
            Object.assign(card.style, { textAlign: 'left', padding: '7px', cursor: 'pointer', borderRadius: '10px', border: widget.value === a.option ? '2px solid #65d7dc' : '1px solid #475569', background: '#151c27', color: '#f2f4fa', minWidth: '0' });
            if (a.preview_url) {
                const image = document.createElement('img'); image.src = imgUrl(a); image.alt = a.label + '的Anima测试例图'; image.loading = 'lazy';
                Object.assign(image.style, { display: 'block', width: '100%', aspectRatio: '0.684', objectFit: 'contain', borderRadius: '6px', background: '#0e131b' });
                card.append(image);
            } else {
                const blank = document.createElement('div'); blank.textContent = a.option === disabled ? '原文直通' : '例图待生成';
                Object.assign(blank.style, { aspectRatio: '0.684', display: 'grid', placeItems: 'center', background: '#0e131b', color: '#8e9cad', borderRadius: '6px' }); card.append(blank);
            }
            const label = document.createElement('div'); label.textContent = a.label; label.style.cssText = 'font-weight:600;margin-top:8px;overflow-wrap:anywhere';
            const tag = document.createElement('div'); tag.textContent = a.option === disabled ? a.name : '@' + a.name;
            tag.style.cssText = 'font-size:12px;color:#a5c5dd;margin-top:4px;overflow-wrap:anywhere';
            card.append(label, tag);
            if (a.visual_features) { const detail = document.createElement('div'); detail.textContent = a.visual_features; detail.style.cssText = 'font-size:11px;color:#bbc4ce;margin-top:6px;line-height:1.4'; card.append(detail); }
            card.onclick = () => choose(a.option);
            const cell = document.createElement('div'); cell.style.cssText = 'position:relative;min-width:0';
            card.style.width = '100%'; card.style.height = '100%'; cell.append(card);
            if (a.id) {
                const pin = document.createElement('button'); pin.type = 'button';
                pin.textContent = a.pinned ? '★' : '☆'; pin.title = (a.pinned ? '取消置顶 ' : '置顶 ') + a.label;
                pin.setAttribute('aria-label', pin.title);
                pin.style.cssText = 'position:absolute;right:12px;top:12px;min-width:36px;min-height:36px;font-size:24px;line-height:1;border:1px solid #64748b;border-radius:8px;background:#17202ee6;color:#ffd36b;cursor:pointer';
                pin.onclick = async (event) => {
                    event.stopPropagation(); pin.disabled = true;
                    try {
                        const r = await api.fetchApi('/anima_artist_gallery/pin', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id: a.id, pinned: !a.pinned }) });
                        if (!r.ok) throw new Error('置顶保存失败，请检查节点是否已更新并重启。');
                        const result = await r.json(); a.pinned = result.pinned; draw();
                    } catch (error) { note.textContent = error.message; pin.disabled = false; }
                };
                cell.append(pin);
            }
            grid.append(cell);
        }
        if (!grid.children.length) { const empty = document.createElement('p'); empty.textContent = '没有匹配的画师。'; grid.append(empty); }
    }
    search.oninput = draw; dialog.append(bar, note, grid);
    dialog.addEventListener('close', () => dialog.remove(), { once: true });
    dialog.addEventListener('click', e => { if (e.target === dialog) { const r = dialog.getBoundingClientRect(); if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom) dialog.close(); } });
    document.body.append(dialog); draw(); dialog.showModal(); search.focus();
}

app.registerExtension({
    name: 'local.AnimaArtistGallery',
    async beforeRegisterNodeDef(nodeType, nodeData) {
        if (nodeData.name !== 'AnimaArtistGallery') return;
        const original = nodeType.prototype.onNodeCreated;
        nodeType.prototype.onNodeCreated = function () {
            original?.apply(this, arguments);
            const node = this;
            const widget = this.widgets.find(w => w.name === 'artist');
            const box = document.createElement('div');
            Object.assign(box.style, { width: '100%', height: '100%', overflow: 'hidden', color: '#d9e3ed', background: '#131b26', borderRadius: '8px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: '5px', cursor: 'pointer', padding: '6px', boxSizing: 'border-box' });
            box.textContent = '点击上方按钮，按名称和例图选择画师';
            let rows = [];
            const refresh = () => {
                box.replaceChildren(); const a = rows.find(a => a.option === widget.value);
                if (a?.preview_url) {
                    const img = document.createElement('img'); img.src = imgUrl(a); img.alt = a.label;
                    Object.assign(img.style, { width: '100%', minHeight: '0', flex: '1', objectFit: 'contain' }); box.append(img);
                }
                const caption = document.createElement('div'); caption.textContent = a ? a.label + ' · @' + a.name : '不添加画师 · 原文直通';
                Object.assign(caption.style, { fontSize: '12px', textAlign: 'center', overflowWrap: 'anywhere', flexShrink: '0' }); box.append(caption);
            };
            const open = async () => {
                try { rows = await catalog(); openGallery(node, widget, rows, refresh); refresh(); }
                catch (error) { box.textContent = error.message; }
            };
            this.addWidget('button', '选择画师 · 查看例图', null, open, { serialize: false });
            this.addDOMWidget('artist_preview', 'AnimaArtistPreview', box, { serialize: false, hideOnZoom: false });
            box.onclick = open;
            const callback = widget.callback;
            widget.callback = function () { callback?.apply(this, arguments); refresh(); };
            catalog().then(data => { rows = data; refresh(); }).catch(error => { box.textContent = error.message; });
            setTimeout(() => { this.setSize([Math.max(this.size[0], 360), Math.max(this.size[1], 470)]); }, 0);
        };
    },
});
