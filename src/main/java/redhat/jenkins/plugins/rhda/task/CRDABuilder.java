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

package redhat.jenkins.plugins.rhda.task;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.redhat.exhort.Api;
import com.redhat.exhort.api.AnalysisReport;
import com.redhat.exhort.api.DependenciesSummary;
import com.redhat.exhort.api.ProviderStatus;
import com.redhat.exhort.api.VulnerabilitiesSummary;
import com.redhat.exhort.impl.ExhortApi;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import redhat.jenkins.plugins.rhda.action.CRDAAction;
import redhat.jenkins.plugins.rhda.credentials.CRDAKey;
import redhat.jenkins.plugins.rhda.utils.Utils;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


public class CRDABuilder extends Builder implements SimpleBuildStep, Serializable {

    private String file;
    private String crdaKeyId;
    private boolean consentTelemetry = false;

    @DataBoundConstructor
    public CRDABuilder(String file, String crdaKeyId, boolean consentTelemetry) {
        this.file = file;
        this.crdaKeyId = crdaKeyId;
        this.consentTelemetry = consentTelemetry;
    }

    public String getFile() {
        return file;
    }

    @DataBoundSetter
    public void setFile(String file) {
        this.file = file;
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
    public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        logger.println("----- RHDA Analysis Begins -----");
        String crdaUuid = Utils.getCRDACredential(this.getCrdaKeyId());

        EnvVars envVars = getEnvVars(run, listener);
        if(envVars != null){
            // setting system properties to pass to java-api
            if(envVars.get("EXHORT_MVN_PATH") != null ){
                System.setProperty("EXHORT_MVN_PATH", envVars.get("EXHORT_MVN_PATH"));
            }
            else{
                System.clearProperty("EXHORT_MVN_PATH");
            }

            if(envVars.get("EXHORT_URL") != null ){
                System.setProperty("EXHORT_URL", envVars.get("EXHORT_URL"));
            }
            else{
                System.clearProperty("EXHORT_URL");
            }

            if(envVars.get("EXHORT_SNYK_TOKEN") != null ){
                System.setProperty("EXHORT_SNYK_TOKEN", envVars.get("EXHORT_SNYK_TOKEN"));
            }
            else {
                System.clearProperty("EXHORT_SNYK_TOKEN");
            }
        }

        System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "");

        Path manifestPath = Paths.get(getFile());
        if (manifestPath.getParent() == null) {
            manifestPath = Paths.get(workspace.child(getFile()).toURI());
        }

        // instantiate the Exhort(crda) API implementation
        var exhortApi = new ExhortApi();

        // get a AnalysisReport future holding a mixed report object aggregating:
        // - (json) deserialized Stack Analysis report
        // - (html) html Stack Analysis report
        CompletableFuture<Api.MixedReport> mixedStackReport = exhortApi.stackAnalysisMixed(manifestPath.toString());

        try {
            processReport(mixedStackReport.get().json, listener);
            saveHtmlReport(mixedStackReport.get().html, listener, workspace);
            // Archiving the report
            ArtifactArchiver archiver = new ArtifactArchiver("dependency-analytics-report.html");
            archiver.perform(run, workspace, envVars, launcher, listener);
            logger.println("Click on the RHDA Stack Report icon to view the detailed report.");
            logger.println("----- RHDA Analysis Ends -----");
            run.addAction(new CRDAAction(crdaUuid, mixedStackReport.get().json, workspace + "/dependency-analysis-report.html", "freestyle"));
        } catch (ExecutionException e) {
            logger.println("error");
            e.printStackTrace(logger);
            e.printStackTrace();
        }
    }

    private EnvVars getEnvVars(Run<?,?> run, TaskListener listener) {
        if (run == null || listener == null) {
            return null;
        }

        try {
            return run.getEnvironment(listener);
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    @Extension
    public static final class BuilderDescriptorImpl extends BuildStepDescriptor<Builder> {

        public BuilderDescriptorImpl() {
            load();
        }

        public FormValidation doCheckFile(@QueryParameter String file) {
            if (file.length() == 0) {
                return FormValidation.error(Messages.CRDABuilder_DescriptorImpl_errors_missingFileName());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckCrdaKeyId(@QueryParameter String crdaKeyId)
                throws IOException, ServletException {
            int len = crdaKeyId.length();
            if (len == 0) {
                return FormValidation.error(Messages.CRDABuilder_DescriptorImpl_errors_missingUuid());
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("deprecation")
        public ListBoxModel doFillCrdaKeyIdItems(@AncestorInPath Item item, @QueryParameter String crdaKeyId) {
            StandardListBoxModel model = new StandardListBoxModel();
            if (item == null) {

                Jenkins jenkins = Jenkins.getInstance();
                if (!jenkins.hasPermission(Jenkins.ADMINISTER)) {
                    return model.includeCurrentValue(crdaKeyId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return model.includeCurrentValue(crdaKeyId);
                }
            }
            return model.includeEmptyValue()
                    .includeAs(ACL.SYSTEM, item, CRDAKey.class)
                    .includeCurrentValue(crdaKeyId);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.CRDABuilder_DescriptorImpl_DisplayName();
        }
    }

    private void processReport(AnalysisReport report, TaskListener listener) throws ExecutionException, InterruptedException {
        PrintStream logger = listener.getLogger();
        DependenciesSummary dependenciesSummary = report.getSummary().getDependencies();
        VulnerabilitiesSummary vulnerabilitiesSummary = report.getSummary().getVulnerabilities();
        for (ProviderStatus providerStatus : report.getSummary().getProviderStatuses()) {
            if(providerStatus.getStatus() != 200){
                logger.println("WARNING: " + providerStatus.getProvider() + ": " + providerStatus.getMessage());
            }
        }
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
        logger.println("You can find the latest detailed HTML report in your workspace and in your build under Build Artifacts.");
    }

}
