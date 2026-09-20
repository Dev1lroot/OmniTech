import express from 'express';
import cors from 'cors';
import path from 'node:path';
import fs from 'node:fs';
import chokidar from 'chokidar';
import { fileURLToPath } from 'node:url';
import { buildGraph } from './graph.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const PROJECT_PATH = process.env.PROJECT_PATH || path.resolve(__dirname, '..');
const MODID = process.env.MODID || 'omnitech';
const PORT = process.env.PORT || 4100;

// Newest-first: prefer an exact version match, fall back to the newest
// decompiled sources present so this still works after a version bump.
function findVanillaSources() {
    const decompiled = path.join(PROJECT_PATH, 'decompiled');
    if (!fs.existsSync(decompiled)) return null;
    const candidates = fs
        .readdirSync(decompiled, { withFileTypes: true })
        .filter((e) => e.isDirectory() && e.name.startsWith('minecraft-neoforge-'))
        .map((e) => e.name)
        .sort()
        .reverse();
    return candidates.length ? path.join(decompiled, candidates[0]) : null;
}

const VANILLA_SOURCES = findVanillaSources();

const app = express();
app.use(cors());

const RES = path.join(PROJECT_PATH, 'src/main/resources');
const ASSETS = path.join(RES, `assets/${MODID}`);

app.use('/textures/mod/item', express.static(path.join(ASSETS, 'textures/item')));
app.use('/textures/mod/block', express.static(path.join(ASSETS, 'textures/block')));
app.use('/textures/mod/fluid', express.static(path.join(ASSETS, 'textures/block/fluid')));
if (VANILLA_SOURCES) {
    app.use('/textures/vanilla/item', express.static(path.join(VANILLA_SOURCES, 'assets/minecraft/textures/item')));
    app.use('/textures/vanilla/block', express.static(path.join(VANILLA_SOURCES, 'assets/minecraft/textures/block')));
}
app.use(express.static(path.join(__dirname, 'public')));

// ── Graph cache, rebuilt on demand and pushed to clients on file change ──────

let cachedGraph = null;
let buildError = null;

function rebuild() {
    const startedAt = Date.now();
    try {
        cachedGraph = buildGraph({
            projectPath: PROJECT_PATH,
            modid: MODID,
            vanillaSourcesPath: VANILLA_SOURCES,
        });
        buildError = null;
        console.log(
            `[roadmap] rebuilt graph in ${Date.now() - startedAt}ms — ` +
                `${cachedGraph.stats.items} items, ${cachedGraph.stats.fluids} fluids, ` +
                `${cachedGraph.stats.recipes} recipes, ${cachedGraph.stats.edges} edges`
        );
    } catch (err) {
        buildError = String(err.stack || err);
        console.error('[roadmap] graph build failed:', buildError);
    }
    return cachedGraph;
}

rebuild();

app.get('/api/graph', (req, res) => {
    if (buildError) return res.status(500).json({ error: buildError });
    res.json(cachedGraph);
});

app.get('/api/meta', (req, res) => {
    res.json({
        projectPath: PROJECT_PATH,
        modid: MODID,
        vanillaSources: VANILLA_SOURCES ? path.basename(VANILLA_SOURCES) : null,
    });
});

// ── Live updates: Server-Sent Events, one event per rebuild ─────────────────

const sseClients = new Set();

app.get('/api/events', (req, res) => {
    res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive',
    });
    res.write('retry: 2000\n\n');
    sseClients.add(res);
    req.on('close', () => sseClients.delete(res));
});

function broadcastReload() {
    const payload = `data: ${JSON.stringify({ generatedAt: cachedGraph?.generatedAt, stats: cachedGraph?.stats, error: buildError })}\n\n`;
    for (const client of sseClients) client.write(payload);
}

const watchTargets = [
    path.join(RES, `data/${MODID}`),
    path.join(ASSETS, 'textures'),
    path.join(ASSETS, 'items'),
    path.join(ASSETS, 'models'),
];

let debounceTimer = null;
const watcher = chokidar.watch(watchTargets, {
    ignoreInitial: true,
    awaitWriteFinish: { stabilityThreshold: 150, pollInterval: 50 },
});

watcher.on('all', () => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
        rebuild();
        broadcastReload();
    }, 250);
});

app.listen(PORT, () => {
    console.log(`[roadmap] serving on http://localhost:${PORT}`);
    console.log(`[roadmap] watching ${watchTargets.length} directories under ${PROJECT_PATH}`);
    if (!VANILLA_SOURCES) {
        console.warn('[roadmap] no decompiled vanilla sources found — vanilla icons/recipes will be skipped');
    }
});
