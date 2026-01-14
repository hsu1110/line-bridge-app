import express from 'express'
import cors from 'cors'
import bodyParser from 'body-parser'
import crypto from 'crypto'
import { getSettings, getContacts, getTemplates, addLog, saveContacts } from '../store/db'
import { pushMessage, getProfile, getGroupSummary } from './lineService'
import { v4 as uuidv4 } from 'uuid'

let server: any

export function startServer(): Promise<void> {
  return new Promise((resolve) => {
    const app = express()
    const settings = getSettings()
    const port = settings.port || 3000

    app.use(cors())
    
    // Capture raw body for signature validation
    app.use(bodyParser.json({
      verify: (req: any, _res, buf) => {
        req.rawBody = buf
      }
    }))
    app.use(bodyParser.urlencoded({ extended: true }))

    // Health check
    app.get('/api/status', (_req, res) => {
      res.json({ status: 'running', port })
    })

    // Security Middleware (For /api/send)
    const apiKeyAuth = (req: any, res: any, next: any) => {
      const settings = getSettings()
      if (settings.apiKey && settings.apiKey.trim() !== '') {
        const providedKey = req.headers['x-api-key'] || req.query.api_key
        if (providedKey !== settings.apiKey) {
          // Log Auth Failure
          addLog({
            id: uuidv4(),
            timestamp: new Date().toISOString(),
            targetCode: 'SYSTEM',
            content: 'API Authentication Failed',
            status: 'failed',
            error: 'Invalid API Key',
            sourceIp: req.ip
          })
          return res.status(401).json({ success: false, error: 'Invalid API Key' })
        }
      }
      next()
    }

    // LINE Webhook Endpoint
    app.post('/api/webhook', async (req: any, res) => {
      const settings = getSettings()
      
      // 1. Signature Validation
      if (settings.bindingMethod === 'local') {
        const channelSecret = settings.lineChannelSecret
        const signature = req.headers['x-line-signature'] as string
        
        if (!channelSecret || !signature) {
          console.error('Webhook Error: Missing Secret or Signature')
          return res.status(403).send('Forbidden')
        }

        const body = req.rawBody
        const hash = crypto
          .createHmac('sha256', channelSecret)
          .update(body)
          .digest('base64')

        if (hash !== signature) {
          console.error('Webhook Error: Invalid Signature')
          return res.status(403).send('Forbidden')
        }
      } else {
        // If not in local mode, ignore webhooks (or maybe allow them if user wants to test?)
        // For now, let's allow it but log a warning if needed, or just proceed.
        // But strictly speaking, if bindingMethod is GAS, we shouldn't be receiving webhooks here unless configured.
      }

      const events = req.body.events || []
      const contacts = getContacts()
      let isContactsUpdated = false

      for (const event of events) {
        try {
          // Handle Follow (Add Friend)
          if (event.type === 'follow') {
            const userId = event.source.userId
            // Use destination (Bot ID) to get profile using the correct bot token
            const destination = req.body.destination
            const profile = await getProfile(userId, destination)
            const displayName = profile?.displayName || 'Unknown User'
            
            const existing = contacts.find(c => c.lineId === userId)
            if (existing) {
              existing.name = displayName
              // existing.code = displayName // Keep existing code
            } else {
              contacts.push({
                id: uuidv4(),
                name: displayName,
                lineId: userId,
                type: 'user',
                code: displayName // Default code
              })
            }
            isContactsUpdated = true
            
            // Optional: Reply welcome message via push (since we don't have reply API implemented yet)
            // Or just ignore.
          }

          // Handle Join (Join Group)
          if (event.type === 'join' || event.type === 'memberJoined') {
             // memberJoined is for when user joins, but 'join' is when BOT joins.
             // We care about BOT joining a group.
             if (event.type === 'join') {
               const groupId = event.source.groupId
               const destination = req.body.destination
               const summary = await getGroupSummary(groupId, destination)
               const groupName = summary?.groupName || 'Unknown Group'

               const existing = contacts.find(c => c.lineId === groupId)
               if (existing) {
                 existing.name = groupName
               } else {
                 contacts.push({
                   id: uuidv4(),
                   name: groupName,
                   lineId: groupId,
                   type: 'group',
                   code: groupName
                 })
               }
               isContactsUpdated = true
             }
          }

          // Handle Message (System Binding Command)
          if (event.type === 'message' && event.message.type === 'text') {
            const text = event.message.text.trim()
            const regex = /^(系統綁定|System Bind)[:：]\s*(.+)$/i
            const match = text.match(regex)

            if (match) {
              const customName = match[2].trim()
              const source = event.source
              const id = source.groupId || source.roomId || source.userId
              const type = source.type === 'group' || source.type === 'room' ? 'group' : 'user'

              if (id) {
                const existing = contacts.find(c => c.lineId === id)
                if (existing) {
                  existing.name = customName
                  // existing.code = customName // Keep existing code? Or update?
                  // GAS logic: updates Name. 
                  // Let's stick to updating Name only to be safe, or maybe both?
                  // User request said: "系統綁定：XXX他會把舊的資料覆蓋嗎...然後修改"
                  // Usually binding command implies setting the identity.
                  // Let's update Name. Code is for API, maybe keep it stable.
                } else {
                  contacts.push({
                    id: uuidv4(),
                    name: customName,
                    lineId: id,
                    type: type,
                    code: customName
                  })
                }
                isContactsUpdated = true
                
                // Reply success? We need replyToken support in lineService if we want to reply.
                // For now, we can just save.
              }
            }
          }
        } catch (e) {
          console.error('Error handling event:', e)
        }
      }

      if (isContactsUpdated) {
        await saveContacts(contacts)
      }

      res.status(200).send('OK')
    })

    // Send Message API
    app.post('/api/send', apiKeyAuth, async (req, res) => {
      const { target_code, template_code, params, to, message, bot_code } = req.body
      const logId = uuidv4()
      const timestamp = new Date().toISOString()
      let finalContent = ''
      let targetLineId = ''
      let targetCodeForLog = target_code || 'DIRECT'
      let botId: string | undefined = undefined

      try {
        // 1. Resolve Target
        if (target_code) {
          const contacts = getContacts()
          const matchedContacts = contacts.filter(c => c.code === target_code)
          
          if (matchedContacts.length === 0) {
            throw new Error(`Target code not found: ${target_code}`)
          }
          
          if (matchedContacts.length > 1) {
            throw new Error(`Ambiguous target code: ${target_code} (Found ${matchedContacts.length} matches). Please resolve duplicates in Contacts.`)
          }

          targetLineId = matchedContacts[0].lineId
        } else if (to) {
          targetLineId = to
        } else {
          throw new Error('Missing target_code or to')
        }

        // 2. Resolve Content
        if (template_code) {
          const templates = getTemplates()
          const template = templates.find(t => t.code === template_code)
          if (!template) {
            throw new Error(`Template code not found: ${template_code}`)
          }
          
          // Variable Replacement
          let content = template.content
          if (params) {
            for (const [key, value] of Object.entries(params)) {
              content = content.replace(new RegExp(`{{${key}}}`, 'g'), String(value))
            }
          }
          finalContent = content
        } else if (message) {
          finalContent = message
        } else {
          throw new Error('Missing template_code or message')
        }

        // 3. Resolve Bot
        if (bot_code) {
           // bot_code can be the Bot ID (U...) or the Bot Name or the @BasicID
           const settings = getSettings()
           const account = settings.accounts?.find(a => a.id === bot_code || a.name === bot_code || a.lineBotId === bot_code)
           if (account) {
             botId = account.id
           } else {
             throw new Error(`Bot not found: ${bot_code}`)
           }
        } else {
           throw new Error('Missing bot_code. Please specify which bot to use.')
        }

        // 3. Send to LINE
        const lineMessage = { type: 'text', text: finalContent }
        await pushMessage(targetLineId, [lineMessage], botId)

        // 4. Log Success
        await addLog({
          id: logId,
          timestamp,
          targetCode: targetCodeForLog,
          templateCode: template_code,
          content: finalContent,
          status: 'success',
          botId: botId
        })

        res.json({ success: true, logId })

      } catch (error: any) {
        console.error('Send Error:', error.message)
        
        // Log Failure
        await addLog({
          id: logId,
          timestamp,
          targetCode: targetCodeForLog,
          templateCode: template_code,
          content: finalContent || 'Error before content generation',
          status: 'failed',
          error: error.message,
          botId: botId
        })

        res.status(500).json({ success: false, error: error.message })
      }
    })

    server = app.listen(port, () => {
      console.log(`Internal API Server running on port ${port}`)
      resolve()
    })
  })
}

export function stopServer() {
  if (server) {
    server.close()
    server = null
  }
}
