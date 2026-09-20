// OmniTech Roadmap — fetches the graph from /api/graph, lets you search for a
// node and renders its production cycle N hops out, and live-refreshes via
// /api/events (SSE) whenever the mod's data files change on disk.

let graph = null; // { nodes, edges, stats, generatedAt }
let nodeById = new Map();
let outEdges = new Map(); // nodeId -> [edge...] where edge.source === nodeId
let inEdges = new Map(); // nodeId -> [edge...] where edge.target === nodeId
let cy = null;
let focusId = null;

const el = (id) => document.getElementById(id);
const statusEl = el('status');
const cyContainer = el('cy');
const emptyState = el('emptyState');
const tooltip = el('tooltip');

// ── Icon frame extraction ────────────────────────────────────────────────
//
// Minecraft animated textures (every fluid still/flow) are a vertical strip
// of square frames — e.g. a 16×320 PNG is 20 stacked 16×16 frames, a 32×512
// one is 16 stacked 32×32 frames. Handing that straight to Cytoscape as a
// node background just squashes the whole strip into the node's square, so
// instead: load the source image once, crop its top-left `min(w,h)` square
// (the first frame — this also covers any texture that's simply bigger than
// its logical frame in general, not just fluids) onto an offscreen canvas
// with smoothing disabled, and use *that* pre-rendered bitmap as the node's
// background. Baking it at a fixed size well above the on-screen node size
// also sidesteps Cytoscape not exposing a public "pixelated" render toggle
// for canvas-drawn backgrounds — by the time Cytoscape scales it down, it's
// already crisp, nearest-neighbour-sampled pixel art.

const ICON_BAKE_PX = 64;
const iconCache = new Map(); // rawUrl -> dataURL ('' = failed/no image)
const iconWatchers = new Map(); // rawUrl -> Set<nodeId> currently displaying it

function extractFirstFrame(url, callback) {
    const img = new Image();
    img.onload = () => {
        const frame = Math.min(img.naturalWidth, img.naturalHeight) || 16;
        const canvas = document.createElement('canvas');
        canvas.width = ICON_BAKE_PX;
        canvas.height = ICON_BAKE_PX;
        const ctx = canvas.getContext('2d');
        ctx.imageSmoothingEnabled = false;
        ctx.drawImage(img, 0, 0, frame, frame, 0, 0, ICON_BAKE_PX, ICON_BAKE_PX);
        callback(canvas.toDataURL());
    };
    img.onerror = () => callback(null);
    img.src = url;
}

/** Returns the baked icon for `rawUrl` if ready, else kicks off extraction and returns null for now. */
function resolvedIcon(rawUrl, nodeId) {
    if (!rawUrl) return null;
    if (!iconWatchers.has(rawUrl)) iconWatchers.set(rawUrl, new Set());
    iconWatchers.get(rawUrl).add(nodeId);

    if (iconCache.has(rawUrl)) return iconCache.get(rawUrl) || null;

    if (iconWatchers.get(rawUrl).size === 1) {
        extractFirstFrame(rawUrl, (dataUrl) => {
            iconCache.set(rawUrl, dataUrl || '');
            const ids = iconWatchers.get(rawUrl);
            if (!cy || !ids) return;
            for (const id of ids) {
                const ele = cy.getElementById(id);
                if (ele.nonempty()) ele.data('icon', dataUrl || null);
            }
            cy.style().update();
        });
    }
    return null;
}

// ── Data loading ──────────────────────────────────────────────────────────

async function loadGraph() {
    setStatus('loading…', '');
    try {
        const res = await fetch('/api/graph');
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'failed to load graph');
        graph = data;
        indexGraph();
        setStatus(`live — ${graph.stats.items} items, ${graph.stats.fluids} fluids, ${graph.stats.recipes} recipes`, 'live');
        renderStats();
        if (focusId && nodeById.has(focusId)) {
            renderFocus(focusId, currentHops());
        } else if (!cy) {
            // First load, nothing focused yet — show everything, arranged in a circle.
            renderCy(graph.nodes, graph.edges, 'circle');
        }
    } catch (err) {
        setStatus(`error: ${err.message}`, 'error');
    }
}

