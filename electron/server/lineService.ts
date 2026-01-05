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
