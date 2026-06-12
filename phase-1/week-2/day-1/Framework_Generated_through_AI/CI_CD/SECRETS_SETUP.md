# 🔐 GitHub Secrets Setup Guide

Configure these secrets in your repository before running the workflows.

## How to Add Secrets
1. Go to your GitHub repository
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**

## Required Secrets

| Secret Name        | Description                              | Example Value              |
|--------------------|------------------------------------------|----------------------------|
| `API_BEARER_TOKEN` | Bearer token for API auth (all envs)     | `eyJhbGciOiJSUzI1...`      |

## Optional Secrets (if using environment-specific tokens)

| Secret Name               | Description              |
|---------------------------|--------------------------|
| `API_BEARER_TOKEN_DEV`    | Dev environment token    |
| `API_BEARER_TOKEN_QA`     | QA environment token     |
| `API_BEARER_TOKEN_STAGING`| Staging environment token|

## GitHub Pages Setup (for Allure report publishing)
1. Go to **Settings** → **Pages**
2. Set **Source** to `gh-pages` branch
3. Allure report will be available at:
   `https://<your-username>.github.io/<repo-name>/allure-report`

## Workflow Permissions
Ensure Actions have write permissions:
1. Go to **Settings** → **Actions** → **General**
2. Under **Workflow permissions**, select **Read and write permissions**
3. Check **Allow GitHub Actions to create and approve pull requests**