function indexGraph() {
    nodeById = new Map(graph.nodes.map((n) => [n.id, n]));
    outEdges = new Map();
    inEdges = new Map();
    for (const e of graph.edges) {
        if (!outEdges.has(e.source)) outEdges.set(e.source, []);
        outEdges.get(e.source).push(e);
        if (!inEdges.has(e.target)) inEdges.set(e.target, []);
        inEdges.get(e.target).push(e);
    }
}

function setStatus(text, cls) {
    statusEl.textContent = text;
    statusEl.className = 'status' + (cls ? ' ' + cls : '');
}

function renderStats() {
    el('stats').innerHTML = `
        Generated: ${new Date(graph.generatedAt).toLocaleTimeString()}<br/>
        ${graph.stats.items} items · ${graph.stats.fluids} fluids<br/>
        ${graph.stats.recipes} recipes · ${graph.stats.edges} links
    `;
}

// ── Live updates ──────────────────────────────────────────────────────────

function connectEvents() {
    const es = new EventSource('/api/events');
    es.onmessage = () => loadGraph();
    es.onerror = () => {
        setStatus('reconnecting…', 'error');
    };
}

// ── Search ────────────────────────────────────────────────────────────────

const searchInput = el('search');
const suggestionsEl = el('suggestions');

searchInput.addEventListener('input', () => {
    const q = searchInput.value.trim().toLowerCase();
    if (!q) return closeSuggestions();
    const matches = graph
        ? graph.nodes
              .filter((n) => n.kind !== 'recipe' && (n.name.includes(q) || n.label.toLowerCase().includes(q)))
              .slice(0, 30)
        : [];
    renderSuggestions(matches);
});

searchInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
        const q = searchInput.value.trim().toLowerCase();
        const match = graph?.nodes.find((n) => n.kind !== 'recipe' && n.name.toLowerCase() === q);
        if (match) selectNode(match.id);
    }
});

document.addEventListener('click', (e) => {
    if (!suggestionsEl.contains(e.target) && e.target !== searchInput) closeSuggestions();
});

function renderSuggestions(matches) {
    if (!matches.length) return closeSuggestions();
    suggestionsEl.innerHTML = matches
        .map((n) => {
            const icon = n.icon
                ? `<img src="${n.icon}" loading="lazy" />`
                : `<span class="dot" style="background:${n.kind === 'fluid' ? 'var(--fluid)' : 'var(--item)'}"></span>`;
            return `<div class="suggestion" data-id="${n.id}">${icon}<span>${n.label}</span><span style="margin-left:auto;color:var(--text-dim)">${n.namespace}</span></div>`;
        })
        .join('');
    suggestionsEl.classList.add('open');
    for (const row of suggestionsEl.querySelectorAll('.suggestion')) {
        row.addEventListener('click', () => selectNode(row.dataset.id));
    }
}

function closeSuggestions() {
    suggestionsEl.classList.remove('open');
}

function selectNode(id) {
    closeSuggestions();
    const node = nodeById.get(id);
    searchInput.value = node ? node.label : '';
    focusId = id;
    renderFocus(id, currentHops());
}

// ── Hops slider ───────────────────────────────────────────────────────────

const hopsInput = el('hops');
const hopsValue = el('hopsValue');
hopsInput.addEventListener('input', () => {
    hopsValue.textContent = hopsInput.value;
    if (focusId) renderFocus(focusId, currentHops());
});
function currentHops() {
    return parseInt(hopsInput.value, 10);
}

el('hideRecipes').addEventListener('change', () => {
    if (focusId) renderFocus(focusId, currentHops());
    else if (cy) applyRecipeVisibility();
});

el('clearBtn').addEventListener('click', () => {
    focusId = null;
    searchInput.value = '';
    el('focusInfo').innerHTML = '';
    destroyGraph();
    emptyState.classList.remove('hidden');
});

el('fullGraphBtn').addEventListener('click', () => {
    if (!graph) return;
    focusId = null;
    el('focusInfo').innerHTML = `<b>Full graph</b> — ${graph.nodes.length} nodes. This can be slow; use search for a focused view.`;
    renderCy(graph.nodes, graph.edges, 'circle');
});

// ── BFS subgraph extraction (the actual "production cycle" trace) ──────────

