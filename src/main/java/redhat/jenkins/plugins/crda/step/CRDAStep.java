/* Copyright © 2021 Red Hat Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# Author: Yusuf Zainee <yzainee@redhat.com>
*/

package redhat.jenkins.plugins.crda.step;

import com.redhat.exhort.api.AnalysisReport;
import com.redhat.exhort.api.DependenciesSummary;
import com.redhat.exhort.api.VulnerabilitiesSummary;
import com.redhat.exhort.impl.ExhortApi;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import redhat.jenkins.plugins.crda.action.CRDAAction;
import redhat.jenkins.plugins.crda.task.CRDABuilder.BuilderDescriptorImpl;
import redhat.jenkins.plugins.crda.utils.Config;
import redhat.jenkins.plugins.crda.utils.Utils;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class CRDAStep extends Step {
    private String file;
    private String crdaKeyId;
    private String cliVersion;
    private boolean consentTelemetry = false;

    @DataBoundConstructor
    public CRDAStep(String file, String crdaKeyId, String cliVersion, boolean consentTelemetry) {
        this.file = file;
        this.crdaKeyId = crdaKeyId;
        this.cliVersion = cliVersion;
        this.consentTelemetry = consentTelemetry;
    }

    public String getFile() {
        return file;
    }

    @DataBoundSetter
    public void setFile(String file) {
        this.file = file;
    }
    
    public String getCliVersion() {
        return cliVersion;
    }

    @DataBoundSetter
    public void setCliVersion(String cliVersion) {
        this.cliVersion = cliVersion;
    }

    public String getCrdaKeyId() {
        return crdaKeyId;
    }

    @DataBoundSetter
    public void setCrdaKeyId(String crdaKeyId) {
        this.crdaKeyId = crdaKeyId;
    }
    
    public boolean getConsentTelemetry() {
        return consentTelemetry;
    }

    @DataBoundSetter
    public void setConsentTelemetry(boolean consentTelemetry) {
        this.consentTelemetry = consentTelemetry;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    public static class Execution extends SynchronousNonBlockingStepExecution<String> {

        private transient final CRDAStep step;
        private String jenkinsPath;

        protected Execution(CRDAStep step, StepContext context) {
            super(context);
            this.step = step;
            try {
            	EnvVars envVars = context.get(EnvVars.class);
            	jenkinsPath = envVars.get("PATH");
                // setting system properties to pass to java-api
                if(envVars.get("CRDA_MVN_PATH") != null ){
                    System.setProperty("CRDA_MVN_PATH", envVars.get("CRDA_MVN_PATH"));
                }

            } catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
        }

        @Override
        protected String run() throws Exception {

            PrintStream logger = getContext().get(TaskListener.class).getLogger();
            logger.println("Analysis Begins");
            String crdaUuid = "";
            Run run = getContext().get(Run.class);
            TaskListener listener = getContext().get(TaskListener.class);
            FilePath workspace = getContext().get(FilePath.class);

            crdaUuid = Utils.getCRDACredential(step.crdaKeyId);
            if (crdaUuid == null) {
                logger.println("RHDA Key id '" + step.crdaKeyId + "' was not found in the credentials. Please configure the build properly and retry.");
                return Config.EXIT_FAILED;
            }

            if(crdaUuid.equals("")) {
                logger.println("RHDA Key id '" + step.crdaKeyId + "' was not found in the credentials. Please configure the build properly and retry.");
                return Config.EXIT_FAILED;
            }

            System.setProperty("EXHORT_SNYK_TOKEN", crdaUuid);
            System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "");

            // to get build directory
            // run.getRootDir().getPath();
            String manifestPath = step.getFile();
            if (manifestPath == null) {
                logger.println("Filepath for the manifest file not provided. Please configure the build properly and retry.");
                return Config.EXIT_FAILED;
            }

            // instantiate the Crda API implementation
            var exhortApi = new ExhortApi();
            // TODO: Enable for the SP.
//            CompletableFuture<Api.MixedReport> mixedStackReport = exhortApi.stackAnalysisMixed(manifestPath.toString());

            // get a byte array future holding a html report
            CompletableFuture<byte[]> htmlReport = exhortApi.stackAnalysisHtml(manifestPath.toString());

            // get a AnalysisReport future holding a deserialized report
            CompletableFuture<AnalysisReport> analysisReport = exhortApi.stackAnalysis(manifestPath.toString());
//
            try {

                processReport(analysisReport.get(), listener);
                saveHtmlReport(htmlReport.get(), listener, workspace);
                logger.println("Click on the RHDA Stack Report icon to view the detailed report");
                logger.println("----- RHDA Analysis Ends -----");
                run.addAction(new CRDAAction(crdaUuid, analysisReport.get(), workspace + "/dependency-analytics-report.html"));
                return (analysisReport.get().getSummary().getVulnerabilities().getTotal()).intValue() == 0 ? Config.EXIT_SUCCESS : Config.EXIT_VULNERABLE;
//              // TODO: Enable for the SP.
//                run.addAction(new CRDAAction(crdaUuid, mixedStackReport.get().json, workspace + "/dependency-analysis-report.html"));
//                return mixedStackReport.get().json.getSummary().getVulnerabilities().getTotal().compareTo(BigDecimal.ZERO) == 0 ? Config.EXIT_SUCCESS : Config.EXIT_VULNERABLE;
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            return Config.EXIT_VULNERABLE;
        }

        private void processReport(AnalysisReport report, TaskListener listener) throws ExecutionException, InterruptedException {
            PrintStream logger = listener.getLogger();
            DependenciesSummary dependenciesSummary = report.getSummary().getDependencies();
            VulnerabilitiesSummary vulnerabilitiesSummary = report.getSummary().getVulnerabilities();
            logger.println("Summary");
            logger.println("  Dependencies");
            logger.println("    Scanned dependencies:    " + dependenciesSummary.getScanned());
            logger.println("    Transitive dependencies: " + dependenciesSummary.getTransitive());
            logger.println("  Vulnerabilities");
            logger.println("    Total: " + vulnerabilitiesSummary.getTotal());
            logger.println("    Direct: " + vulnerabilitiesSummary.getDirect());
            logger.println("    Critical: " + vulnerabilitiesSummary.getCritical());
            logger.println("    High: " + vulnerabilitiesSummary.getHigh());
            logger.println("    Medium: " + vulnerabilitiesSummary.getMedium());
            logger.println("    Low: " + vulnerabilitiesSummary.getLow());
            logger.println("");
        }

        private void saveHtmlReport(byte[] html, TaskListener listener, FilePath workspace) throws IOException, InterruptedException {
            PrintStream logger = listener.getLogger();
            File file = new File(workspace + "/dependency-analytics-report.html");
            FileUtils.writeByteArrayToFile(file, html);
            logger.println("You can find the detailed HTML report in your workspace.");
        }

        private static final long serialVersionUID = 1L;
    }


    @Extension
    @Symbol("crdaAnalysis")
    public static class DescriptorImpl extends StepDescriptor {

    	private final BuilderDescriptorImpl builderDescriptor;

        public DescriptorImpl() {
          builderDescriptor = new BuilderDescriptorImpl();
        }
        
        @SuppressWarnings("unused")
        public ListBoxModel doFillCrdaKeyIdItems(@AncestorInPath Item item, @QueryParameter String crdaKeyId) {
          return builderDescriptor.doFillCrdaKeyIdItems(item, crdaKeyId);
        }
        
        @SuppressWarnings("unused")
        public FormValidation doCheckCrdaKeyId(@QueryParameter String crdaKeyId) throws IOException, ServletException {
          return builderDescriptor.doCheckCrdaKeyId(crdaKeyId);
        }
        
        @SuppressWarnings("unused")
        public FormValidation doCheckFile(@QueryParameter String file) throws IOException, ServletException {
          return builderDescriptor.doCheckFile(file);
        }
    	
    	@Override
        public String getFunctionName() {
            return "crdaAnalysis";
        }

        @Override
        public String getDisplayName() {
            return "Test Invoke Red Hat Dependency Analytics (RHDA)";
        }

        @Override
        public Set< Class<?>> getRequiredContext() {
            return Collections.unmodifiableSet(new HashSet<Class<?>>(Arrays.asList(FilePath.class, Run.class, TaskListener.class)));
        }
    }
}
