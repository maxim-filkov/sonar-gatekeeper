package com.tobetester.sonar;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import net.minidev.json.JSONArray;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Properties;

@Mojo(name = "analyse")
public class AnalyseGoal extends AbstractMojo {

   @Parameter(property = "sonarUrl")
   private String sonarUrl;

   public void execute() {
      sonarUrl = sonarUrl.replaceFirst("/*$", "");
      getLog().info("Started analysis of Sonar report");
      try {
         final String errorReport = analyse();
         if (!errorReport.isEmpty()) {
            getLog().error("Fail! Sonar analysis report contains errors!\n" + new JSONObject(errorReport).toString(4));
            System.exit(1);
         }
         getLog().info("Success! Received Sonar analysis report contains no errors");
      } catch (final Exception e) {
         getLog().error(String.format("Sonar analysis failed: %s", e.getMessage()));
         System.exit(1);
      }
   }

   /**
    * Downloads Sonar analysis results and searches for errors in it.
    *
    * @return Error report in JSON format if some errors were found, otherwise returns empty string.
    * @throws Exception In case analysis is failed.
    */
   private String analyse() throws Exception {
      final String analysisId = sonarAnalysisId();
      final URL url = new URL(sonarUrl + "/api/qualitygates/project_status?analysisId=" + analysisId);
      getLog().info(String.format("Getting Sonar analysis results from '%s'", url));
      final String json = IOUtils.toString(url, Charset.forName("UTF-8"));
      final JSONArray errors = JsonPath.read(json, "$.projectStatus.conditions[?(@.status == 'ERROR')]");
      return errors.size() > 0 ? json : "";
   }

   /**
    * Finds and returns Sonar analysis identifier received by ceTaskUrl that is extracted from report-task.txt. The
    * report text file is generated after sonar:sonar goal execution.
    *
    * @return Extracted Sonar analysis identifier.
    * @throws Exception In case it is impossible to get Sonar analysis identifier.
    */
   private String sonarAnalysisId() throws Exception {
      final Properties report = new Properties();
      getLog().info(String.format("Getting ceTaskUrl from '%s'", "target/sonar/report-task.txt"));
      report.load(new FileInputStream("target/sonar/report-task.txt"));
      getLog().info(String.format("ceTaskUrl is '%s'", report.getProperty("ceTaskUrl")));
      final URL url = new URL(report.getProperty("ceTaskUrl"));
      getLog().info(String.format("Getting id of Sonar analysis from '%s'", url));
      String id = "";
      int maxAttempts = 30;
      while (id.isEmpty() && --maxAttempts > 0) {
         try {
            final String json = IOUtils.toString(url, Charset.forName("UTF-8"));
            id = JsonPath.read(json, "$.task.analysisId");
            getLog().info(String.format("Sonar analysis id is '%s'", id));
         } catch (final PathNotFoundException e) {
            getLog().info("Sonar analysis is still in progress, wait more...");
            Thread.sleep(18000);
         }
      }
      if (maxAttempts == 0) {
         getLog().error("Couldn't get Sonar analysis results within 3 minutes. Sonar is down?");
         System.exit(1);
      }
      return id;
   }

}
