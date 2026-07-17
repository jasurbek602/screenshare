// Oddiy WebSocket server: Android ilovadan JPEG kadrlarni qabul qiladi
// va ularni ulangan brauzer klientlariga qayta uzatadi.
//
// Ishga tushirish:
//   npm install
//   node server.js
//
// Keyin brauzerda: http://localhost:8080 ni oching.

const WebSocket = require('ws');
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 8080;

// Oddiy HTML sahifani xizmat qiluvchi HTTP server
const server = http.createServer((req, res) => {
    if (req.url === '/' || req.url === '/index.html') {
        const html = fs.readFileSync(path.join(__dirname, 'viewer.html'));
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(html);
    } else {
        res.writeHead(404);
        res.end('Topilmadi');
    }
});

const wss = new WebSocket.Server({ server });

// Android klientlar va brauzer (viewer) klientlarni ajratib turamiz
let androidClient = null;
const viewers = new Set();

wss.on('connection', (ws, req) => {
    console.log('Yangi ulanish:', req.socket.remoteAddress);

    ws.on('message', (data, isBinary) => {
        if (isBinary) {
            // Bu Android ilovadan kelgan JPEG kadr — barcha viewerlarga uzatamiz
            androidClient = ws;
            for (const viewer of viewers) {
                if (viewer.readyState === WebSocket.OPEN) {
                    viewer.send(data, { binary: true });
                }
            }
        } else {
            // Matnli xabar kelsa (masalan brauzerdan "I am viewer" degan signal)
            if (data.toString() === 'viewer') {
                viewers.add(ws);
                console.log('Yangi viewer qo\'shildi. Jami:', viewers.size);
            }
        }
    });

    ws.on('close', () => {
        viewers.delete(ws);
        if (ws === androidClient) androidClient = null;
    });
});

server.listen(PORT, () => {
    console.log(`Server ishga tushdi: http://localhost:${PORT}`);
});