function expandFromNode(id, hops) {
    const visitedNodes = new Set([id]);
    const visitedEdges = new Set();
    const distance = new Map([[id, 0]]);
    let frontier = [id];

    for (let h = 0; h < hops; h++) {
        const next = [];
        for (const nid of frontier) {
            for (const e of outEdges.get(nid) || []) {
                visitedEdges.add(e);
                if (!visitedNodes.has(e.target)) {
                    visitedNodes.add(e.target);
                    distance.set(e.target, h + 1);
                    next.push(e.target);
                }
            }
            for (const e of inEdges.get(nid) || []) {
                visitedEdges.add(e);
                if (!visitedNodes.has(e.source)) {
                    visitedNodes.add(e.source);
                    distance.set(e.source, h + 1);
                    next.push(e.source);
                }
            }
        }
        frontier = next;
        if (!frontier.length) break;
    }

    const nodes = [...visitedNodes].map((nid) => nodeById.get(nid)).filter(Boolean);
    const edges = [...visitedEdges];
    return { nodes, edges, distance };
}

function renderFocus(id, hops) {
    const node = nodeById.get(id);
    if (!node) return;
    const { nodes, edges, distance } = expandFromNode(id, hops);

    const producedBy = (inEdges.get(id) || []).filter((e) => e.role === 'output' || e.role === 'byproduct').length;
    const consumedBy = (outEdges.get(id) || []).filter((e) => e.role === 'input' || e.role === 'catalyst').length;

    el('focusInfo').innerHTML = `
        <b>${node.label}</b> <span style="color:var(--text-dim)">(${node.namespace}:${node.name})</span><br/>
        Produced by ${producedBy} recipe(s) · consumed by ${consumedBy} recipe(s)<br/>
        Showing ${nodes.length} nodes, ${hops} hop(s) out.
    `;

    renderCy(nodes, edges, 'concentric', id, distance);
}

// ── Cytoscape rendering ──────────────────────────────────────────────────

function recipeColor(recipeType) {
    let hash = 0;
    for (let i = 0; i < recipeType.length; i++) hash = (hash * 31 + recipeType.charCodeAt(i)) >>> 0;
    const hue = hash % 360;
    return `hsl(${hue}, 55%, 55%)`;
}

function destroyGraph() {
    if (cy) {
        cy.destroy();
        cy = null;
    }
}

function toCyElements(nodes, edges, distance) {
    iconWatchers.clear(); // fresh render — only nodes actually shown should keep an icon alive
    const els = [];
    for (const n of nodes) {
        const dist = distance ? distance.get(n.id) : undefined;
        if (n.kind === 'recipe') {
            els.push({
                data: { id: n.id, label: n.label, kind: 'recipe', recipeType: n.recipeType, dist },
                classes: 'recipe',
            });
        } else {
            els.push({
                data: { id: n.id, label: n.label, kind: n.kind, icon: resolvedIcon(n.icon, n.id), mod: n.mod, dist },
                classes: n.kind + (n.mod ? ' mod' : ' vanilla'),
            });
        }
    }
    for (const e of edges) {
        els.push({
            data: {
                id: `${e.source}->${e.target}:${e.role}:${els.length}`,
                source: e.source,
                target: e.target,
                role: e.role,
                amount: e.amount,
                chance: e.chance,
            },
            classes: e.role,
        });
    }
    return els;
}

/**
 * "concentric" centers the clicked/searched node and rings everything else
 * around it by BFS distance (closer = further-in ring) — exactly "arrange
 * around it". Plain "circle" is used for the unfocused full-graph overview,
 * where there's no single node to center on.
 */
function buildLayoutOptions(layoutName) {
    if (layoutName === 'concentric') {
        return {
            name: 'concentric',
            animate: true,
            animationDuration: 350,
            minNodeSpacing: 28,
            concentric: (node) => {
                const d = node.data('dist');
                return d === undefined ? 0 : 1000 - d; // smaller BFS distance -> larger value -> more central
            },
            levelWidth: () => 1,
        };
    }
    return { name: 'circle', animate: true, animationDuration: 350, spacingFactor: 1.1 };
}

