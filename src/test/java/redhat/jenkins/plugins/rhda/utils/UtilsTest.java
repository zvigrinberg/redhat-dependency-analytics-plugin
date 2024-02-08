package redhat.jenkins.plugins.rhda.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.exhort.api.AnalysisReport;
import com.redhat.exhort.api.Severity;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class UtilsTest extends BaseTest {

	@Test
	public void testUtilsFunctions() {
		System.setProperty("os.name", "Linux");
		assertTrue(Utils.isLinux());
		assertFalse(Utils.isWindows());
		assertFalse(Utils.isMac());

		System.setProperty("sun.arch.data.model", "64");
		assertTrue(Utils.is64());
		assertFalse(Utils.is32());

		String validJson = "{ 'a_b': 10}";
		assertTrue(Utils.isJSONValid(validJson));

		String invalidJson = "abcdefgh";
		assertFalse(Utils.isJSONValid(invalidJson));
	}

	@Test
	public void testGetAllHighestSeveritiesFromResponse() throws JsonProcessingException, ExecutionException, InterruptedException {
		String exhortResponse = this.getStringFromFile("exhort_responses", "exhort_response.json");
		ObjectMapper om = new ObjectMapper();
		AnalysisReport exhortResponseObject = om.readValue(exhortResponse, AnalysisReport.class);
		Set<Severity> allHighestSeveritiesFromResponse = Utils.getAllHighestSeveritiesFromResponse(exhortResponseObject);
		allHighestSeveritiesFromResponse.forEach( severity -> System.out.println(severity.toString()));
		assertEquals(allHighestSeveritiesFromResponse,Set.of(Severity.CRITICAL,Severity.HIGH,Severity.MEDIUM));
	}

	@Test
	public void testIsHighestVulnerabilityAllowedExceeded() {
		assertTrue(Utils.isHighestVulnerabilityAllowedExceeded(Set.of(Severity.CRITICAL,Severity.HIGH,Severity.MEDIUM),Severity.HIGH));
		assertTrue(Utils.isHighestVulnerabilityAllowedExceeded(Set.of(Severity.CRITICAL,Severity.HIGH,Severity.MEDIUM),Severity.MEDIUM));
		assertFalse(Utils.isHighestVulnerabilityAllowedExceeded(Set.of(Severity.HIGH,Severity.MEDIUM,Severity.LOW),Severity.HIGH));
		assertFalse(Utils.isHighestVulnerabilityAllowedExceeded(Set.of(Severity.LOW),Severity.MEDIUM));
		assertFalse(Utils.isHighestVulnerabilityAllowedExceeded(Set.of(Severity.LOW,Severity.MEDIUM),Severity.MEDIUM));
		assertFalse(Utils.isHighestVulnerabilityAllowedExceeded(Set.of(),Severity.LOW));
		assertFalse(Utils.isHighestVulnerabilityAllowedExceeded(Set.of(Severity.LOW),Severity.LOW));
		assertTrue(Utils.isHighestVulnerabilityAllowedExceeded(Set.of(Severity.MEDIUM),Severity.LOW));
		assertFalse(Utils.isHighestVulnerabilityAllowedExceeded(Set.of(Severity.CRITICAL,Severity.HIGH,Severity.MEDIUM),Severity.CRITICAL));
		assertTrue(Utils.isHighestVulnerabilityAllowedExceeded(Set.of(Severity.CRITICAL,Severity.HIGH,Severity.MEDIUM),Severity.HIGH));
	}
// Test together both methods
	@Test
	public void testGetAllHighestSeveritiesFromResponseAndTestIsHighestVulnerabilityAllowedExceeded() throws JsonProcessingException, ExecutionException, InterruptedException {
		String exhortResponse = this.getStringFromFile("exhort_responses", "exhort_response.json");
		ObjectMapper om = new ObjectMapper();
		AnalysisReport exhortResponseObject = om.readValue(exhortResponse, AnalysisReport.class);
		Set<Severity> allHighestSeveritiesFromResponse = Utils.getAllHighestSeveritiesFromResponse(exhortResponseObject);
		assertTrue(Utils.isHighestVulnerabilityAllowedExceeded(allHighestSeveritiesFromResponse,Severity.HIGH));
		assertTrue(Utils.isHighestVulnerabilityAllowedExceeded(allHighestSeveritiesFromResponse,Severity.MEDIUM));
		assertTrue(Utils.isHighestVulnerabilityAllowedExceeded(allHighestSeveritiesFromResponse,Severity.LOW));
		assertFalse(Utils.isHighestVulnerabilityAllowedExceeded(allHighestSeveritiesFromResponse,Severity.CRITICAL));
	}
}
