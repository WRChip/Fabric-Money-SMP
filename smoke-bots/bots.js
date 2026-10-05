// logs each name given into the dev server and leaves them standing there until they're
// kicked or the server stops. node smoke-bots/bots.js RedA RedB ...
const mineflayer = require('mineflayer')

for (const name of process.argv.slice(2)) {
  const bot = mineflayer.createBot({ host: 'localhost', port: 25599, username: name, version: '1.21.11' })
  bot.on('spawn', () => console.log(name, 'spawned'))
  bot.on('kicked', r => console.log(name, 'kicked', r))
  bot.on('error', e => console.log(name, 'error', e.message))
}
