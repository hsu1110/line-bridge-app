import express from 'express'
import cors from 'cors'
import bodyParser from 'body-parser'
import { getSettings, getContacts, getTemplates, addLog } from '../store/db'
import { pushMessage } from './lineService'
import { v4 as uuidv4 } from 'uuid'

let server: any

export function startServer() {
  const app = express()
  const settings = getSettings()
  const port = settings.port || 3000

  app.use(cors())
  app.use(bodyParser.json())

  // Health check
  app.get('/api/status', (_req, res) => {
    res.json({ status: 'running', port })
  })

  // Security Middleware
  app.use((req, res, next) => {
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
  })

  // Send Message API
  app.post('/api/send', async (req, res) => {
    const { target_code, template_code, params, to, message } = req.body
    const logId = uuidv4()
    const timestamp = new Date().toISOString()
    let finalContent = ''
    let targetLineId = ''
    let targetCodeForLog = target_code || 'DIRECT'

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

      // 3. Send to LINE
      const lineMessage = { type: 'text', text: finalContent }
      await pushMessage(targetLineId, [lineMessage])

      // 4. Log Success
      await addLog({
        id: logId,
        timestamp,
        targetCode: targetCodeForLog,
        templateCode: template_code,
        content: finalContent,
        status: 'success'
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
        error: error.message
      })

      res.status(500).json({ success: false, error: error.message })
    }
  })

  server = app.listen(port, () => {
    console.log(`Internal API Server running on port ${port}`)
  })
}

export function stopServer() {
  if (server) {
    server.close()
    server = null
  }
}
