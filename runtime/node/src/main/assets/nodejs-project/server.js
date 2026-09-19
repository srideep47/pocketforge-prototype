'use strict';

// Static dev server for the on-device web workspace.
//
// Dependency-free on purpose: the phone has no npm registry access at runtime, so
// everything here is core Node 18. Serves the project directory, falls back to
// index.html, and pushes a reload over SSE whenever a file on disk changes.

const fs = require('fs');
const http = require('http');
const path = require('path');

const projectRoot = path.resolve(process.argv[2] || process.cwd());
const port = Number(process.argv[3] || 5173);
const POLL_INTERVAL_MS = 800;

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.htm': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
};

const LIVE_RELOAD_SNIPPET = `
<script>
(function () {
  var source = new EventSource('/__webstudio/reload');
  source.onmessage = function () { window.location.reload(); };
  source.onerror = function () { setTimeout(function () { window.location.reload(); }, 2000); };
})();
</script>
`;

/** Open SSE responses, one per live preview tab. */
const reloadClients = new Set();

function notFound(res, message) {
  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end(message);
}

function serveFile(res, filePath) {
  const extension = path.extname(filePath).toLowerCase();
  const contentType = MIME_TYPES[extension] || 'application/octet-stream';

  if (extension === '.html' || extension === '.htm') {
    // Inject the reload client so edits made in the IDE show up without a manual refresh.
    fs.readFile(filePath, 'utf8', (error, body) => {
      if (error) return notFound(res, 'could not read ' + filePath);
      const injected = body.includes('</body>')
        ? body.replace('</body>', LIVE_RELOAD_SNIPPET + '</body>')
        : body + LIVE_RELOAD_SNIPPET;
      res.writeHead(200, { 'Content-Type': contentType, 'Cache-Control': 'no-store' });
      res.end(injected);
    });
    return;
  }

  res.writeHead(200, { 'Content-Type': contentType, 'Cache-Control': 'no-store' });
  fs.createReadStream(filePath).on('error', () => res.end()).pipe(res);
}

function resolveRequestPath(urlPath) {
  const decoded = decodeURIComponent(urlPath.split('?')[0]);
  const candidate = path.normalize(path.join(projectRoot, decoded));
  // Never escape the project directory, whatever the URL says.
  if (!candidate.startsWith(projectRoot)) return null;
  return candidate;
}

const server = http.createServer((req, res) => {
  if (req.url.startsWith('/__webstudio/reload')) {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    });
    res.write('retry: 1000\n\n');
    reloadClients.add(res);
    req.on('close', () => reloadClients.delete(res));
    return;
  }

  const target = resolveRequestPath(req.url);
  if (target === null) return notFound(res, 'outside the project root');

  fs.stat(target, (error, stats) => {
    if (!error && stats.isFile()) return serveFile(res, target);

    if (!error && stats.isDirectory()) {
      const index = path.join(target, 'index.html');
      if (fs.existsSync(index)) return serveFile(res, index);
    }

    // SPA fallback so client-side routes keep working.
    const rootIndex = path.join(projectRoot, 'index.html');
    if (fs.existsSync(rootIndex)) return serveFile(res, rootIndex);

    notFound(res, 'no index.html in ' + projectRoot);
  });
});

/**
 * Snapshots mtimes under the project root. `fs.watch` has no recursive mode on Linux,
 * and a project here is a handful of files, so a cheap poll is the honest option.
 */
function snapshot(dir, accumulator) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch (error) {
    return accumulator;
  }
  for (const entry of entries) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      snapshot(full, accumulator);
    } else {
      try {
        accumulator[full] = fs.statSync(full).mtimeMs;
      } catch (error) {
        /* file vanished mid-walk */
      }
    }
  }
  return accumulator;
}

let previous = snapshot(projectRoot, {});
setInterval(() => {
  const current = snapshot(projectRoot, {});
  const keys = new Set([...Object.keys(previous), ...Object.keys(current)]);
  let changed = false;
  for (const key of keys) {
    if (previous[key] !== current[key]) {
      changed = true;
      break;
    }
  }
  previous = current;
  if (!changed) return;
  for (const client of reloadClients) {
    client.write('data: reload\n\n');
  }
}, POLL_INTERVAL_MS).unref();

server.listen(port, '127.0.0.1', () => {
  console.log('[webstudio] serving ' + projectRoot + ' on http://localhost:' + port);
});

server.on('error', (error) => {
  console.error('[webstudio] server error: ' + error.message);
});
