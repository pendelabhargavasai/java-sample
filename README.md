# **HARNESS DELEGATE CUSTOM IMAGE – WORKFLOW AND REPOSITORY OVERVIEW**

## 1. PURPOSE

This repository automates the creation and security validation of a custom Harness Delegate Docker image.

The implementation is organized into two GitHub Actions workflows::
1.  `delegate-image.yaml` Builds the actual custom Harness Delegate image.
2.  `vendor-delegate-image.yaml` Handles the vendor/base image publishing process through a shared vendor-image workflow used in Dockerfile as a base image.
The repository also contains: 
- Environment-specific configuration under `delegate-image-download/config/` also the current environment structure includes dev and prod.
- Helm/Kubernetes templates under `delegate-image-download/templates/` 
- Chart.yaml 
- Dockerfile 
- pipeline.json

The current environment structure includes `dev` and `prod`.

## 2. REPOSITORY STRUCTURE

``` text

.github/

└── workflows/

├── delegate-image.yaml

└── vendor-delegate-image.yaml

  

delegate-image-download/

├── config/

│ ├── dev/

│ │ └── values.yaml

│ └── prod/

│ └── values.yaml

├── templates/

│ ├── clusterrolebinding.yaml

│ ├── deployment.yaml

│ └── secret.yaml

├── Chart.yaml

├── Dockerfile

├── pipeline.json

└── README.md

```



## 3. CUSTOM DELEGATE IMAGE WORKFLOW STEPS

It is designed to be started manually from GitHub Actions. The workflow accepts these important inputs: 
- dev 
- prod

This input determines which environment-specific `values.yaml` is read by the workflow.

delegate_version: Optional Harness Delegate version override. If blank, the workflow reads delegateVersion from the selected environment’s values.yaml.

image_tag: Optional Docker image tag override. If blank, the Delegate version is used as the image tag.

push_to_artifactory: Controls whether the resulting image is pushed to Artifactory.

The workflow defines values including:

-  DOCKER_REGISTRY
-  IMAGE_NAME
-  DOCKERFILE_PATH

The registry must be changed/configured appropriately for production if the production image must be stored in a production registry.


> Note:- The following are the implementation-relevant steps. GitHub checkout/setup steps are intentionally omitted because they are infrastructure/internal workflow operations.


<ins>**3.1 SET BUILD ENVIRONMENT VARIABLES**

1. Loads common pipeline/environment properties through the shared load-pipeline-props action.
2. It supplies values such as: 
- GitHub token 
- Secret context 
- Globalsecret context 
- Organization secret context

These values are later consumed by the workflow and security/integration steps.

*Output:* The step establishes the environment/secret context required by subsequent steps.

<ins>**3.2 SET IMAGE VARIABLES**

This is one of the most important steps because it connects the workflow input to the environment-specific configuration.
The workflow does:

    ENV_NAME=“${{ inputs.env }}"
    VALUES_FILE="delegate-image-download/config/${ENV_NAME}/values.yaml"
    
So selecting dev or prod changes the values file used by the image/version determination logic.

Therefore: The workflow checks that the selected file exists.

> env=dev -> config/dev/values.yaml
> env=prod -> config/prod/values.yaml

Delegate version logic: 
1. If delegate_version was supplied manually, use it. 
2. Otherwise read delegateVersion from the selected values.yaml.

Image tag logic: 
1. If image_tag was supplied, use it. 
2. Otherwise use DELEGATE_VERSION value.

Then it constructs:

    FULL_IMAGE_NAME = DOCKER_REGISTRY/IMAGE_NAME:IMAGE_TAG

The following values are exported for later workflow steps: 
- ENV_NAME
- DELEGATE_VERSION 
- IMAGE_TAG 
- FULL_IMAGE_NAME 

*Output:* Environment-specific image/version information is made available
to subsequent steps.

<ins>**3.3 LOGIN TO DOCKER ARTIFACTORY**

The workflow authenticates Docker against the configured Artifactory/Docker registry.
The credentials come from environment/secret values such as the JFrog username and password.

*Output:* A successful Docker login allows the subsequent Docker build and push operations to access the required registry.

<ins>**3.4 BUILD HARNESS DELEGATE DOCKER IMAGE**

This is the actual custom image creation step.
First it will determines the architecture and the output value is exported as TARGETARCH.
- x86_64 -> amd64 
- aarch64 -> arm64 
- arm64 -> arm64

Then builds the docker image using: `delegate-image-download/Dockerfile` file.
Important build arguments: 
- TARGETARCH 
- DELEGATE_VERSION

Then the generated image is tagged using `FULL_IMAGE_NAME`variable.

