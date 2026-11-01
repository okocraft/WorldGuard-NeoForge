/** Execute real DQM effects against control and protected fixtures. */
import { join } from 'node:path';
import { prepareServer, boot, download } from './server-runtime.mjs';

const runtime = await prepareServer('dqm-effects', '3G');
const assets = [
  {
    "name": "DQMVII-1.06.25.jar",
    "url": "https://github.com/GuriguriGuriguri/DQMVII/releases/download/DQMVII/DQMVII-1.06.25.jar",
    "bytes": 539939699,
    "digest": "sha256:1110dda8355831f7166525b021f1d3a7a30ccd2b758394eea4bdc623888d671e"
  },
  {
    "name": "DQMVII-Voice-1.00.00.jar",
    "url": "https://github.com/GuriguriGuriguri/DQMVII/releases/download/DQMVII/DQMVII-Voice-1.00.00.jar",
    "bytes": 218141086,
    "digest": "sha256:60caefe72334f555a14e1950d569c293a8618087c159379ac6622f6827229b63"
  },
  {
    "name": "DQMVII-Voice-Party-1.00.00.jar",
    "url": "https://github.com/GuriguriGuriguri/DQMVII/releases/download/DQMVII/DQMVII-Voice-Party-1.00.00.jar",
    "bytes": 240885260,
    "digest": "sha256:97acab6ca47ebd1e555b83669a8acc9d69b1b1cea1ea8f5453eefb733ecc9c93"
  },
  {
    "name": "DQMVII-Voice-Extra-1.06.08.jar",
    "url": "https://github.com/GuriguriGuriguri/DQMVII/releases/download/DQMVII/DQMVII-Voice-Extra-1.06.08.jar",
    "bytes": 61971260,
    "digest": "sha256:516878dccc8c4eaeff3e8e8240004abbb933208c8c8b91ed8fa4f2f1cd638fb0"
  }
];
await Promise.all(assets.map(asset => download(asset.url, join(runtime.mods, asset.name), asset.digest.slice(7))));
await boot(runtime, ['say WG_SMOKE_FINISHED'], '', ['DQM_RANGE_PROTECTION_PASSED', 'DQM_BOMIE_PROTECTION_PASSED', 'DQM_MERA_PROTECTION_PASSED'], 300000);
console.log('Real DQM effect protection tests passed.');
