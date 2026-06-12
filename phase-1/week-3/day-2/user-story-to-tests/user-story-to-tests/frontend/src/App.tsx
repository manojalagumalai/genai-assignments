import { useState } from 'react'
import { generateTests, API_BASE_URL } from './api'
import { GenerateRequest, GenerateResponse, TestCase, JiraIssue, JiraSearchResponse } from './types'

function App() {
  const [formData, setFormData] = useState<GenerateRequest>({
    storyTitle: '',
    acceptanceCriteria: '',
    description: '',
    additionalInfo: ''
  })
  const [results, setResults] = useState<GenerateResponse | null>(null)
  const [isLoading, setIsLoading] = useState<boolean>(false)
  const [error, setError] = useState<string | null>(null)
  const [expandedTestCases, setExpandedTestCases] = useState<Set<string>>(new Set())

  // Jira connection state (UI-only)
  const [jiraBaseUrl, setJiraBaseUrl] = useState<string>('')
  const [jiraEmail, setJiraEmail] = useState<string>('')
  const [jiraApiKey, setJiraApiKey] = useState<string>('')
  const [jiraJql, setJiraJql] = useState<string>('issuetype = Story OR issuetype = "User Story"')
  const [jiraConnected, setJiraConnected] = useState<boolean>(false)
  const [jiraIssues, setJiraIssues] = useState<JiraIssue[]>([])
  const [jiraLoading, setJiraLoading] = useState<boolean>(false)
  const [jiraError, setJiraError] = useState<string | null>(null)
  const [selectedIssue, setSelectedIssue] = useState<JiraIssue | null>(null)

  // Helper to extract plain text from Jira's description field (handles string or ADF-ish objects)
  const extractPlainTextFromJiraDescription = (desc: any): string => {
    if (!desc) return ''
    if (typeof desc === 'string') return desc
    // Atlassian Document Format (very basic traversal)
    if (desc.content && Array.isArray(desc.content)) {
      const parts: string[] = []
      const walk = (node: any) => {
        if (!node) return
        if (node.type === 'text' && node.text) parts.push(node.text)
        if (node.content && Array.isArray(node.content)) node.content.forEach(walk)
      }
      desc.content.forEach(walk)
      return parts.join('\n')
    }
    try {
      return String(desc)
    } catch {
      return ''
    }
  }

  // Select an issue for preview. Applying it to the form is a separate action.
  const handleSelectIssue = (issue: JiraIssue) => {
    setSelectedIssue(issue)
  }

  const applySelectedIssueToForm = (issue: JiraIssue) => {
    const summary = issue.fields?.summary || issue.key
    const desc = extractPlainTextFromJiraDescription(issue.fields?.description)
    setFormData(prev => ({
      ...prev,
      storyTitle: summary,
      description: desc,
      acceptanceCriteria: desc ? desc.slice(0, 1000) : prev.acceptanceCriteria
    }))
    setSelectedIssue(null)
  }

  const toggleTestCaseExpansion = (testCaseId: string) => {
    const newExpanded = new Set(expandedTestCases)
    if (newExpanded.has(testCaseId)) {
      newExpanded.delete(testCaseId)
    } else {
      newExpanded.add(testCaseId)
    }
    setExpandedTestCases(newExpanded)
  }

  const handleInputChange = (field: keyof GenerateRequest, value: string) => {
    setFormData(prev => ({ ...prev, [field]: value }))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
    if (!formData.storyTitle.trim() || !formData.acceptanceCriteria.trim()) {
      setError('Story Title and Acceptance Criteria are required')
      return
    }

    setIsLoading(true)
    setError(null)
    
    try {
      const response = await generateTests(formData)
      setResults(response)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to generate tests')
    } finally {
      setIsLoading(false)
    }
  }

  const sanitizeFilename = (s: string) => {
    return s.replace(/[^a-z0-9\-\_\.]/gi, '_').slice(0, 200)
  }
  const mapStepPrefix = (step: string, index: number, total: number) => {
    const raw = (step || '').trim()
    if (!raw) return ''
    const low = raw.toLowerCase()
    if (/^(given|when|then|and|but)\b/.test(low)) return '' // keep original

    if (/(login|logged in|logged-out|have to|have |ensure|precondition|setup|given)/.test(low)) return 'Given'
    if (/(click|enter|select|submit|press|choose|type|navigate|open|set|fill|go to|tap|search|upload)/.test(low)) return 'When'
    if (/(see|should|expect|verify|then|display|be|is |are |response|result|should see|validate)/.test(low)) return 'Then'

    if (index === 0) return 'Given'
    if (index === total - 1) return 'Then'
    return 'When'
  }

  const buildFeatureContent = () => {
    const title = formData.storyTitle || selectedIssue?.fields?.summary || 'User Story'
    const description = formData.description || extractPlainTextFromJiraDescription(selectedIssue?.fields?.description) || ''

    let scenarios: { title: string; steps: string[] }[] = []

    if (results && results.cases && results.cases.length > 0) {
      scenarios = results.cases.map(c => ({ title: c.title || c.id, steps: c.steps }))
    } else if (selectedIssue) {
      const descText = extractPlainTextFromJiraDescription(selectedIssue.fields?.description)
      const steps = descText ? descText.split('\n').filter(Boolean) : ['(no description)']
      scenarios = [{ title: selectedIssue.fields?.summary || selectedIssue.key, steps }]
    } else {
      const ac = formData.acceptanceCriteria || ''
      const steps = ac ? ac.split('\n').filter(Boolean) : ['Describe acceptance criteria here']
      scenarios = [{ title: title, steps }]
    }

    const lines: string[] = []
    lines.push(`Feature: ${title}`)
    if (description) {
      lines.push('')
      description.split('\n').forEach(l => lines.push(`  ${l}`))
    }
    lines.push('')

    scenarios.forEach((sc, si) => {
      lines.push(`  Scenario: ${sc.title}`)
      sc.steps.forEach((step, idx) => {
        const trimmed = (step || '').trim()
        if (!trimmed) return
        if (/^(given|when|then|and|but)\b/i.test(trimmed)) {
          lines.push(`    ${trimmed}`)
        } else {
          const prefix = mapStepPrefix(trimmed, idx, sc.steps.length)
          lines.push(`    ${prefix} ${trimmed}`)
        }
      })
      if (si < scenarios.length - 1) lines.push('')
    })

    const content = lines.join('\n')

    // build filename: prefer Jira key if available, else title; append timestamp
    const jiraKey = selectedIssue?.key
    const baseName = jiraKey ? `${jiraKey}` : sanitizeFilename(title || 'feature')
    const ts = new Date().toISOString().replace(/[:\.]/g, '-')
    const filename = `${baseName}_${ts}.feature`

    return { content, filename }
  }

  const downloadFeatureFile = () => {
    const { content, filename } = buildFeatureContent()
    const blob = new Blob([content], { type: 'text/x-gherkin' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  }

  const copyFeatureToClipboard = async () => {
    const { content } = buildFeatureContent()
    try {
      await navigator.clipboard.writeText(content)
      // optionally show a short UI feedback - for now use alert
      alert('Feature copied to clipboard')
    } catch (err) {
      alert('Failed to copy to clipboard')
    }
  }

  return (
    <div>
      <style>{`
        * {
          box-sizing: border-box;
          margin: 0;
          padding: 0;
        }
        
        body {
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
          background-color: #f5f5f5;
          color: #333;
          line-height: 1.6;
        }
        
        .container {
          max-width: 95%;
          width: 100%;
          margin: 0 auto;
          padding: 20px;
          min-height: 100vh;
        }
        
        @media (min-width: 768px) {
          .container {
            max-width: 90%;
            padding: 30px;
          }
        }
        
        @media (min-width: 1024px) {
          .container {
            max-width: 85%;
            padding: 40px;
          }
        }
        
        @media (min-width: 1440px) {
          .container {
            max-width: 1800px;
            padding: 50px;
          }
        }
        
        .header {
          text-align: center;
          margin-bottom: 40px;
        }
        
        .title {
          font-size: 2.5rem;
          color: #2c3e50;
          margin-bottom: 10px;
        }
        
        .subtitle {
          color: #666;
          font-size: 1.1rem;
        }
        
        .form-container {
          background: white;
          border-radius: 8px;
          padding: 30px;
          box-shadow: 0 2px 10px rgba(0,0,0,0.1);
          margin-bottom: 30px;
        }
        
        .form-group {
          margin-bottom: 20px;
        }
        
        .form-label {
          display: block;
          font-weight: 600;
          margin-bottom: 8px;
          color: #2c3e50;
        }
        
        .form-input, .form-textarea {
          width: 100%;
          padding: 12px;
          border: 2px solid #e1e8ed;
          border-radius: 6px;
          font-size: 14px;
          transition: border-color 0.2s;
        }
        
        .form-input:focus, .form-textarea:focus {
          outline: none;
          border-color: #3498db;
        }
        
        .form-textarea {
          resize: vertical;
          min-height: 100px;
        }
        
        .submit-btn {
          background: #3498db;
          color: white;
          border: none;
          padding: 12px 24px;
          border-radius: 6px;
          font-size: 16px;
          font-weight: 600;
          cursor: pointer;
          transition: background-color 0.2s;
        }
        
        .submit-btn:hover:not(:disabled) {
          background: #2980b9;
        }
        
        .submit-btn:disabled {
          background: #bdc3c7;
          cursor: not-allowed;
        }
        
        .error-banner {
          background: #e74c3c;
          color: white;
          padding: 15px;
          border-radius: 6px;
          margin-bottom: 20px;
        }
        
        .loading {
          text-align: center;
          padding: 40px;
          color: #666;
          font-size: 18px;
        }
        
        .results-container {
          background: white;
          border-radius: 8px;
          padding: 30px;
          box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        
        .results-header {
          margin-bottom: 20px;
          padding-bottom: 15px;
          border-bottom: 2px solid #e1e8ed;
        }
        
        .results-title {
          font-size: 1.8rem;
          color: #2c3e50;
          margin-bottom: 10px;
        }
        
        .results-meta {
          color: #666;
          font-size: 14px;
        }
        
        .table-container {
          overflow-x: auto;
        }
        
        .results-table {
          width: 100%;
          border-collapse: collapse;
          margin-top: 20px;
        }
        
        .results-table th,
        .results-table td {
          padding: 12px;
          text-align: left;
          border-bottom: 1px solid #e1e8ed;
        }
        
        .results-table th {
          background: #f8f9fa;
          font-weight: 600;
          color: #2c3e50;
        }
        
        .results-table tr:hover {
          background: #f8f9fa;
        }
        
        .category-positive { color: #27ae60; font-weight: 600; }
        .category-negative { color: #e74c3c; font-weight: 600; }
        .category-edge { color: #f39c12; font-weight: 600; }
        .category-authorization { color: #9b59b6; font-weight: 600; }
        .category-non-functional { color: #34495e; font-weight: 600; }
        
        .test-case-id {
          cursor: pointer;
          color: #3498db;
          font-weight: 600;
          padding: 8px 12px;
          border-radius: 4px;
          transition: background-color 0.2s;
          display: inline-flex;
          align-items: center;
          gap: 8px;
        }
        
        .test-case-id:hover {
          background: #f8f9fa;
        }
        
        .test-case-id.expanded {
          background: #e3f2fd;
          color: #1976d2;
        }
        
        .expand-icon {
          font-size: 10px;
          transition: transform 0.2s;
        }
        
        .expand-icon.expanded {
          transform: rotate(90deg);
        }
        
        .expanded-details {
          margin-top: 15px;
          background: #fafbfc;
          border: 1px solid #e1e8ed;
          border-radius: 8px;
          padding: 20px;
        }
        
        .step-item {
          background: white;
          border: 1px solid #e1e8ed;
          border-radius: 6px;
          padding: 15px;
          margin-bottom: 12px;
          box-shadow: 0 1px 3px rgba(0,0,0,0.05);
        }
        
        .step-header {
          display: grid;
          grid-template-columns: 80px 1fr 1fr 1fr;
          gap: 15px;
          align-items: start;
        }
        
        .step-id {
          font-weight: 600;
          color: #2c3e50;
          background: #f8f9fa;
          padding: 4px 8px;
          border-radius: 4px;
          text-align: center;
          font-size: 12px;
        }
        
        .step-description {
          color: #2c3e50;
          line-height: 1.5;
        }
        
        .step-test-data {
          color: #666;
          font-style: italic;
          font-size: 14px;
        }
        
        .step-expected {
          color: #27ae60;
          font-weight: 500;
          font-size: 14px;
        }
        
        .step-labels {
          display: grid;
          grid-template-columns: 80px 1fr 1fr 1fr;
          gap: 15px;
          margin-bottom: 10px;
          font-weight: 600;
          color: #666;
          font-size: 12px;
          text-transform: uppercase;
          letter-spacing: 0.5px;
        }
      `}</style>
      
      <div className="container">
        <div className="form-container" style={{marginBottom: 20}}>
          <h3 style={{marginBottom: 12, color: '#2c3e50'}}>Jira Integration (UI only)</h3>
          <div className="form-group">
            <label className="form-label">Jira Base URL</label>
            <input className="form-input" placeholder="https://your-domain.atlassian.net" value={jiraBaseUrl} onChange={e => setJiraBaseUrl(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label">Email</label>
            <input className="form-input" placeholder="you@company.com" value={jiraEmail} onChange={e => setJiraEmail(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label">Jira API Key</label>
            <input className="form-input" placeholder="API token" value={jiraApiKey} onChange={e => setJiraApiKey(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label">JQL (optional)</label>
            <input className="form-input" value={jiraJql} onChange={e => setJiraJql(e.target.value)} />
          </div>
          <div style={{display: 'flex', gap: 12}}>
            <button
              type="button"
              className="submit-btn"
              onClick={async () => {
                setJiraError(null)
                setJiraLoading(true)
                try {
                  if (!jiraBaseUrl || !jiraEmail || !jiraApiKey) {
                    throw new Error('Please provide Base URL, Email and API Key')
                  }

                  // Call backend proxy to avoid CORS and secure credentials
                  const resp = await fetch(`${API_BASE_URL}/jira/search`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ baseUrl: jiraBaseUrl, email: jiraEmail, apiKey: jiraApiKey, jql: jiraJql })
                  })

                  if (!resp.ok) {
                    const errBody = await resp.json().catch(() => ({ error: 'Unknown error' }))
                    throw new Error(errBody.error || `HTTP ${resp.status}`)
                  }

                  const data: JiraSearchResponse = await resp.json()
                  setJiraIssues(data.issues || [])
                  setJiraConnected(true)
                } catch (err) {
                  setJiraError(err instanceof Error ? err.message : String(err))
                  setJiraConnected(false)
                  setJiraIssues([])
                } finally {
                  setJiraLoading(false)
                }
              }}
              disabled={jiraLoading}
            >
              {jiraLoading ? 'Connecting...' : 'Connect Jira'}
            </button>
            <button type="button" className="submit-btn" onClick={() => {
              setJiraConnected(false)
              setJiraIssues([])
              setJiraError(null)
            }}>
              Disconnect
            </button>
          </div>
          {jiraError && <div className="error-banner" style={{marginTop: 12}}>{jiraError}</div>}
        </div>
        {jiraConnected && (
          <div className="form-container" style={{marginBottom: 20}}>
            <h3 style={{marginBottom: 12, color: '#2c3e50'}}>Jira Stories</h3>
            {jiraIssues.length === 0 && (
              <div style={{color: '#666'}}>No stories found for the provided JQL.</div>
            )}
            {jiraIssues.length > 0 && (
              <div className="table-container">
                <table className="results-table">
                  <thead>
                    <tr>
                      <th>Key</th>
                      <th>Summary</th>
                    </tr>
                  </thead>
                  <tbody>
                    {jiraIssues.map(issue => (
                      <tr
                        key={issue.id}
                        onClick={() => handleSelectIssue(issue)}
                        style={{
                          cursor: 'pointer',
                          background: selectedIssue?.id === issue.id ? '#e8f4ff' : undefined
                        }}
                      >
                        <td style={{color: '#3498db', fontWeight: 600}}>{issue.key}</td>
                        <td>{issue.fields?.summary}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
        {selectedIssue && (
          <div className="form-container" style={{marginBottom: 20}}>
            <h3 style={{marginBottom: 12, color: '#2c3e50'}}>Selected Story Preview</h3>
            <div style={{marginBottom: 8}}>
              <strong>{selectedIssue.key}</strong> — {selectedIssue.fields?.summary}
            </div>
            <div style={{whiteSpace: 'pre-wrap', color: '#444', marginBottom: 12}}>
              {extractPlainTextFromJiraDescription(selectedIssue.fields?.description) || '(no description)'}
            </div>
            <div style={{display: 'flex', gap: 12}}>
              <button type="button" className="submit-btn" onClick={() => applySelectedIssueToForm(selectedIssue)}>
                Use this story
              </button>
              <button type="button" className="submit-btn" onClick={() => setSelectedIssue(null)}>
                Clear Selection
              </button>
            </div>
          </div>
        )}
        <div className="header">
          <h1 className="title">User Story to Tests</h1>
          <p className="subtitle">Generate comprehensive test cases from your user stories</p>
        </div>
        
        <form onSubmit={handleSubmit} className="form-container">
          <div className="form-group">
            <label htmlFor="storyTitle" className="form-label">
              Story Title *
            </label>
            <input
              type="text"
              id="storyTitle"
              className="form-input"
              value={formData.storyTitle}
              onChange={(e) => handleInputChange('storyTitle', e.target.value)}
              placeholder="Enter the user story title..."
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="description" className="form-label">
              Description
            </label>
            <textarea
              id="description"
              className="form-textarea"
              value={formData.description}
              onChange={(e) => handleInputChange('description', e.target.value)}
              placeholder="Additional description (optional)..."
            />
          </div>
          
          <div className="form-group">
            <label htmlFor="acceptanceCriteria" className="form-label">
              Acceptance Criteria *
            </label>
            <textarea
              id="acceptanceCriteria"
              className="form-textarea"
              value={formData.acceptanceCriteria}
              onChange={(e) => handleInputChange('acceptanceCriteria', e.target.value)}
              placeholder="Enter the acceptance criteria..."
              required
            />
          </div>
          
          <div className="form-group">
            <label htmlFor="additionalInfo" className="form-label">
              Additional Info
            </label>
            <textarea
              id="additionalInfo"
              className="form-textarea"
              value={formData.additionalInfo}
              onChange={(e) => handleInputChange('additionalInfo', e.target.value)}
              placeholder="Any additional information (optional)..."
            />
          </div>
          
          <button
            type="submit"
            className="submit-btn"
            disabled={isLoading}
          >
            {isLoading ? 'Generating...' : 'Generate'}
          </button>
          <div style={{display: 'inline-flex', gap: 12, marginLeft: 12}}>
            <button type="button" className="submit-btn" onClick={downloadFeatureFile}>
              Download Feature
            </button>
            <button type="button" className="submit-btn" onClick={copyFeatureToClipboard}>
              Copy Feature
            </button>
          </div>
        </form>

        {error && (
          <div className="error-banner">
            {error}
          </div>
        )}

        {isLoading && (
          <div className="loading">
            Generating test cases...
          </div>
        )}

        {results && (
          <div className="results-container">
            <div className="results-header">
              <h2 className="results-title">Generated Test Cases</h2>
              <div className="results-meta">
                {results.cases.length} test case(s) generated
                {results.model && ` • Model: ${results.model}`}
                {results.promptTokens > 0 && ` • Tokens: ${results.promptTokens + results.completionTokens}`}
              </div>
            </div>
            
            <div className="table-container">
              <table className="results-table">
                <thead>
                  <tr>
                    <th>Test Case ID</th>
                    <th>Title</th>
                    <th>Category</th>
                    <th>Expected Result</th>
                  </tr>
                </thead>
                <tbody>
                  {results.cases.map((testCase: TestCase) => (
                    <>
                      <tr key={testCase.id}>
                        <td>
                          <div 
                            className={`test-case-id ${expandedTestCases.has(testCase.id) ? 'expanded' : ''}`}
                            onClick={() => toggleTestCaseExpansion(testCase.id)}
                          >
                            <span className={`expand-icon ${expandedTestCases.has(testCase.id) ? 'expanded' : ''}`}>
                              ▶
                            </span>
                            {testCase.id}
                          </div>
                        </td>
                        <td>{testCase.title}</td>
                        <td>
                          <span className={`category-${testCase.category.toLowerCase()}`}>
                            {testCase.category}
                          </span>
                        </td>
                        <td>{testCase.expectedResult}</td>
                      </tr>
                      {expandedTestCases.has(testCase.id) && (
                        <tr key={`${testCase.id}-details`}>
                          <td colSpan={4}>
                            <div className="expanded-details">
                              <h4 style={{marginBottom: '15px', color: '#2c3e50'}}>Test Steps for {testCase.id}</h4>
                              <div className="step-labels">
                                <div>Step ID</div>
                                <div>Step Description</div>
                                <div>Test Data</div>
                                <div>Expected Result</div>
                              </div>
                              {testCase.steps.map((step, index) => (
                                <div key={index} className="step-item">
                                  <div className="step-header">
                                    <div className="step-id">S{String(index + 1).padStart(2, '0')}</div>
                                    <div className="step-description">{step}</div>
                                    <div className="step-test-data">{testCase.testData || 'N/A'}</div>
                                    <div className="step-expected">
                                      {index === testCase.steps.length - 1 ? testCase.expectedResult : 'Step completed successfully'}
                                    </div>
                                  </div>
                                </div>
                              ))}
                            </div>
                          </td>
                        </tr>
                      )}
                    </>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

export default App