The Docker build also adds metadata labels such as: 
- maintainer 
- build URL 
- Artifactory repository 
- Git branch 
- Git commit 
- Git commit SHA 
- Git URL 
- image tag 
- image description

*Output:* A custom Harness Delegate Docker image is built locally on the GitHub Actions runner.
The workflow verifies the result using:

``` bash

docker  images | grep  harness-delegate

```

<ins>**3.5 PRISMA CLOUD IMAGE SCAN**

After the image is built, the workflow invokes the Prisma Cloud image scanning action. The scan is provided with the Prisma Cloud console URL and the required security credentials.

The image is scanned for known vulnerabilities and security issues before it is considered ready for deployment.

*Output:* The scan produces security findings/results associated with the built Docker image.

<ins>**3.6 PRISMA CLOUD IMAGE SCAN RESULTS**

Processes the results from the Prisma Cloud scan, to determine whether the image satisfies the organization's security requirements.

*Output:* The workflow receives/report the image security scan result

<ins>**3.7 PUSH DOCKER IMAGE / BUILD SUMMARY**

The workflow contains post-build handling for the image publication and build reporting. The resulting image is identified by `FULL_IMAGE_NAME`. 
The build summary reports information about the resulting Delegate version/image and workflow execution.
Depending on the `push_to_artifactory` setting and the remaining workflow logic, the image can be pushed to the configured Artifactory registry.


## 4. VENDOR IMAGE PROCESS

This is a separate workflow used for the vendor base image, it is also manually triggered with `workflow_dispatch`. And Its purpose is to handle the **vendor/base image publishing process**.

Important inputs:

Image-name: The image or images to process.
Registry: Target registry selection: 
- none 
- prod 
- non-prod

Project-name: Target image/project directory in the KP registry.
Atlas-app-id: Atlas application identifier.
Approver-nuid: Approver’s NUID.
Technical-sme-mail-id: Technical SME email identifier.
Login: Used when external credentials/login information is required.

<ins>**4.1  VENDOR IMAGE JOB**

The workflow creates a job named vendor-image with display name Vendor image publish. It inherits secrets and calls the shared workflow.
``` text

devsecops/shared-github-actions/.github/workflows/vendor_base_images.yaml@master

```
The shared vendor workflow performs the vendor-image build/publish process and that involves
 1. Vendor Docker Image Build 
 2. Docker Push to non-prod Registry

The exact downstream behavior is controlled by the shared vendor_base_images.yaml workflow.

*Output:* The shared vendor workflow performs the vendor image build/publish process.

## 5. End-to-End Execution of Custom Delegate Image workflow

<ins>The overall sequence is:</ins>

1. Manually trigger `delegate-image.yaml`.
2. Select `dev`, `staging`, or `prod`.
3. Load common pipeline properties.
4. Select `config/<env>/values.yaml`.
5. Determine Delegate version.
6. Determine Docker image tag.
7. Construct the full Docker image name.
8. Authenticate to Docker Artifactory.
9. Determine target CPU architecture.
10. Build the custom Harness Delegate image.
11. Run Prisma Cloud vulnerability scanning.
12. Process the scan results.
13. Publish/report the build result and push the image when enabled.

## 5. PRIMARY OUTPUTS

The implementation produces the following major outputs:
<ins>Environment-specific configuration:</ins> Separate values files for dev and prod allowing the same Kubernetes templates to be reused across environments.
<ins>Docker image:</ins> A final custom Harness Delegate image stored in image artifactory
<ins>Security scan:</ins> Prisma Cloud vulnerability/security result for the built image.
<ins>Helm chart:</ins> Reusable harness-delegate chart containing: Deployment, Secret and ClusterRoleBinding


## 6. SUMMARY OF FILE RESPONSIBILITIES

 1. .github/workflows/delegate-image.yaml:- Builds, scans and publishes the custom Harness Delegate image.
 2. .github/workflows/vendor-delegate-image.yaml:- Handles vendor/base image publishing through the shared vendor workflow.
 3. config/dev/values.yaml:- Dev-specific Helm/image configuration.
 4. config/prod/values.yaml:- Prod-specific Helm/image configuration.
 5. templates/deployment.yaml:- This template creates the Kubernetes Deployment resource required for the Delegate installation in k3s.
 6. templates/secret.yaml:- This template creates the Kubernetes Secret resources required by the Delegate deployment.
 7. templates/clusterrolebinding.yaml:- This template defines the Kubernetes RBAC binding required by the Delegate.
 8. Chart.yaml:- Defines the Helm chart metadata.
 9. Dockerfile:- Builds the custom Delegate Docker image.
 10. pipeline.json:- Supporting pipeline configuration content.
