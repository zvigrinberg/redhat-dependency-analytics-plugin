package redhat.jenkins.plugins.crda.client;

import com.redhat.exhort.api.AnalysisReport;

public class DepAnalysisDTO {

        AnalysisReport report;
        String html;

        public DepAnalysisDTO(AnalysisReport report, String html) {
                this.report = report;
                this.html = html;
        }

        public AnalysisReport getReport() {
                return report;
        }

        public void setReport(AnalysisReport report) {
                this.report = report;
        }

        public String getHtml() {
                return html;
        }

        public void setHtml(String html) {
                this.html = html;
        }

}
