# Kinescope

A super simple Web UI to list records in AWS Kinesis.

## Configuration

You can configure Kinescope by providing environment variables.
Relevant names and default values are listed in a table below.
AWS settings is compatible with AWS CLI.
*Environment variables marked in **bold** are required!*

| environment variable name | default value |
|---------------------------|---------------|
| HTTP_HOST                 | 0.0.0.0       |
| HTTP_PORT                 | 8888          |
| **AWS_ACCESS_KEY_ID**     | -             |
| **AWS_SECRET_ACCESS_KEY** | -             |
| AWS_SESSION_TOKEN         | -             |
| **AWS_REGION**            | -             |
| AWS_ENDPOINT_OVERRIDE     | -             |
