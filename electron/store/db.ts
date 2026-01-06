import { app } from 'electron'
import { join } from 'path'
import { Low } from 'lowdb'
import { JSONFile } from 'lowdb/node'

// Define schemas
export interface Contact {
  id: string
  name: string
  lineId: string
  type: 'user' | 'group'
  code?: string
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
}

export interface Settings {
  port: number
  channelAccessToken: string
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
  channelAccessToken: '',
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
  settingsDB = new Low(new JSONFile(join(userDataPath, 'settings.json')), defaultSettings)
  await settingsDB.read()
  settingsDB.data ||= defaultSettings
  await settingsDB.write()

  // Migration: Check for legacy db.json
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
        settingsDB.data = { ...defaultSettings, ...legacyDB.data.settings }
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
