
```markdown
# AI API Agent CLI: Command Reference

This is a quick-reference guide for the core commands of the AI API Agent CLI.

---

## Command: `learn`

Teaches the agent about a new API by reading its OpenAPI specification.

### Syntax
```bash
learn --alias <alias> --source <url_or_filepath>
```

### Options
*   `-a`, `--alias` (Required): A short, memorable name for the API (e.g., `petstore`).
*   `-s`, `--source` (Required): The URL or local file path to the OpenAPI spec (JSON or YAML).

### Example
```bash
shell:> learn --alias petstore --source https://raw.githubusercontent.com/swagger-api/swagger-petstore/master/src/main/resources/openapi.yaml
Successfully learned API 'petstore'
```

---

## Command: `auth`

Securely saves the authentication credentials (like an API key) for a learned API.

### Syntax
```bash
auth --alias <alias> --type <auth_type> --token <your_token>
```

### Options
*   `-a`, `--alias` (Required): The alias of the API to configure.
*   `-t`, `--type` (Required): The type of authentication. Currently supports `api_key`.
*   `-t`, `--token` (Required): Your secret API key or token string.

### Example
```bash
shell:> auth --alias petstore --type api_key --token special-key
Successfully configured authentication for 'petstore'
```

---

## Command: `query`

Executes a natural language request against a learned API, generating and executing a plan.

### Syntax
```bash
query --alias <alias> --prompt <your_prompt> [--verbose]
```

### Options
*   `-a`, `--alias` (Required): The alias of the API to query.
*   `-p`, `--prompt` (Required): Your request in plain English. No quotes are needed for multi-word prompts.
*   `-v`, `--verbose` (Optional): Enables detailed debug logging to see the inner workings.

### Example 1: Simple Query (Information Provided)
The agent finds all necessary information in the prompt and executes directly.

```bash
shell:> query --alias petstore --prompt find pet with ID 10

I have generated the following plan:
  1. The user provided the pet ID, so I can directly fetch its details.
Execute this plan? [y/N]: y
Executing plan...

{
  "id": 10,
  "category": {
    "id": 1,
    "name": "Dogs"
  },
  "name": "doggie",
  "status": "available"
  ...
}
```

### Example 2: Interactive Query (Information Missing)
The agent identifies missing information and prompts the user for it.

```bash
shell:> query --alias petstore --prompt find pets by status

I have generated the following plan:
  1. The user wants to find pets by status, but did not specify which status. I need to ask the user for this information.
Execute this plan? [y/N]: y
Executing plan...
Please provide a value for 'status': available

[
  {
    "id": 10,
    "name": "doggie",
    "status": "available"
    ...
  }
]
```
