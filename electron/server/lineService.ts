import axios from 'axios'
import { getSettings } from '../store/db'

const LINE_API_BASE = 'https://api.line.me/v2/bot/message'

export async function pushMessage(to: string, messages: any[]) {
  const settings = getSettings()
  const token = settings.channelAccessToken

  if (!token) {
    throw new Error('Channel Access Token is not set')
  }

  const proxyConfig = settings.proxy ? {
    host: settings.proxy.split(':')[1].replace('//', ''),
    port: parseInt(settings.proxy.split(':')[2])
  } : undefined

  try {
    const response = await axios.post(
      `${LINE_API_BASE}/push`,
      {
        to,
        messages,
      },
      {
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        proxy: proxyConfig ? {
            protocol: 'http',
            host: proxyConfig.host,
            port: proxyConfig.port
        } : false
      }
    )
    
    if (!response) {
      throw new Error('No response received from LINE API')
    }
    
    return response.data
  } catch (error: any) {
    console.error('LINE API Error:', error)
    const errorData = error.response?.data || error.message || 'Unknown Error'
    throw new Error(typeof errorData === 'object' ? JSON.stringify(errorData) : errorData)
  }
}

export async function getProfile(userId: string) {
  const settings = getSettings()
  const token = settings.channelAccessToken

  if (!token) return null

  const proxyConfig = settings.proxy ? {
    host: settings.proxy.split(':')[1].replace('//', ''),
    port: parseInt(settings.proxy.split(':')[2])
  } : undefined

  try {
    const response = await axios.get(
      `https://api.line.me/v2/bot/profile/${userId}`,
      {
        headers: { Authorization: `Bearer ${token}` },
        proxy: proxyConfig ? {
            protocol: 'http',
            host: proxyConfig.host,
            port: proxyConfig.port
        } : false
      }
    )
    return response.data
  } catch (error: any) {
    console.error('Get Profile Error:', error.message)
    return null
  }
}

export async function getGroupSummary(groupId: string) {
  const settings = getSettings()
  const token = settings.channelAccessToken

  if (!token) return null

  const proxyConfig = settings.proxy ? {
    host: settings.proxy.split(':')[1].replace('//', ''),
    port: parseInt(settings.proxy.split(':')[2])
  } : undefined

  try {
    const response = await axios.get(
      `https://api.line.me/v2/bot/group/${groupId}/summary`,
      {
        headers: { Authorization: `Bearer ${token}` },
        proxy: proxyConfig ? {
            protocol: 'http',
            host: proxyConfig.host,
            port: proxyConfig.port
        } : false
      }
    )
    return response.data
  } catch (error: any) {
    console.error('Get Group Summary Error:', error.message)
    return null
  }
}
