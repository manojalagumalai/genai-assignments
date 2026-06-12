export interface GenerateRequest {
  storyTitle: string
  acceptanceCriteria: string
  description?: string
  additionalInfo?: string
}

export interface TestCase {
  id: string
  title: string
  steps: string[]
  testData?: string
  expectedResult: string
  category: string
}

export interface GenerateResponse {
  cases: TestCase[]
  model?: string
  promptTokens: number
  completionTokens: number
}

// Minimal types for Jira issues used by the UI only
export interface JiraIssue {
  id: string
  key: string
  fields: {
    summary: string
    description?: any
  }
}

export interface JiraSearchResponse {
  issues: JiraIssue[]
  total?: number
}