function renderCy(nodes, edges, layoutName, focusNodeId, distance) {
    emptyState.classList.add('hidden');
    destroyGraph();

    cy = cytoscape({
        container: cyContainer,
        elements: toCyElements(nodes, edges, distance),
        style: cyStyle(),
        wheelSensitivity: 0.25,
    });

    const layout = cy.layout(buildLayoutOptions(layoutName));
    layout.run();

    cy.on('tap', 'node', (evt) => {
        const d = evt.target.data();
        if (d.kind === 'recipe') return;
        selectNode(d.id);
    });

    cy.on('mouseover', 'node', (evt) => showTooltip(evt));
    cy.on('mouseout', 'node', () => (tooltip.style.display = 'none'));
    cy.on('mousemove', (evt) => {
        if (tooltip.style.display === 'block') {
            tooltip.style.left = evt.originalEvent.clientX + 14 + 'px';
            tooltip.style.top = evt.originalEvent.clientY + 14 + 'px';
        }
    });

    applyRecipeVisibility();

    if (focusNodeId) {
        const fn = cy.getElementById(focusNodeId);
        if (fn.nonempty()) fn.addClass('focus');
    }

    cy.fit(undefined, 40);
}

function applyRecipeVisibility() {
    if (!cy) return;
    const hide = el('hideRecipes').checked;
    cy.batch(() => {
        cy.nodes('.recipe').style('display', hide ? 'none' : 'element');
        cy.edges().forEach((e) => {
            const hidden = hide && (e.source().hasClass('recipe') || e.target().hasClass('recipe'));
            e.style('display', hidden ? 'none' : 'element');
        });
    });
}

function showTooltip(evt) {
    const d = evt.target.data();
    let html = `<b>${d.label}</b>`;
    if (d.kind === 'recipe') {
        html += `<div class="row">machine: ${d.recipeType}</div>`;
    } else {
        html += `<div class="row">${d.kind} · ${d.mod ? 'omnitech' : 'vanilla'}</div>`;
        const produced = (inEdges.get(d.id) || []).length;
        const consumed = (outEdges.get(d.id) || []).length;
        html += `<div class="row">produced by ${produced} · used by ${consumed}</div>`;
        html += `<div class="row" style="color:var(--accent)">click to refocus here</div>`;
    }
    tooltip.innerHTML = html;
    tooltip.style.display = 'block';
}

function cyStyle() {
    return [
        {
            selector: 'node.item, node.fluid',
            style: {
                width: 34,
                height: 34,
                'background-color': (n) => (n.data('kind') === 'fluid' ? '#264a73' : '#1e4d52'),
                'background-image': (n) => n.data('icon') || 'none',
                'background-fit': 'contain',
                'background-clip': 'node',
                'border-width': 2,
                'border-color': (n) => (n.data('kind') === 'fluid' ? '#6ea8ff' : '#4dd0e1'),
                label: 'data(label)',
                color: '#e7e9ee',
                'font-size': 9,
                'text-valign': 'bottom',
                'text-margin-y': 4,
                'text-wrap': 'wrap',
                'text-max-width': 70,
            },
        },
        {
            selector: 'node.vanilla',
            style: { 'border-style': 'dashed', 'border-color': '#8b93a7' },
        },
        {
            selector: 'node.recipe',
            style: {
                shape: 'diamond',
                width: 26,
                height: 26,
                'background-color': (n) => recipeColor(n.data('recipeType') || 'recipe'),
                label: 'data(recipeType)',
                'font-size': 8,
                color: '#c7cbd6',
                'text-valign': 'top',
                'text-margin-y': -4,
            },
        },
        {
            selector: 'node.focus',
            style: { 'border-width': 4, 'border-color': '#ffd166', 'overlay-color': '#ffd166', 'overlay-opacity': 0.15, 'overlay-padding': 6 },
        },
        {
            selector: 'edge',
            style: {
                width: 1.6,
                'curve-style': 'bezier',
                'target-arrow-shape': 'triangle',
                'target-arrow-scale': 0.8,
                'arrow-scale': 0.8,
                opacity: 0.85,
            },
        },
        { selector: 'edge.input', style: { 'line-color': '#5b8def', 'target-arrow-color': '#5b8def' } },
        { selector: 'edge.output', style: { 'line-color': '#5ed08c', 'target-arrow-color': '#5ed08c' } },
        { selector: 'edge.byproduct', style: { 'line-color': '#7ed99a', 'target-arrow-color': '#7ed99a', 'line-style': 'dashed' } },
        { selector: 'edge.catalyst', style: { 'line-color': '#e0a95b', 'target-arrow-color': '#e0a95b', 'line-style': 'dotted' } },
    ];
}

// ── Boot ──────────────────────────────────────────────────────────────────

loadGraph().then(connectEvents);
