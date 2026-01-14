import { app } from 'electron'
import { join } from 'path'
import { Low } from 'lowdb'
import { JSONFile } from 'lowdb/node'
import { v4 as uuidv4 } from 'uuid'

// Define schemas
export interface Contact {
  id: string
  name: string
  lineId: string
  type: 'user' | 'group'
  code?: string
  linkedBotIds?: string[] // [New] List of bot IDs this contact is linked to
}

export interface Template {
  id: string
  code: string
  content: string
  name: string
}

export interface Log {
  id: string
  timestamp: string
  sourceIp?: string
  targetCode: string
  templateCode?: string
  content: string
  status: 'success' | 'failed'
  error?: string
  botId?: string // [New] Identify which bot sent the message
}

export interface BotAccount {
  id: string
  name: string
  lineBotId: string // @12345xyz
  channelAccessToken: string
  secret: string
  quotaLimit: number
  currentUsage: number
  lastSyncTime?: string
}

export interface Settings {
  port: number
  // channelAccessToken: string // [Deprecated] Moved to accounts
  accounts: BotAccount[] // [New] Multi-account support
  lineChannelSecret?: string // For Local Webhook Signature Validation
  bindingMethod: 'gas' | 'local'
  proxy?: string
  gasUrl?: string
  apiKey?: string // Internal API Security
}

// DB Instances
let contactsDB: Low<Contact[]>
let templatesDB: Low<Template[]>
let logsDB: Low<Log[]>
let settingsDB: Low<Settings>

const defaultSettings: Settings = {
  port: 3000,
  accounts: [],
  lineChannelSecret: '',
  bindingMethod: 'gas',
  apiKey: ''
}

export async function initDB() {
  const userDataPath = app.getPath('userData')

  // Contacts
  contactsDB = new Low(new JSONFile(join(userDataPath, 'contacts.json')), [])
  await contactsDB.read()
  contactsDB.data ||= []
  await contactsDB.write()

  // Templates
  templatesDB = new Low(new JSONFile(join(userDataPath, 'templates.json')), [])
  await templatesDB.read()
  templatesDB.data ||= []
  await templatesDB.write()

  // Logs
  logsDB = new Low(new JSONFile(join(userDataPath, 'logs.json')), [])
  await logsDB.read()
  logsDB.data ||= []
  await logsDB.write()

  // Settings
  // Note: We need to handle potential schema mismatch during read
  settingsDB = new Low(new JSONFile(join(userDataPath, 'settings.json')), defaultSettings)
  await settingsDB.read()
  settingsDB.data ||= defaultSettings

  // --- Migration Logic ---
  let settingsChanged = false
  const settingsData = settingsDB.data as any // Cast to any to access legacy fields

  // 1. Migrate legacy channelAccessToken to accounts
  if (settingsData.channelAccessToken && (!settingsData.accounts || settingsData.accounts.length === 0)) {
    console.log('Migrating legacy channelAccessToken to BotAccount...')
    const defaultBot: BotAccount = {
      id: uuidv4(),
      name: 'Default Bot',
      lineBotId: '', // User needs to fill this
      channelAccessToken: settingsData.channelAccessToken,
      secret: settingsData.lineChannelSecret || '',
      quotaLimit: 200, // Default free tier assumption or safe low value
      currentUsage: 0
    }
    settingsDB.data.accounts = [defaultBot]
    delete settingsData.channelAccessToken
    settingsChanged = true

    // 2. Link existing contacts to this default bot
    if (contactsDB.data.length > 0) {
      console.log('Linking existing contacts to default bot...')
      let contactsChanged = false
      contactsDB.data.forEach(contact => {
        if (!contact.linkedBotIds) {
          contact.linkedBotIds = [defaultBot.id]
          contactsChanged = true
        }
      })
      if (contactsChanged) await contactsDB.write()
    }
  }

  // Ensure accounts array exists
  if (!settingsDB.data.accounts) {
    settingsDB.data.accounts = []
    settingsChanged = true
  }

  if (settingsChanged) {
    await settingsDB.write()
    console.log('Settings migration complete.')
  }
  // -----------------------

  // Legacy DB Migration (Keep existing logic just in case)
  const fs = await import('fs/promises')
  const legacyPath = join(userDataPath, 'db.json')
  try {
    await fs.access(legacyPath)
    // If db.json exists, migrate data
    const legacyAdapter = new JSONFile<any>(legacyPath)
    const legacyDB = new Low(legacyAdapter, {})
    await legacyDB.read()
    
    if (legacyDB.data) {
      console.log('Migrating legacy database...')
      if (legacyDB.data.contacts?.length) {
        contactsDB.data = legacyDB.data.contacts
        await contactsDB.write()
      }
      if (legacyDB.data.templates?.length) {
        templatesDB.data = legacyDB.data.templates
        await templatesDB.write()
      }
      if (legacyDB.data.logs?.length) {
        logsDB.data = legacyDB.data.logs
        await logsDB.write()
      }
      if (legacyDB.data.settings) {
        // Merge legacy settings, but be careful with the new structure
        // If legacy has channelAccessToken, it might override our new structure if we are not careful
        // But since we already handled migration above, we might just want to take other fields
        // For simplicity, let's assume the above migration handles the critical part.
        // If this runs, it means we have a VERY old db.json. 
        // We'll just copy what we can.
        const { channelAccessToken, ...otherSettings } = legacyDB.data.settings
        settingsDB.data = { ...settingsDB.data, ...otherSettings }
        
        // If we pulled a token from legacy db.json and we don't have accounts yet
        if (channelAccessToken && settingsDB.data.accounts.length === 0) {
             const defaultBot: BotAccount = {
                id: uuidv4(),
                name: 'Legacy Bot',
                lineBotId: '',
                channelAccessToken: channelAccessToken,
                secret: '',
                quotaLimit: 200,
                currentUsage: 0
            }
            settingsDB.data.accounts.push(defaultBot)
        }
        await settingsDB.write()
      }
      
      // Rename legacy file to backup
      await fs.rename(legacyPath, join(userDataPath, 'db.json.bak'))
      console.log('Migration complete. Legacy db.json renamed to db.json.bak')
    }
  } catch (e) {
    // Legacy file doesn't exist or error accessing it, skip migration
  }
}

// Accessors
export const getContacts = () => contactsDB.data
export const getTemplates = () => templatesDB.data
export const getLogs = () => logsDB.data
export const getSettings = () => settingsDB.data

// Savers
export const saveContacts = async (data: Contact[]) => {
  contactsDB.data = data
  await contactsDB.write()
}
export const saveTemplates = async (data: Template[]) => {
  templatesDB.data = data
  await templatesDB.write()
}
export const saveLogs = async (data: Log[]) => {
  logsDB.data = data
  await logsDB.write()
}
export const saveSettings = async (data: Settings) => {
  settingsDB.data = data
  await settingsDB.write()
}

// Helper to append log
export const addLog = async (log: Log) => {
  logsDB.data.unshift(log)
  // Limit logs to 1000
  if (logsDB.data.length > 1000) logsDB.data.length = 1000
  await logsDB.write()
}

// Legacy support for IPC (Get All)
export const getAllData = () => ({
  contacts: contactsDB.data,
  templates: templatesDB.data,
  logs: logsDB.data,
  settings: settingsDB.data
})
