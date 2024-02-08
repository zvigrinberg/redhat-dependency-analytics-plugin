![openshift](https://user-images.githubusercontent.com/37098367/114392384-522d8480-9bb6-11eb-8cd8-cdc6191f6a79.png)
***

# Table of Contents
- [Introduction](#red-hat-dependency-analytics)
- [How To Use The Plugin](#how-to-use-the-plugin)
  - [Admin Steps](#admin-steps)
    - [Install The Plugin](#1-install-the-redhat-dependency-analytics-jenkins-plugin)
    - [Configuration](#2-configuration)
      - [Customization](#customization)  
      - [General Configuration](#general-configuration)  
      - [Python Pipeline Configuration](#python-pipeline-configuration)  
    - [Invoking The Plugin](#using-the-plugin)
      - [1. Build Step](#option-i--as-a-build-step)
      - [2. Pipeline Task](#option-ii--as-a-pipeline-task)
      - [Return Code](#return-code-from-plugin)
- [Results](#results)
  - [1. Console Output](#1-console-output)
  - [2. RHDA Stack Report](#2-rhda-stack-report)
  - [3. Detailed RHDA Stack Report](#3-detailed-rhda-stack-report)
- [Snyk Registration](#snyk-registration)
  - [1. Via SNYK website](#1-via-snyk-website)
  - [2. Via CLI](#2-via-cli)
  - 
# Red Hat Dependency Analytics
Red Hat Dependency Analytics (RHDA) is a Jenkins plug-in developed by **Red Hat Inc**. RHDA is powered by **Snyk Intel Vulnerability DB**, which is the most advanced and accurate open source vulnerability database in the industry. It adds value with the latest, fastest and more number of vulnerabilities derived from numerous sources.

'RHDA Report' with Insights about your application dependencies:
- Flags a security vulnerability(CVE) and suggests a remedial version
- Shows Github popularity metrics along with latest version
- Suggests a project level license, check for conflicts between dependency licences
- AI based guidance for additional, alternative dependencies

The plugin can be used in Jenkins as a pipeline task or as a build step.

## How to use the plugin
### Admin Steps
### 1. Install the redhat-dependency-analytics Jenkins plugin
- From the Jenkins Dashboard, click **Manage Jenkins** -> **Plugins** -> **Available Plugins**.
- If Maven is not installed, search for _Maven Integration_, and install the plug in.
- Search for _redhat-dependency-analytics_, and install the plug in.
- Restart Jenkins.

### 2. Configuration
Make sure that the Path is updated to point to the corresponding executables, like `mvn`, `pip` etc.

#### Customization

There is a way to set a custom Package Managers paths using <em>Environment Variables</em>. 

- Click on Manage Jenkins -> System, scroll down to Global properties/Environment Variables.
- Set the corresponding custom path based on your project: 
  - For Maven - Set Name: _EXHORT_MVN_PATH_ and Value: `/path/to/custom/mvn`.
  - For NPM - Set Name: _EXHORT_NPM_PATH_ and Value: `/path/to/custom/npm`.
  - For GO - Set Name: _EXHORT_GO_PATH_ and Value: `/path/to/custom/go`.
  - For Python3 - Set Name: _EXHORT_PYTHON3_PATH_ and Value: `/path/to/custom/python3`.
  - For Pip3 - Set Name: _EXHORT_PIP3_PATH_ and Value: `/path/to/custom/pip3`.
  - For Python - Set Name: _EXHORT_PYTHON_PATH_ and Value: `/path/to/custom/python`.
  - For Pip - Set Name: _EXHORT_PIP_PATH_ and Value: `/path/to/custom/pip`.

If you have a Snyk token, add that as an environment variable:
- Click on Manage Jenkins -> System, scroll down to Global properties/Environment Variables.
- Set the variable name to _EXHORT_SNYK_TOKEN_, and copy-and-paste your Snyk token into the value field.


#### General Configuration
 Click on Manage Jenkins -> System, scroll down to Global properties/Environment Variables, And there you can set the following settings:
 - name: _EXHORT_DEBUG_, Value: true , Description: Will invoke the analysis in verbose mode and will print a lot of useful logs to job output console - good for debugging, Default value is false.


 - name: _EXHORT_DEV_MODE_, value: true, Description: Will invoke the Analysis on Staging Instance Of EXHORT Service, Default: false ( EXHORT Production Instance)


 - name: _HIGHEST_ALLOWED_VULN_SEVERITY_, Possible values: [`LOW`,`MEDIUM`,`HIGH`,`CRITICAL`], Description: will determine what is the highest allowed Severity of a vulnerability found for a given package/dependency in the analysis, for the analysis to be considered Successful(RC=0) and not Vulnerable(RC=2), Default value is `MEDIUM`

#### Python Pipeline Configuration
 For python PIP Packages, you can bypass using the python and pip binaries during the invocation of the analysis, and just use them elsewhere in the pipeline job (another stage, or even another agent/node),
 The Idea is to bring maximum flexibility with the python and pip versions, so you won't enforce the user to install sometimes a different python and pip versions just to adapt it to the exact requirements.txt' packages' versions, as python pip is very sensitive to versioning (for each python and pip version , there is only a limited range of supported versions for every package).
 There are two environment variables:
  1. _EXHORT_PIP_FREEZE_
  2. _EXHORT_PIP_SHOW_ 
 

 This feature enable you to use python on different agent ( for example, container image of python containing the desired python version), install the input requirements.txt using pip in the container image, and then invoke `pip freeze --all` and `pip show [List_Of_Packages]`, and save the output of these to a workspace files,
 And then set the above variables with these output after encoding them using base64 encoding.

Example Pipeline With proper usage:

```yaml
node {
    def dockerArguments= '--user=root'
    def pipFreezeOutput
    def pipShowOutput
    def pythonImage = "python:${params.PYTHON_VERSION}-slim"
    def gitRepoWithRequirements = "${params.REQUIREMENTS_GIT_REPO}"
    def gitRepoWithRequirementsBranch = "${params.REQUIREMENTS_GIT_BRANCH}"


    stage('Checkout Git Repo') { // for display purposes
        // Get some code from a GitHub repository
        dir('requirementsDir') {
            git  branch: gitRepoWithRequirementsBranch, url: gitRepoWithRequirements
        }

    }

    stage('Install Python Package') {
        docker.withTool('docker-tool') {
            docker.withServer('tcp://localhost:2376','docker-server-certs'){

                docker.image(pythonImage).inside(dockerArguments) {
                    sh 'pip install -r requirementsDir/requirements.txt'
                    pipFreezeOutput = sh(script: "pip freeze --all" ,returnStdout: true ).trim()
                    writeFile([file: 'pip-freeze.txt', text: pipFreezeOutput])
                    pipFreezeOutput = sh(script: "pip freeze --all | awk -F \"==\" '{print \$1}' | tr \"\n\" \" \"" ,returnStdout: true ).trim()
                    pipShowOutput = sh(script:"pip show ${pipFreezeOutput}" ,returnStdout: true )
                    writeFile([file: 'pip-show.txt', text: pipShowOutput])


                }
            }
        }
    }
    stage('RHDA Run Analysis') {
        def pipFreezeB64= sh(script: 'cat pip-freeze.txt | base64 -w0' ,returnStdout: true ).trim()
        def pipShowB64= sh(script: 'cat pip-show.txt | base64 -w0',returnStdout: true ).trim()
        echo "pipFreezeB64= ${pipFreezeB64}"
        echo "pipShowpipShowB64= ${pipShowB64}"
        withEnv(["EXHORT_PIP_FREEZE=${pipFreezeB64}","EXHORT_PIP_SHOW=${pipShowB64}"]) {

            rhdaAnalysis consentTelemetry: true, file: "${WORKSPACE}/requirementsDir/requirements.txt"
        }

    }

    stage('Clean Workspace') {
        cleanWs cleanWhenAborted: false, cleanWhenFailure: false, cleanWhenNotBuilt: false, cleanWhenUnstable: false
    }

}
```
 
 

### Using The Plugin

#### Option I- As a build step
- Click on Configure -> Build Trigger -> Add Build Step. Select `Invoke Red Hat Dependency Analytics (RHDA)`.
- Filepath (Mandatory): Provide the filepath for the manifest file. We currently support the following
	- Maven: pom.xml
	- Python: requirements.txt
	- Npm: package.json
	- Golang: go.mod
- Usage Statistics (Optional): Consent given to red hat to collect some usage statistics to improve the plugin and report. Default consent is false.
![](./images/configOption1.png)

  **NOTE:** If you get a Java runtime error because the build can not find the `mvn` binary, try doing the following steps:
  1. From the Jenkins Dashboard, click **Manage Jenkins** -> **Tools** -> **Maven Installations** -> click **Add Maven** -> Enter a name on **Maven Name** -> check **Install automatically** -> click **Save**
  2. From the Jenkins Dashboard, click **Manage Jenkins** -> **System** -> **Check Environment Variables**, click **Add**.
  3. Enter _EXHORT_MVN_PATH_ as the variable name, with the value pointing to the `mvn` binary from the Maven Integration installation.
     For example, a value of `$JENKINS_HOME/tools/hudson.tasks.Maven_MavenInstallation/<Maven Name from Step 1>/bin/mvn`.
  4. Include **Invoke top-level maven targets** as a build step by specifying the Maven version, and add **clean install** as a goal for the new pipeline item.
  
#### Option II- As a pipeline task
- Its just a single line that you need to add in your pipeline script.
`rhdaAnalysis file:'manifest file path', consentTelemetry:true`
The value description remains the same as provided in the Option I.
User can also use the pipeline snippet generator to generate the command.
  ![](./images/pipeline.png)
#### Return Code From Plugin
- It returns 3 different exit status code
    - 0: SUCCESS - Analysis is successful and there were no vulnerabilities found with Severity that exceeded the highest severity Allowed  in the dependency stack.
    - 1: ERROR -   Analysis encountered an error.
    - 2: VULNERABLE - Analysis is successful, but it found 1 or more vulnerabilities that Their Severity Exceeds the Highest Severity Allowed in the dependency stack.

## Results
There are a total 3 ways to view the results of the analysis.
### 1. Console Output
This provides the count and types of vulnerabilities found in the dependency stack. This data is generated for every build and can be viewed in the corresponding console log. It also provides a link to the detailed report.
![](./images/consoleOutput.png)

### 2. RHDA Stack Report
After every successful analysis, you can find a new icon added in the left panel named
`RHDA Stack Report` . Click on this icon to view the report in graphical form. Here too, we provide a button to redirect to the detailed stack report UI.
![](./images/stackReport.png)

### 3. Detailed RHDA Stack Report
The stack report can be accessed via 2 ways, as mentioned in point number 1 (via url) and 2 (via button click). The report provides comprehensive details about each vulnerability, each dependency in the stack along with the license analysis and the recommended companions.
![](./images/detailedStackReport.png)

## Snyk Registration

### Via SNYK website

https://docs.snyk.io/getting-started/quickstart/create-a-snyk-account
