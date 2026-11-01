/** Packaged startup, protection, command, persistence and optional-permission tests. */
import { readFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import assert from 'node:assert/strict';
import { prepareServer, boot, download } from './server-runtime.mjs';

const runtime = await prepareServer('smoke');
await rm(join(runtime.mods, 'LuckPerms-NeoForge-5.5.85.jar'), { force: true });
await boot(runtime, ['wg version', 'rg flag -w minecraft:overworld __global__ pvp deny',
    'rg info -w minecraft:overworld __global__', 'say WG_SMOKE_FINISHED'], '');
const regionFile = join(runtime.server, 'world', 'worldguard', 'worlds', 'minecraft~overworld', 'regions.yml');
assert((await readFile(regionFile, 'utf8')).includes('pvp: deny'), 'Console region flag was not persisted');
await boot(runtime, ['rg info -w minecraft:overworld __global__', 'say WG_SMOKE_FINISHED'], '-restart');
assert((await readFile(regionFile, 'utf8')).includes('pvp: deny'), 'Region flags were not preserved across restart');
await download('https://cdn.modrinth.com/data/Vebnzrzj/versions/cLEqLHhz/LuckPerms-NeoForge-5.5.85.jar',
    join(runtime.mods, 'LuckPerms-NeoForge-5.5.85.jar'));
await boot(runtime, ['wg version', 'say WG_SMOKE_FINISHED'], '-luckperms', ['WG_LUCKPERMS_TESTS_PASSED']);
console.log('Packaged startup, protection, persistence and LuckPerms integration passed.');
