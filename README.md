# ses-to-q

AWS Lambda that transforms SES email notifications into Amazon Q Developer custom notifications for Slack.

## Architecture

```
SES receipt rule
  → SNS topic (raw email)
    → this Lambda (parses email, formats notification)
      → SNS topic (Q Developer custom notification)
        → Amazon Q Developer
          → Slack channel
```

Built with Micronaut 4.9 and compiled to a GraalVM native image for fast Lambda cold starts.

## Configuration

The Lambda requires one environment variable:

| Variable | Description |
|----------|-------------|
| `TARGET_SNS_TOPIC_ARN` | ARN of the SNS topic that Amazon Q Developer subscribes to |

## Container images

Published to GitHub Container Registry on every version tag:

```
ghcr.io/functorful/ses-to-q:<tag>
```

## Versioning

Tags are created automatically on every push to `main` using [github-tag-action](https://github.com/anothrNick/github-tag-action). The default bump is **patch**.

### Controlling the version bump

Include one of these tokens in your commit message to control the bump:

| Token | Example | Result |
|-------|---------|--------|
| `#patch` | `fix: handle null body #patch` | `v0.1.0` → `v0.1.1` |
| `#minor` | `feat: add html parsing #minor` | `v0.1.0` → `v0.2.0` |
| `#major` | `refactor!: new notification format #major` | `v0.1.0` → `v1.0.0` |
| `#none` | `docs: update readme #none` | no tag created |

If no token is present, the default `#patch` bump is applied.

When multiple tokens are present, the highest-ranking one wins: `#major` > `#minor` > `#patch` > `#none`.

## Development

```bash
./gradlew build              # compile + test
./gradlew test               # tests only
./gradlew optimizedDockerBuildNative   # build native Docker image
```

Requires Java 21 and GraalVM.
