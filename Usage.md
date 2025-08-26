# API Agent Usage Guide

## Overview

The API Agent is a command-line interface (CLI) tool that allows you to interact with REST APIs using natural language. It learns an API's structure from an OpenAPI specification, allows you to securely store credentials, and then translates your prompts (e.g., "add a new pet named Fido") into real API calls.

## Docker Setup

The easiest way to run the API Agent is with Docker. This ensures all dependencies are correctly managed in a clean, isolated environment.

### Prerequisites

*   You must have Docker installed and running on your system.
*   You are in the root directory of the project, where the `Dockerfile` is located.

### 1. Building the Docker Image

First, build the Docker image. This command packages the application into a self-contained image named `api-agent`.

```bash
docker build -t api-agent .
```

### 2. Running the Interactive Shell

To run the agent, you need to provide three essential pieces of information as environment variables. The command below also mounts a local directory into the container to **persist your learned APIs and credentials** even after the container stops.

```bash
docker run -it --rm \
  -e API_KEY="sk-your-real-openai-api-key" \
  -e MODEL="gpt-4-turbo" \
  -e ENCRYPTOR_PASSWORD="your-strong-secret-password" \
  -v "$(pwd)/.api-agent:/home/spring/.api-agent" \
  api-agent
```

**Command Breakdown:**
*   `docker run -it --rm`: Runs the container in interactive mode (`-it`) and automatically removes it when you exit (`--rm`).
*   `-e API_KEY="..."`: **Required.** Your secret API key from your LLM provider (e.g., OpenAI).
*   `-e MODEL="..."`: **Required.** The specific LLM model to use (e.g., `gpt-4-turbo`, `gpt-3.5-turbo`).
*   `-e ENCRYPTOR_PASSWORD="..."`: **Required.** A secret password you create. It is used to encrypt credentials stored in the state file.
*   `-v "$(pwd)/.api-agent:/home/spring/.api-agent"`: **Highly Recommended.** This creates a directory named `.api-agent` in your current folder and links it to the agent's internal storage. This saves your state, so you don't have to `learn` APIs every time you start the container.
*   `api-agent`: The name of the image to run.

Once you run this command, you will be dropped into the interactive shell, ready to issue commands.

```
shell:>
```

---

## Command Reference

### `learn`
**Purpose:** Teaches the agent about a new API by parsing its OpenAPI specification.
**Syntax:** `learn --alias <alias> --source <url-or-file-path>`
*   `alias`: A short, unique name you will use to refer to this API.
*   `source`: The URL or local file path to the OpenAPI `yaml` or `json` file.

**Example:**
```bash
shell:> learn --alias petstore --source https://raw.githubusercontent.com/swagger-api/swagger-petstore/master/src/main/resources/openapi.yaml
```

### `auth`
**Purpose:** Securely configures and stores authentication credentials for a learned API.
**Syntax:** `auth --alias <alias> --type <type> --token <token>`
*   `alias`: The alias of the already-learned API.
*   `type`: The type of authentication. Currently, `api_key` is the primary supported type.
*   `token`: Your actual secret token or API key.

**Example:**
```bash
shell:> auth --alias petstore --type api_key --token special-key
```

### `details`
**Purpose:** Displays detailed information for a learned API. Can show all operations or focus on just one.
**Syntax:** `details <alias> [--operation|-o <operationId>]`
*   `alias`: The alias of the API to inspect.
*   `--operation` or `-o`: **Optional.** The specific `operationId` you want to see details for.

**Example 1: List All Operations**
```bash
shell:> details petstore
```

**Example 2: Get Details for a Single Operation**
```bash
shell:> details petstore --operation addPet
```

### `auth-info`
**Purpose:** Shows the security requirements for a learned API, telling you what kind of authentication is needed.
**Syntax:** `auth-info <alias>`

**Example:**
```bash
shell:> auth-info petstore
```

### `query`
**Purpose:** Executes a natural language prompt against a learned API.
**Syntax:** `query --alias <alias> --prompt "<prompt>" [--autofill|-af]`
*   `--autofill` or `-af`: **Optional.** If provided, the agent will not ask for request body fields. Instead, it will automatically generate plausible, fake data based on the API schema. This is ideal for quick tests.

**Example 1: Direct Execution (Data Provided)**
```bash
shell:> query --alias petstore --prompt "find pet with ID 123"
```

**Example 2: Interactive Mode (Data Missing)**
The agent will ask for the fields it needs to build the request.
```bash
shell:> query --alias petstore --prompt "add a new pet to the store"
```

**Example 3: Autofill Mode (For Testing)**
The agent will instantly execute the request with valid, auto-generated data, without asking any questions.
```bash
shell:> query --alias petstore --prompt "add a new pet" --autofill
```

### `exit` or `quit`
**Purpose:** Exits the interactive shell and stops the container.

---

## Full Workflow Example

**Step 1: Learn the API**
```bash
shell:> learn --alias petstore --source https://raw.githubusercontent.com/swagger-api/swagger-petstore/master/src/main/resources/openapi.yaml
```

**Step 2: Configure Authentication**
```bash
shell:> auth --alias petstore --type api_key --token special-key
```

**Step 3: Test the `addPet` Endpoint Quickly with Autofill**
You want to see if the endpoint works without manually entering data.

```bash
shell:> query --alias petstore --prompt "add a new pet" --autofill
```

The agent will show a plan, confirm that autofill is active, and upon your approval, immediately execute the request with intelligently generated fake data.

```
> I have generated the following plan:
>   1. The user wants to add a new pet to the store.
>
> -- Autofill mode is active. Request bodies will be generated automatically. --
> Execute this plan? [y/N]: y
> Executing plan...
> {
>   "id": 8432,
>   "name": "fake-name-52",
>   "status": "available",
>   ...
> }
```

**Step 4: Add a Specific Pet Interactively**
Now you want to add a pet with real data.

```bash
shell:> query --alias petstore --prompt "I want to add a new pet"
```

The agent will prompt you for the details.

```
> I have generated the following plan:
>   1. The user wants to add a new pet but has not provided the data. I will ask for each required field to build the request body.
> Execute this plan? [y/N]: y
> Executing plan...
> Please provide a value for 'name': Fido
> Please provide a value for 'photoUrls': http://images.com/fido.jpg
> ...
```

**Step 5: Exit the Shell**
```bash
shell:> exit
```