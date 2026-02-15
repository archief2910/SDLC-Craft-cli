# Branch Protection Configuration

To enforce CI checks and code reviews before merging to `main`, configure the following settings in your GitHub repository.

## Steps to Configure

1. Go to your repository on GitHub
2. Navigate to **Settings** → **Branches**
3. Click **Add branch protection rule**
4. Configure the following:

### Branch name pattern
```
main
```

### Protection Rules

#### Require a pull request before merging
- ✅ Enable this option
- **Required approvals**: 1 (minimum)
- ✅ Dismiss stale pull request approvals when new commits are pushed
- ✅ Require review from Code Owners (optional, if you have CODEOWNERS file)

#### Require status checks to pass before merging
- ✅ Enable this option
- ✅ Require branches to be up to date before merging
- **Required status checks** (add these):
  - `build-test`
  - `sast-sbom`

#### Additional Recommended Settings
- ✅ Require conversation resolution before merging
- ✅ Do not allow bypassing the above settings
- ✅ Restrict who can push to matching branches (optional - for stricter control)

## What This Achieves

- **Automated Quality Gates**: All PRs must pass Maven tests, Go tests, linting, and security scans
- **Human Review**: At least one team member must review and approve changes
- **Security**: Trivy catches high/critical vulnerabilities before merge
- **Traceability**: SBOM artifacts track dependencies for each build
- **Consistency**: Prevents direct pushes to main, enforcing the PR workflow

## Testing the Setup

1. Create a test branch: `git checkout -b test/ci-validation`
2. Make a small change and push
3. Open a PR to `main`
4. Verify that CI jobs run automatically
5. Confirm that merge is blocked until:
   - All CI checks pass (green)
   - At least one approval is given

## CI Job Details

### build-test
- Runs Maven tests for Java backend
- Runs Go unit tests for CLI
- Runs golangci-lint for Go code quality

### sast-sbom
- Trivy security scan (HIGH + CRITICAL vulnerabilities)
- Generates Software Bill of Materials (SBOM) with Syft
- Uploads SBOM as workflow artifact
