// Daily Play rank tracker. Appends to ranks.csv and apps.csv under DATA_DIR.
const gplay = require('google-play-scraper').default || require('google-play-scraper');
const fs = require('fs');
const os = require('os');
const path = require('path');

const OURS = 'com.simplecityapps.shuttle';
const DATA = process.env.ASO_DATA_DIR || path.join(os.homedir(), '.local/share/shuttle-music-aso');
// Keywords before the first [country/lang] header are English defaults; a header scopes its keywords to that run.
const SECTIONS = {};
let section = 'en';
for (const line of fs.readFileSync(path.join(__dirname, 'keywords.txt'), 'utf8').split('\n').map(s => s.trim())) {
  if (!line || line.startsWith('#')) continue;
  const header = line.match(/^\[(\w+)\/(\w+)\]$/);
  if (header) section = `${header[1]}/${header[2]}`;
  else (SECTIONS[section] = SECTIONS[section] || []).push(line);
}
const keywordsFor = (country, lang) => SECTIONS[`${country}/${lang}`] || (lang.startsWith('en') ? SECTIONS.en : []);
const RUNS = [['us', 'en'], ['gb', 'en'], ['gb', 'en-GB'], ['au', 'en'], ['au', 'en-AU'], ['ca', 'en'], ['in', 'en'],
  ['us', 'es'], ['mx', 'es'], ['br', 'pt'], ['ru', 'ru'], ['fr', 'fr'], ['de', 'de']];
const APPS = [OURS, 'app.symfonik.music.player', 'com.unicornsonlsd.finamp', 'com.maxmpz.audioplayer',
  'in.krosbits.musicolet', 'tv.plex.labs.plexamp', 'com.ghenry22.substream2', 'org.moire.ultrasonic',
  'com.rhmsoft.pulsar', 'com.piyush.music', 'code.name.monkey.retromusic'];

const sleep = ms => new Promise(r => setTimeout(r, ms));
const withTimeout = p => Promise.race([p, new Promise((_, j) => setTimeout(() => j(new Error('timeout')), 20000))]);

async function retry(fn) {
  for (let attempt = 0; ; attempt++) {
    try { return await withTimeout(fn()); } catch (e) {
      // The scraper throws a TypeError on an empty result page; that means zero hits.
      if (/map|null|undefined/.test(e.message) && !/timeout/.test(e.message)) return [];
      if (attempt >= 3) throw e;
      await sleep(2000 * 2 ** attempt);
    }
  }
}

function appendCsv(file, header, rows) {
  const f = path.join(DATA, file);
  const q = v => /[",\n]/.test(String(v)) ? '"' + String(v).replace(/"/g, '""') + '"' : String(v);
  if (!fs.existsSync(f)) fs.writeFileSync(f, header + '\n');
  const cur = fs.readFileSync(f, 'utf8');
  if (cur && !cur.endsWith('\n')) fs.appendFileSync(f, '\n');
  fs.appendFileSync(f, rows.map(r => r.map(q).join(',')).join('\n') + '\n');
}

(async () => {
  fs.mkdirSync(DATA, { recursive: true });
  const date = new Date().toLocaleDateString('en-CA');
  const limit = Number(process.env.ASO_LIMIT || Infinity);
  const rows = [];
  let errors = 0;
  for (const [country, lang] of RUNS) {
    for (const k of keywordsFor(country, lang).slice(0, limit)) {
      try {
        const r = await retry(() => gplay.search({ term: k, num: 250, country, lang }));
        const idx = r.findIndex(x => x.appId === OURS);
        rows.push([date, country, lang, k, idx < 0 ? '' : idx + 1, r.length, r.slice(0, 3).map(x => x.appId).join(' ')]);
      } catch (e) {
        errors++;
        console.error(`fail ${country}/${lang} "${k}": ${e.message}`);
      }
      await sleep(1000);
    }
    console.log(`${country}/${lang} done`);
  }
  if (rows.length) appendCsv('ranks.csv', 'date,country,lang,keyword,rank,hits,top3', rows);

  const apps = [];
  for (const id of APPS) {
    try {
      const a = await retry(() => gplay.app({ appId: id, lang: 'en', country: 'us' }));
      apps.push([date, id, a.realInstalls ?? '', a.installs ?? '', a.score ?? '', a.ratings ?? '']);
    } catch (e) {
      errors++;
      console.error(`fail app ${id}: ${e.message}`);
    }
    await sleep(1000);
  }
  if (apps.length) appendCsv('apps.csv', 'date,appId,real_installs,installs,score,ratings', apps);
  console.log(`wrote ${rows.length} rank rows, ${apps.length} app rows, ${errors} errors`);
  process.exit(errors > rows.length / 2 ? 1 : 0);
})();
