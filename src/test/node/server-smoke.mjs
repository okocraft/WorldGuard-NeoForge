/** Exercise packaged mods and protection events on a fresh loopback-only server. */
import { createWriteStream } from 'node:fs';
import { mkdir, readFile, writeFile, copyFile, rm } from 'node:fs/promises';
import { execFileSync, spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { createInterface } from 'node:readline';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { once } from 'node:events';
import assert from 'node:assert/strict';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const properties = Object.fromEntries((await readFile(join(root, 'gradle.properties'), 'utf8'))
    .split(/\r?\n/).filter(line => line.includes('=') && !line.startsWith('#'))
    .map(line => { const i = line.indexOf('='); return [line.slice(0, i), line.slice(i + 1)]; }));
const neoVersion = properties.neo_version;
const work = join(root, 'build', 'smoke');
const server = join(work, 'server');

async function download(url, target) {
    const response = await fetch(url, { signal: AbortSignal.timeout(180000) });
    if (!response.ok) throw new Error('Download failed: ' + response.status + ' ' + url);
    await pipeline(Readable.fromWeb(response.body), createWriteStream(target));
}

async function boot(commands, suffix, requireLuckPerms = false) {
    const argsFile = process.platform === 'win32' ? 'win_args.txt' : 'unix_args.txt';
    const argsPath = 'libraries/net/neoforged/neoforge/' + neoVersion + '/' + argsFile;
    const child = spawn('java', ['@user_jvm_args.txt', '@' + argsPath, 'nogui'], {
        cwd: server, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true,
    });
    const log = createWriteStream(join(work, 'server-output' + suffix + '.log'));
    let ready = false, initialized = false, protectedEvents = false, luckPermsPassed = false, failed = false, sent = false;
    const timeout = setTimeout(() => child.kill(), 150000);
    const consume = stream => {
        const lines = createInterface({ input: stream });
        lines.on('line', line => {
            console.log(line);
            log.write(line + '\n');
            initialized ||= line.includes('WorldGuard Core initialized for NeoForge server');
            protectedEvents ||= line.includes('WG_PROTECTION_TESTS_PASSED');
            luckPermsPassed ||= line.includes('WG_LUCKPERMS_TESTS_PASSED');
            failed ||= line.includes('WG_PROTECTION_TESTS_FAILED');
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
        assert(protectedEvents && !failed, 'Break/place/use event protection tests did not pass');
        if (requireLuckPerms) assert(luckPermsPassed, 'LuckPerms permission/group integration did not pass');
        assert.equal(code, 0, 'Dedicated server exited unsuccessfully');
    } finally {
        clearTimeout(timeout);
        if (child.exitCode === null && child.signalCode === null) child.kill();
        log.end();
    }
}

await mkdir(server, { recursive: true });
const installer = join(work, 'installer.jar');
await download('https://maven.neoforged.net/releases/net/neoforged/neoforge/' + neoVersion
    + '/neoforge-' + neoVersion + '-installer.jar', installer);
execFileSync('java', ['-jar', installer, '--installServer'], {
    cwd: server, timeout: 180000, stdio: 'inherit', windowsHide: true,
});
const mods = join(server, 'mods');
await mkdir(mods, { recursive: true });
await rm(join(mods, 'LuckPerms-NeoForge-5.5.85.jar'), { force: true });
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
await writeFile(join(server, 'user_jvm_args.txt'), '-Xms256M\n-Xmx1G\n');
await boot(['wg version', 'rg flag -w minecraft:overworld __global__ pvp deny',
    'rg info -w minecraft:overworld __global__', 'say WG_SMOKE_FINISHED'], '');
const regionFile = join(server, 'world', 'worldguard', 'worlds', 'minecraft~overworld', 'regions.yml');
assert((await readFile(regionFile, 'utf8')).includes('pvp: deny'), 'Console region flag was not persisted');
await boot(['rg info -w minecraft:overworld __global__', 'say WG_SMOKE_FINISHED'], '-restart');
assert((await readFile(regionFile, 'utf8')).includes('pvp: deny'), 'Region flags were not preserved across restart');
await download('https://cdn.modrinth.com/data/Vebnzrzj/versions/cLEqLHhz/LuckPerms-NeoForge-5.5.85.jar',
    join(mods, 'LuckPerms-NeoForge-5.5.85.jar'));
await boot(['wg version', 'say WG_SMOKE_FINISHED'], '-luckperms', true);
console.log('Packaged-mod startup, protection events, commands, persistence and LuckPerms integration passed.');
