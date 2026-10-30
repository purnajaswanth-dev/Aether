import { test, expect } from '@playwright/test';

test.describe('End-to-End Acceptance Flow', () => {
  const testEmail = `engineer_${Date.now()}@aether.test`;

  test('Complete flow: register -> create project -> save artifact -> new version -> compare -> propose decision -> human approve -> view timeline', async ({ page }) => {
    // 1. Visit Welcome Page
    await page.goto('/');
    await expect(page.locator('h1')).toContainText('Welcome to your unified engineering workspace');

    // 2. Register Account
    await page.goto('/register');
    await page.fill('input[placeholder="e.g. Alex Chen"]', 'Alex Architect');
    await page.fill('input[type="email"]', testEmail);
    await page.fill('input[type="password"]', 'Password123!');
    await page.click('button[type="submit"]');

    // Should navigate to projects
    await expect(page).toHaveURL(/\/projects/);

    // 3. Create Project
    await page.goto('/projects/new');
    await page.fill('input[placeholder="e.g. Aether Engineering Platform"]', 'Vionex Platform E2E');
    await page.fill('textarea[placeholder*="Summary of project"]', 'Core persistent context platform E2E verification');
    await page.click('button:has-text("Create Project")');

    // Should open Project Overview
    await expect(page).toHaveURL(/\/projects\/[a-f0-9-]+/);
    await expect(page.locator('h1')).toContainText('Vionex Platform E2E');

    // 4. Save Architecture Document (Version 1)
    await page.click('button:has-text("Save Artifact")');
    await page.fill('input[placeholder*="System Architecture Specification"]', 'Core System Architecture');
    await page.fill('textarea[placeholder*="Paste complete original"]', '# Vionex Architecture Specification v1\n\nImmutability triggers and pgvector RAG active.');
    await page.click('button:has-text("Save Artifact")');

    // Verify artifact is displayed
    await expect(page.locator('text=Core System Architecture')).toBeVisible();
    await expect(page.locator('text=v1')).toBeVisible();

    // 5. Create Version 2
    await page.click('button:has-text("New Version")');
    await page.fill('input[placeholder*="e.g. Added database schema"]', 'Added pgvector section');
    await page.fill('textarea', '# Vionex Architecture Specification v2\n\nImmutability triggers and pgvector RAG active.\n- Added HNSW vector index.');
    await page.click('button:has-text("Append Version")');

    // Verify version 2 pill exists
    await expect(page.locator('button:has-text("v2")')).toBeVisible();

    // 6. Compare Versions
    await page.click('button:has-text("Compare")');
    await expect(page.locator('text=+1')).toBeVisible();

    // 7. Propose and Approve Decision
    await page.click('nav >> text=Decisions');
    await page.click('button:has-text("Propose Decision")');
    await page.fill('input[placeholder*="Adopt PostgreSQL"]', 'Adopt PostgreSQL 16 with pgvector');
    await page.fill('textarea[placeholder*="Describe the architectural challenge"]', 'Need high performance vector similarity and relational transactions.');
    await page.fill('textarea[placeholder*="Exact technical decision"]', 'Use pgvector extension inside PostgreSQL 16.');
    await page.click('button:has-text("Propose Decision")');

    await expect(page.locator('text=PROPOSED')).toBeVisible();

    // Submit for review
    await page.click('button:has-text("Submit for Review")');
    await expect(page.locator('text=UNDER_REVIEW')).toBeVisible();

    // Human Approval
    await page.click('button:has-text("Approve Decision")');
    await expect(page.locator('text=APPROVED')).toBeVisible();

    // 8. View Timeline
    await page.click('nav >> text=Timeline');
    await expect(page.locator('text=PROJECT_CREATED')).toBeVisible();
    await expect(page.locator('text=ARTIFACT_SAVED')).toBeVisible();
    await expect(page.locator('text=DECISION_APPROVED')).toBeVisible();
  });
});

