import express from 'express'
import fetch from 'node-fetch'
import { z } from 'zod'

export const jiraRouter = express.Router()

const JiraSearchSchema = z.object({
  baseUrl: z.string().url(),
  email: z.string().min(1),
  apiKey: z.string().min(1),
  jql: z.string().optional()
})

jiraRouter.post('/search', async (req: express.Request, res: express.Response) => {
  try {
    const parse = JiraSearchSchema.safeParse(req.body)
    if (!parse.success) {
      res.status(400).json({ error: `Validation error: ${parse.error.message}` })
      return
    }

    const { baseUrl, email, apiKey, jql } = parse.data
    const normalizedBase = baseUrl.replace(/\/+$/, '')
    const query = jql && jql.trim() ? jql.trim() : 'issuetype = Story OR issuetype = "User Story"'
    // Use the newer search/jql endpoint (POST) per Atlassian migration guidance
    const url = `${normalizedBase}/rest/api/3/search/jql`

    const auth = Buffer.from(`${email}:${apiKey}`).toString('base64')

    // Try multiple payload shapes to support different Jira Cloud API expectations
    const payloadVariants = [
      { jql: query, maxResults: 50, startAt: 0, fields: ['summary', 'description', 'issuetype'] },
      { query: query, maxResults: 50, startAt: 0, fields: ['summary', 'description', 'issuetype'] },
      { jql: query },
      { query: query }
    ]

    let finalData: any = null
    let lastStatus = 0
    let lastStatusText = ''
    let lastBody = ''

    for (const payload of payloadVariants) {
      try {
        console.log('Calling Jira search/jql with payload:', JSON.stringify(payload))
        const resp = await fetch(url, {
          method: 'POST',
          headers: {
            Authorization: `Basic ${auth}`,
            Accept: 'application/json',
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(payload)
        })

        const text = await resp.text().catch(() => '')
        console.log('Attempt response status:', resp.status, resp.statusText)
        console.log('Attempt response body:', text)

        lastStatus = resp.status
        lastStatusText = resp.statusText
        lastBody = text

        if (resp.ok) {
          try {
            finalData = JSON.parse(text)
          } catch {
            finalData = null
          }
          break
        }

        // if 400 try next variant
        if (resp.status === 400) continue
        // if 410 or other non-recoverable status, stop
        if (resp.status >= 400) break
      } catch (innerErr) {
        console.error('Error when calling Jira with payload variant:', innerErr)
      }
    }

    // Fallback: try GET on /search (may be deprecated but worth attempting)
    if (!finalData && (lastStatus === 400 || lastStatus === 410)) {
      try {
        const getUrl = `${normalizedBase}/rest/api/3/search?jql=${encodeURIComponent(query)}&maxResults=50`
        console.log('Fallback GET to', getUrl)
        const resp = await fetch(getUrl, { headers: { Authorization: `Basic ${auth}`, Accept: 'application/json' } })
        const text = await resp.text().catch(() => '')
        console.log('Fallback GET status:', resp.status, resp.statusText)
        console.log('Fallback GET body:', text)
        if (resp.ok) {
          try { finalData = JSON.parse(text) } catch {}
        } else {
          lastStatus = resp.status
          lastStatusText = resp.statusText
          lastBody = text
        }
      } catch (getErr) {
        console.error('Fallback GET error:', getErr)
      }
    }

    if (!finalData) {
      // Try to parse lastBody JSON for structured error
      let parsedErr: any = null
      try { parsedErr = JSON.parse(lastBody) } catch {}
      const message = parsedErr?.errorMessages ? parsedErr.errorMessages.join('; ') : lastBody || 'Unknown error'
      res.status(lastStatus || 502).json({ error: `Jira API error: ${lastStatus} ${lastStatusText} ${message}` })
      return
    }

    const data = finalData
    if (!data) {
      res.status(502).json({ error: 'Invalid JSON from Jira' })
      return
    }

    // Return the Jira search response as-is (frontend expects `issues`)
    res.json(data)
  } catch (err) {
    console.error('Error in /api/jira/search:', err)
    res.status(500).json({ error: 'Internal server error' })
  }
})
