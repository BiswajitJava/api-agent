# AI API Agent CLI 🤖

A powerful, AI-driven command-line interface (CLI) that learns any API from an OpenAPI specification and allows you to interact with it using natural language.

This tool translates your plain English queries into a multi-step, executable plan, asks for your confirmation, and then intelligently calls the API to get the job done. It's like having a conversation with your APIs.

---

## 📋 Table of Contents

- [✨ Features](#-features)
- [⚙️ How It Works](#️-how-it-works)
- [🚀 Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Configuration](#configuration)
  - [Build and Run](#build-and-run)
- [🧑‍💻 Usage](#-usage)
  - [Step 1: Learn an API](#step-1-learn-an-api)
  - [Step 2: Configure Authentication](#step-2-configure-authentication)
  - [Step 3: Query the API](#step-3-query-the-api)
- [📂 Project Structure](#-project-structure)
- [🛠️ Key Technologies](#️-key-technologies)
- [💡 Future Improvements](#-future-improvements)

---

## ✨ Features

- **Learn Any API**: Ingests and understands any API documented with an OpenAPI v3 specification.
- **AI-Powered Planning**: Leverages a Large Language Model (LLM) to translate natural language prompts into a concrete, multi-step execution plan.
- **Interactive Execution**: Displays the generated plan for user review and confirmation before making any live API calls.
- **Dynamic Parameter Resolution**: Intelligently identifies when it needs more information and prompts the user for required parameters at runtime.
- **Secure Credential Storage**: Encrypts and stores API keys and tokens locally using Jasypt, keeping your secrets safe.
- **Resilient API Calls**: Automatically retries API calls on transient errors (like HTTP 429 or 5xx) with an exponential backoff strategy, powered by Resilience4j.
- **Verbose Debug Mode**: A `-v` flag provides detailed insight into the planning, parameter resolution, and execution process.

## ⚙️ How It Works

The agent follows a simple but powerful three-stage process: **Learn, Plan, and Execute**.

1.  **Learn (`learn` command)**:
    - You provide a URL or file path to an OpenAPI specification and a simple alias (e.g., `petstore`).
    - The `OpenApiServiceImpl` parses the spec, extracting a simplified model of all available operations, parameters, server URLs, and security schemes.
    - This simplified model is stored locally in a JSON file (`~/.api-agent/state.json`).

2.  **Plan (`query` command)**:
    - You provide a natural language prompt (e.g., `"find all available pets"`).
    - The `AiPlanningServiceImpl` constructs a detailed "system prompt" for the LLM. This prompt includes:
        - The list of all available API operations (formatted as "tools").
        - The strict JSON schema for the required output (`ExecutionPlan`).
        - Instructions on how to handle missing information (using `USER_INPUT`).
        - Examples to guide the model.
    - The LLM receives this context and your prompt, and returns a JSON object representing the `ExecutionPlan`.

3.  **Execute (`query` command, post-confirmation)**:
    - After you approve the plan, the `ExecutionEngineImpl` takes over.
    - It iterates through each step in the plan.
    - For each step, it resolves the required parameters by:
        - Using a `STATIC` value defined in the plan.
        - Prompting you for `USER_INPUT`.
        - Extracting a value from a previous step's output using `FROM_STEP` and a JSONPath expression.
    - It intelligently builds the HTTP request, applying the correct, encrypted credentials based on the API spec's security requirements.
    - It executes the request, captures the result, and proceeds to the next step, finally returning the result of the last step.

## 🚀 Getting Started

### Prerequisites

- **Java 17+**
- **Maven 3.x+**
- **An LLM API Key**: An API key from a compatible provider (e.g., OpenAI, Groq, etc.).

### Configuration

The application is configured via `src/main/resources/application.properties`. You will also need to set one environment variable for the encryption master password.

1.  **Set the Encryption Master Password**:
    This is a secret key used by Jasypt to encrypt and decrypt your API credentials. Set it as an environment variable.

    ```bash
    export JASYPT_ENCRYPTOR_PASSWORD="your-strong-secret-password"
    ```

2.  **Update `application.properties`**:
    Create or update the file with your LLM provider's details.

    ```properties
    # LLM Configuration
    llm.api.endpoint=https://api.groq.com/openai/v1/chat/completions
    llm.model=llama3-8b-8192
    llm.api.key=YOUR_LLM_API_KEY_HERE

    # Jasypt Encryption Configuration (uses the environment variable)
    jasypt.encryptor.bean=stringEncryptor
    jasypt.encryptor.algorithm=PBEWITHHMACSHA512ANDAES_256
    jasypt.encryptor.iv-generator-classname=org.jasypt.iv.RandomIvGenerator
    jasypt.encryptor.salt-generator-classname=org.jasypt.salt.RandomSaltGenerator
    jasypt.encryptor.string-output-type=base64
    jasypt.encryptor.password-env-name=JASYPT_ENCRYPTOR_PASSWORD
    ```

### Build and Run

1.  **Build the JAR**:
    ```bash
    mvn clean package
    ```

2.  **Run the Application**:
    Remember to set the `JASYPT_ENCRYPTOR_PASSWORD` environment variable in the same shell session.
    ```bash
    export JASYPT_ENCRYPTOR_PASSWORD="your-strong-secret-password"
    java -jar target/api-agent-0.0.1-SNAPSHOT.jar
    ```
    You will be greeted by the Spring Shell prompt: `shell:>`.

## 🧑‍💻 Usage

Here is a complete workflow using the public Petstore API.

### Step 1: Learn an API

First, teach the agent about the API you want to use. We'll use the Petstore API and give it the alias `petstore`.

```bash
shell:> learn --alias petstore --source https://petstore3.swagger.io/api/v3/oas.json
