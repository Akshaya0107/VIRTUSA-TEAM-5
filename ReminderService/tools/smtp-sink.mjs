// Local development SMTP capture only. No authentication and no outbound delivery.
import net from 'node:net'
import fs from 'node:fs/promises'
import path from 'node:path'
const directory = path.resolve(process.env.SMTP_CAPTURE_DIR || '.local-data/mail')
await fs.mkdir(directory, { recursive: true })
let sequence = 0
const server = net.createServer(socket => {
  socket.setEncoding('utf8'); socket.write('220 localhost Smart Reminder capture\r\n')
  let buffer = '', inData = false, message = []
  socket.on('error', () => {})
  socket.on('data', chunk => {
    buffer += chunk
    while (buffer.includes('\r\n')) {
      const end = buffer.indexOf('\r\n'), line = buffer.slice(0, end); buffer = buffer.slice(end + 2)
      if (inData) {
        if (line === '.') {
          inData = false; const filename = `message-${Date.now()}-${++sequence}.eml`
          fs.writeFile(path.join(directory, filename), message.join('\r\n')).then(() => socket.write('250 2.0.0 captured\r\n')).catch(() => socket.write('451 capture failed\r\n')); message = []
        } else message.push(line.startsWith('..') ? line.slice(1) : line)
        continue
      }
      if (/^EHLO/i.test(line)) socket.write('250-localhost\r\n250-8BITMIME\r\n250 SIZE 1048576\r\n')
      else if (/^HELO|^MAIL FROM:|^RCPT TO:|^RSET|^NOOP/i.test(line)) socket.write('250 OK\r\n')
      else if (/^DATA$/i.test(line)) { inData = true; message = []; socket.write('354 End with a dot\r\n') }
      else if (/^QUIT/i.test(line)) socket.end('221 Bye\r\n')
      else socket.write('502 Command not supported\r\n')
    }
  })
})
server.listen(1025, '127.0.0.1', () => process.stdout.write('Development SMTP capture listening at 127.0.0.1:1025; messages remain local.\n'))
