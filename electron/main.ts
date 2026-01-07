import { app, BrowserWindow, ipcMain } from 'electron'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import axios from 'axios'
import { initDB, getAllData, saveContacts, saveTemplates, saveSettings, saveLogs } from './store/db'
import { startServer, stopServer } from './server/index'
import { getQuota, getQuotaConsumption } from './server/lineService'


const __dirname = path.dirname(fileURLToPath(import.meta.url))

process.env.APP_ROOT = path.join(__dirname, '..')

export const VITE_DEV_SERVER_URL = process.env['VITE_DEV_SERVER_URL']
export const MAIN_DIST = path.join(process.env.APP_ROOT, 'dist-electron')
export const RENDERER_DIST = path.join(process.env.APP_ROOT, 'dist')

process.env.VITE_PUBLIC = VITE_DEV_SERVER_URL ? path.join(process.env.APP_ROOT, 'public') : RENDERER_DIST

let win: BrowserWindow | null

function createWindow() {
  win = new BrowserWindow({
    icon: path.join(process.env.VITE_PUBLIC, 'LINE_New_App_Icon.png'),
    frame: false, // Frameless window
    webPreferences: {
      preload: path.join(__dirname, 'preload.mjs'),
    },
  })

  win.webContents.on('did-finish-load', () => {
    win?.webContents.send('main-process-message', (new Date).toLocaleString())
  })

  if (VITE_DEV_SERVER_URL) {
    win.loadURL(VITE_DEV_SERVER_URL)
  } else {
    win.loadFile(path.join(RENDERER_DIST, 'index.html'))
  }
}

app.on('window-all-closed', () => {
  stopServer() // Stop Express server
  if (process.platform !== 'darwin') {
    app.quit()
    win = null
  }
})

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow()
  }
})

app.whenReady().then(async () => {
  // Initialize DB
  await initDB()
  
  // Start Express Server
  startServer()

  // IPC Handlers
  ipcMain.handle('db:get-all', () => {
    return getAllData()
  })

  ipcMain.handle('db:save', async (_, data) => {
    // Distribute data to respective savers
    if (data.contacts) await saveContacts(data.contacts)
    if (data.templates) await saveTemplates(data.templates)
    if (data.settings) await saveSettings(data.settings)
    // Logs are usually read-only from frontend, but if needed:
    if (data.logs) await saveLogs(data.logs)
    return true
  })

  ipcMain.handle('server:restart', async () => {
    stopServer()
    await startServer()
    return true
  })

  ipcMain.handle('gas:fetch', async (_, url) => {
    try {
      const response = await axios.get(url)
      return response.data
    } catch (error: any) {
      throw new Error(error.message)
    }
  })

  ipcMain.handle('line:get-quota', async () => {
    try {
      const [quota, consumption] = await Promise.all([
        getQuota(),
        getQuotaConsumption()
      ])
      return {
        type: quota?.type || 'none',
        value: quota?.value || 0,
        totalUsage: consumption?.totalUsage || 0
      }
    } catch (error) {
      console.error('IPC line:get-quota error:', error)
      return { type: 'none', value: 0, totalUsage: 0 }
    }
  })

  // Window Controls
  ipcMain.on('window:minimize', () => {
    win?.minimize()
  })

  ipcMain.on('window:maximize', () => {
    if (win?.isMaximized()) {
      win.unmaximize()
    } else {
      win?.maximize()
    }
  })

  ipcMain.on('window:close', () => {
    win?.close()
  })

  createWindow()
})
