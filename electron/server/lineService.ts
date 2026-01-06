import axios from 'axios'
import { getSettings } from '../store/db'

const LINE_API_BASE = 'https://api.line.me/v2/bot/message'

export async function pushMessage(to: string, messages: any[]) {
  const settings = getSettings()
  const token = settings.channelAccessToken

  if (!token) {
    throw new Error('Channel Access Token is not set')
  }

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
      }
    )
    return response.data
  } catch (error: any) {
    console.error('LINE API Error:', error.response?.data || error.message)
    throw new Error(JSON.stringify(error.response?.data || error.message))
  }
}

export async function getProfile(userId: string) {
  const settings = getSettings()
  const token = settings.channelAccessToken

  if (!token) return null

  try {
    const response = await axios.get(
      `https://api.line.me/v2/bot/profile/${userId}`,
      {
        headers: { Authorization: `Bearer ${token}` }
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

  try {
    const response = await axios.get(
      `https://api.line.me/v2/bot/group/${groupId}/summary`,
      {
        headers: { Authorization: `Bearer ${token}` }
      }
    )
    return response.data
  } catch (error: any) {
    console.error('Get Group Summary Error:', error.message)
    return null
  }
}
