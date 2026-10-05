// two bots for smoke-legend.sh. they do what the console tells them with
// "say DO <bot> <action> [args]" and print every message they get, action bar included,
// so the script can grep what a player saw. node smoke-bots/legend.js Alice Bob
const mineflayer = require('mineflayer')
const { Vec3 } = require('vec3')

const sleep = ms => new Promise(r => setTimeout(r, ms))
const bots = {}

async function act(bot, words) {
  const [action, ...args] = words
  const other = name => bot.players[name] && bot.players[name].entity
  switch (action) {
    case 'face': {
      const e = other(args[0])
      if (e) await bot.lookAt(e.position.offset(0, 1.5, 0), true)
      break
    }
    case 'look':
      await bot.lookAt(new Vec3(+args[0], +args[1], +args[2]), true)
      break
    case 'slot':
      bot.setQuickBarSlot(+args[0])
      break
    case 'use':
      bot.activateItem()
      await sleep(100)
      bot.deactivateItem()
      break
    case 'sneakuse':
      bot.setControlState('sneak', true)
      await sleep(400)
      bot.activateItem()
      await sleep(100)
      bot.deactivateItem()
      await sleep(200)
      bot.setControlState('sneak', false)
      break
    case 'swap':
    case 'sneakswap':
      if (action === 'sneakswap') {
        bot.setControlState('sneak', true)
        await sleep(400)
      }
      bot._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 })
      await sleep(200)
      bot.setControlState('sneak', false)
      break
    case 'cmduse':
      // a command and a shot in the same tick, so the shot lands inside the command's hit
      bot.chat('/' + args.join(' '))
      bot.activateItem()
      await sleep(100)
      bot.deactivateItem()
      break
    case 'charge':
      // hold a crossbow long enough to load it
      bot.activateItem()
      await sleep(1600)
      bot.deactivateItem()
      break
    case 'click': {
      const b = bot.blockAt(new Vec3(+args[0], +args[1], +args[2]))
      if (b) await bot.activateBlock(b).catch(e => console.log(bot.username, 'click failed', e.message))
      else console.log(bot.username, 'no block loaded at', args.join(' '))
      break
    }
    case 'hit': {
      const b = bot.blockAt(new Vec3(+args[0], +args[1], +args[2]))
      if (b) {
        bot._client.write('block_dig', { status: 0, location: b.position, face: 1, sequence: 0 })
        bot._client.write('block_dig', { status: 1, location: b.position, face: 1, sequence: 0 })
      }
      break
    }
    case 'attack': {
      const e = other(args[0]) || bot.nearestEntity(en => en.name === args[0])
      if (e) bot.attack(e)
      else console.log(bot.username, 'nothing to attack')
      break
    }
    case 'sees':
      console.log(bot.username, 'SEES', args[0], other(args[0]) ? 'visible' : 'hidden')
      break
    case 'count': {
      const n = Object.values(bot.entities).filter(en => en.name === args[0]).length
      console.log(bot.username, 'COUNT', args[0], n)
      break
    }
    case 'crouch':
      bot.setControlState('sneak', true)
      await sleep(+args[0] * 1000)
      bot.setControlState('sneak', false)
      break
    case 'dig': {
      const b = bot.blockAt(new Vec3(+args[0], +args[1], +args[2]))
      // mineflayer's own dig can't read 1.21.11 enchantments, so start and finish by hand
      if (b) {
        bot._client.write('block_dig', { status: 0, location: b.position, face: 2, sequence: 0 })
        await sleep(500)
        bot._client.write('block_dig', { status: 2, location: b.position, face: 2, sequence: 1 })
      }
      break
    }
    case 'drop':
      // the drop key, not an inventory click: those are refused for a transformation
      bot._client.write('block_dig', { status: 3, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 })
      break
  }
}

for (const name of process.argv.slice(2)) {
  const bot = mineflayer.createBot({ host: 'localhost', port: 25599, username: name, version: '1.21.11' })
  bots[name] = bot
  bot.on('spawn', () => console.log(name, 'spawned'))
  bot.on('kicked', r => console.log(name, 'kicked', r))
  bot.on('error', e => console.log(name, 'error', e.message))
  bot.on('messagestr', (msg, pos) => {
    console.log(name, pos === 'game_info' ? 'BAR' : 'MSG', msg)
    const m = msg.match(/^\[Server\] DO (\S+) (.+)$/)
    if (m && m[1] === name) act(bot, m[2].trim().split(/\s+/)).catch(e => console.log(name, 'act failed', e.message))
  })
  bot.on('title', (text, type) => console.log(name, 'TITLE', type, text))
}
