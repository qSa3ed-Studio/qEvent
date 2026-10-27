/**
 * qEvent verification bot.
 *
 * Connects a real Minecraft protocol client to the local test server, op-runs the /event
 * commands as a player would, and records the packets that prove the announcement actually
 * reached a client: titles, the per-viewer display entity and its player-head metadata, and
 * the resource pack offer.
 *
 * Usage: node bot.js <port> <announcement-command>
 */
const mineflayer = require('mineflayer');

const port = Number(process.argv[2] || 25566);
const commands = process.argv.slice(3);
if (commands.length === 0) {
  commands.push('/event announce Notch HALLOWEEN EVENT STARTED! Join the lobby now!');
}

const seen = {
  title: [],
  subtitle: [],
  displaySpawn: [],
  displayMetadata: [],
  resourcePack: [],
  chat: [],
  errors: [],
};

function short(value) {
  if (value == null) return String(value);
  if (typeof value === 'string') return value;
  if (Buffer.isBuffer(value)) return `<buffer ${value.length}b>`;
  try {
    return JSON.stringify(value, (k, v) => (v && v.type === 'Buffer' ? `<buffer>` : v)).slice(0, 600);
  } catch (e) {
    return String(value);
  }
}

const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port,
  username: 'ViewerOne',
  version: '1.20.6',
  auth: 'offline',
  hideErrors: false,
  checkTimeoutInterval: 600000,
});

bot.on('error', (err) => seen.errors.push(`bot error: ${err.message}`));
bot.on('kicked', (reason) => seen.errors.push(`kicked: ${JSON.stringify(reason)}`));

let itemDisplayTypeId;
function displayTypeId() {
  if (itemDisplayTypeId !== undefined && itemDisplayTypeId > 0) return itemDisplayTypeId;
  itemDisplayTypeId = -1;
  try {
    const registry = bot.registry;
    const byName = (registry && (registry.entityTypesByName || registry.entitiesByName)) || {};
    const entry = byName['item_display'] || byName['minecraft:item_display'];
    if (entry && typeof entry.id === 'number') itemDisplayTypeId = entry.id;
  } catch (e) {
    itemDisplayTypeId = -1;
  }
  return itemDisplayTypeId;
}

const allPackets = {};

bot._client.on('packet', (data, meta) => {
  const name = meta.name;
  allPackets[name] = (allPackets[name] || 0) + 1;
  if (name === 'title') seen.title.push(short(data));
  else if (name === 'set_title_text' || name === 'set_title_time' || name === 'set_title_subtitle') {
    seen.title.push(`${name}: ${short(data)}`);
  }
  else if (name === 'subtitle') seen.subtitle.push(short(data));
  else if (name === 'resource_pack_send' || name === 'resource_pack' || name === 'resource_pack_pop') {
    seen.resourcePack.push(`${name}: ${short(data)}`);
  } else if (name === 'system_chat') seen.chat.push(short(data));
  else if (name === 'spawn_entity') {
    if (data.type === displayTypeId()) {
      seen.displaySpawn.push(short(data));
    }
  } else if (name === 'entity_metadata') {
    // Recorded only while a display entity exists, so metadata is not captured for every mob.
    if (seen.displaySpawn.length > 0 && data.entityId !== undefined) {
      seen.displayMetadata.push(short(data));
    }
  } else if (name === 'entity_destroy' && seen.displaySpawn.length > 0) {
    seen.errors.push(`display destroyed: ${short(data)}`);
  }
});

bot.once('spawn', () => {
  console.log('BOT SPAWNED as', bot.username, 'entityId', bot.entity && bot.entity.id);
  console.log('item_display type id =', displayTypeId());
  try {
    const keys = Object.keys((bot.registry && (bot.registry.entityTypesByName || bot.registry.entitiesByName)) || {})
      .filter((k) => k.includes('display'));
    console.log('registry display keys:', JSON.stringify(keys), 'item_display id =', displayTypeId());
  } catch (e) {
    console.log('registry introspection failed:', e.message);
  }

  setTimeout(() => {
    commands.forEach((command, index) => {
      setTimeout(() => {
        console.log('SENDING:', command);
        bot.chat(command);
      }, index * 4000);
    });
  }, 5000);

  setTimeout(() => {
    console.log('=== SUMMARY ===');
    console.log('title packets     :', seen.title.length);
    seen.title.forEach((t, i) => console.log('   title[' + i + ']:', t));
    console.log('subtitle packets  :', seen.subtitle.length);
    seen.subtitle.forEach((t, i) => console.log('   subtitle[' + i + ']:', t));
    console.log('display spawns    :', seen.displaySpawn.length);
    seen.displaySpawn.forEach((t, i) => console.log('   spawn[' + i + ']:', t));
    console.log('display metadata  :', seen.displayMetadata.length);
    seen.displayMetadata.slice(0, 4).forEach((t, i) => console.log('   meta[' + i + ']:', t));
    console.log('resource pack     :', seen.resourcePack.length);
    seen.resourcePack.forEach((t) => console.log('   ', t));
    console.log('chat/system chat  :', seen.chat.length);
    seen.chat.forEach((t) => console.log('   ', t));
    console.log('errors/kicks      :', seen.errors.length);
    seen.errors.forEach((t) => console.log('   ', t));
    console.log('all packet names  :');
    console.log('   ', JSON.stringify(allPackets, null, 0));
    bot.quit();
    setTimeout(() => process.exit(0), 1500);
  }, 26000);
});