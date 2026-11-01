import { createWriteStream } from 'node:fs';
import { mkdir, readFile, writeFile, copyFile, cp } from 'node:fs/promises';
import { execFileSync, spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { createInterface } from 'node:readline';
import { Readable, Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { once } from 'node:events';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';

export const root = fileURLToPath(new URL('../../../', import.meta.url));
const properties = Object.fromEntries((await readFile(join(root, 'gradle.properties'), 'utf8'))
    .split(/\r?\n/).filter(line => line.includes('=') && !line.startsWith('#'))
    .map(line => { const i = line.indexOf('='); return [line.slice(0, i), line.slice(i + 1)]; }));
const neoVersion = properties.neo_version;

export async function download(url, target, expectedSha256) {
    const response = await fetch(url, { signal: AbortSignal.timeout(240000) });
    if (!response.ok) throw new Error('Download failed: ' + response.status + ' ' + url);
    const hash = createHash('sha256');
    const digest = new Transform({ transform(chunk, encoding, callback) {
        hash.update(chunk);
        callback(null, chunk);
    } });
    await pipeline(Readable.fromWeb(response.body), digest, createWriteStream(target));
    if (expectedSha256) assert.equal(hash.digest('hex'), expectedSha256, 'Publisher file digest mismatch');
}

export async function prepareServer(name, heap = '1G') {
    const work = join(root, 'build', name), server = join(work, 'server');
    await mkdir(server, { recursive: true });
    if (name !== 'smoke') {
        await cp(join(root, 'build', 'smoke', 'server', 'libraries'), join(server, 'libraries'), { recursive: true });
    }
    const installer = join(work, 'installer.jar');
    await download('https://maven.neoforged.net/releases/net/neoforged/neoforge/' + neoVersion
        + '/neoforge-' + neoVersion + '-installer.jar', installer);
    execFileSync('java', ['-jar', installer, '--installServer'], {
        cwd: server, timeout: 240000, stdio: 'inherit', windowsHide: true,
    });
    const mods = join(server, 'mods');
    await mkdir(mods, { recursive: true });
    const jarName = 'worldguard-' + properties.mod_version + '.jar';
    await copyFile(join(root, 'build', 'libs', jarName), join(mods, jarName));
    await copyFile(join(root, 'build', 'testmods', 'worldguard-test.jar'), join(mods, 'worldguard-test.jar'));
    await download('https://maven.enginehub.org/repo/com/sk89q/worldedit/worldedit-neoforge-mc26.2/'
        + '7.4.5/worldedit-neoforge-mc26.2-7.4.5-dist.jar', join(mods, 'worldedit-neoforge-mc26.2-7.4.5-dist.jar'));
    await writeFile(join(server, 'eula.txt'), 'eula=true\n');
    await writeFile(join(server, 'server.properties'),
        'server-ip=127.0.0.1\nserver-port=25585\nonline-mode=false\nenforce-secure-profile=false\n'
        + 'view-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-players=2\n'
        + 'pause-when-empty-seconds=-1\nlevel-type=minecraft:flat\n');
    await writeFile(join(server, 'user_jvm_args.txt'), '-Xms256M\n-Xmx' + heap + '\n');
    return { work, server, mods };
}

export async function boot(runtime, commands, suffix, requiredMarkers = [], timeoutMs = 150000) {
    const argsFile = process.platform === 'win32' ? 'win_args.txt' : 'unix_args.txt';
    const argsPath = 'libraries/net/neoforged/neoforge/' + neoVersion + '/' + argsFile;
    const child = spawn('java', ['@user_jvm_args.txt', '@' + argsPath, 'nogui'], {
        cwd: runtime.server, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true,
    });
    const log = createWriteStream(join(runtime.work, 'server-output' + suffix + '.log'));
    let ready = false, initialized = false, protectedEvents = false, failed = false, sent = false;
    const remaining = new Set(requiredMarkers);
    const timeout = setTimeout(() => child.kill(), timeoutMs);
    const consume = stream => {
        const lines = createInterface({ input: stream });
        lines.on('line', line => {
            console.log(line);
            log.write(line + '\n');
            initialized ||= line.includes('WorldGuard Core initialized for NeoForge server');
            protectedEvents ||= line.includes('WG_PROTECTION_TESTS_PASSED');
            failed ||= line.includes('WG_PROTECTION_TESTS_FAILED');
            for (const marker of remaining) if (line.includes(marker)) remaining.delete(marker);
            ready ||= line.includes('Done (');
            if (ready && protectedEvents && !sent) {
                sent = true;
                child.stdin.write(commands.join('\n') + '\n');
            }
            if (line.includes('WG_SMOKE_FINISHED')) child.stdin.write('stop\n');
        });
    };
    consume(child.stdout);
    consume(child.stderr);
    try {
        const [code] = await once(child, 'close');
        assert(initialized, 'WorldGuard region engine did not initialize');
        assert(ready, 'Packaged mods did not reach a running server');
        assert(protectedEvents && !failed, 'Protection test fixture did not complete successfully');
        assert.equal(remaining.size, 0, 'Missing completed test cases: ' + [...remaining].join(', '));
        assert.equal(code, 0, 'Dedicated server exited unsuccessfully');
    } finally {
        clearTimeout(timeout);
        if (child.exitCode === null && child.signalCode === null) child.kill();
        log.end();
    }
}
