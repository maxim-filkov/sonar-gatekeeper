package com.tobetester.sonar;

import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.Base64;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;

@Mojo(name = "assign")
public class AssignGoal extends AbstractMojo {

   @Parameter(property = "qualityGate", required = true)
   private String qualityGate;

   @Parameter(property = "branch", required = true)
   private String branch;

   @Parameter(property = "stashKey", required = true)
   private String stashKey;

   @Parameter(property = "sonarLogin", defaultValue = "admin")
   private String sonarLogin;

   @Parameter(property = "sonarPassword", defaultValue = "admin")
   private String sonarPassword;

   @Parameter(property = "sonarUrl")
   private String sonarUrl;

   private String artifactId;

   private String groupId;

   public void execute() {
      artifactId = artifactId();
      groupId = groupId();
      sonarUrl = sonarUrl.replaceFirst("/*$", "");

      getLog().info(String.format("qualityGate: '%s'; branch: '%s'; stashKey: '%s'; sonarUrl: '%s', artifactId: '%s'; groupId: '%s'",
         qualityGate, branch, stashKey, sonarUrl, artifactId, groupId));

      getLog().info("Started analysis of Sonar report");
      try {
         int sonarProjectId = sonarProjectId();
         if (sonarProjectId == 0) {
            sonarProjectId = createSonarProject();
         }
         assignSonarProjectToQualityGate(sonarProjectId, sonarQualityGateId());
      } catch (final Exception e) {
         getLog().error(String.format("Sonar analysis failed: %s", e.getMessage()));
         System.exit(1);
      }
   }

   private int createSonarProject() throws Exception {
      getLog().info("Creating Sonar project");
      final String name = String.format("%s%%20%s-%s", artifactId, stashKey, branch);
      final String key = String.format("%s:%s", groupId(), artifactId);
      final String path = String.format("/api/projects/create?name=%s&key=%s&branch=%s-%s", name, key, stashKey, branch);
      final String responseBody = postToSonar(path);
      getLog().info(String.format("Response from Sonar:\n%s", responseBody));
      final String projectId = JsonPath.read(responseBody, "$.id");
      return Integer.valueOf(projectId);
   }

   /**
    * Assigns Sonar project to quality gate.
    *
    * @param projectId Project identifier.
    * @param gateId    Quality gate name, e.g. "AdTechQG".
    * @throws IOException If impossible to perform the assignment.
    */
   private void assignSonarProjectToQualityGate(final Integer projectId, final Integer gateId) throws Exception {
      final String path = String.format("/api/qualitygates/select?gateId=%s&projectId=%s", gateId, projectId);
      getLog().info(String.format("Assigning project id '%s' to quality gate '%s'", projectId, gateId));
      postToSonar(path);
   }

   /**
    * Extracts Maven artifact identifier from the project pom.xml file.
    *
    * @return Extracted artifact identifier.
    */
   private String artifactId() {
      getLog().info("Getting artifactId for current project");
      final Map<String, Object> pluginContext = getPluginContext();
      for (final Map.Entry entry : pluginContext.entrySet()) {
         if (entry.getValue() instanceof MavenProject) {
            final MavenProject mavenProject = ((MavenProject) entry.getValue());
            getLog().info(String.format("Found artifactId: '%s'", mavenProject.getArtifactId()));
            return mavenProject.getArtifactId();
         }
      }
      return null;
   }

   private String groupId() {
      getLog().info("Getting groupId for current project");
      final Map<String, Object> pluginContext = getPluginContext();
      for (final Map.Entry entry : pluginContext.entrySet()) {
         if (entry.getValue() instanceof MavenProject) {
            final MavenProject mavenProject = ((MavenProject) entry.getValue());
            getLog().info(String.format("Found groupId: '%s'", mavenProject.getGroupId()));
            return mavenProject.getGroupId();
         }
      }
      return null;
   }

   /**
    * Returns Sonar project identifier found for the given project Maven artifact identifier.
    *
    * @return Found Sonar project identifier.
    * @throws IOException If impossible to search or request Sonar by the constructed URL.
    */
   private Integer sonarProjectId() throws IOException {
      final String query = String.format("%s:%s:%s-%s", groupId, artifactId, stashKey, branch);
      final URL url = new URL(sonarUrl + "/api/projects/index?key=" + query);
      getLog().info(String.format("Getting project id from Sonar: '%s'", url));
      final String json = IOUtils.toString(url, Charset.forName("UTF-8"));
      getLog().info(String.format("Response from Sonar:\n%s", json));
      final JSONArray matches = JsonPath.read(json, "$.[?(@.k == '" + query + "')].id");
      if (matches.size() > 0) {
         getLog().info(String.format("Sonar project id: %s", matches.get(0)));
         return Integer.valueOf((String) matches.get(0));
      } else {
         getLog().warn("Sonar project id not found");
         return 0;
      }
   }

   /**
    * Finds and returns Sonar quality gate identifier for the given (in configuration for this plugin) quality gate.
    *
    * @return Found Sonar quality gate identifier.
    * @throws IOException If impossible to get list of quality gates from Sonar by te constructed URL.
    */
   private Integer sonarQualityGateId() throws IOException {
      final URL url = new URL(sonarUrl + "/api/qualitygates/list");
      getLog().info(String.format("Getting quality gate id from Sonar: '%s'", url));
      final String json = IOUtils.toString(url, Charset.forName("UTF-8"));
      getLog().info(String.format("Response from Sonar:\n%s", json));
      final JSONArray matches = JsonPath.read(json, "$.qualitygates[?(@.name == '" + qualityGate + "')].id");
      getLog().info(String.format("Sonar quality gate id: %s", matches.size() > 0 ? matches.get(0) : "not found"));
      return (Integer) matches.get(0);
   }

   private String postToSonar(final String path) throws Exception {
      final URL url = new URL(sonarUrl + path);
      getLog().info(String.format("Sending POST request to Sonar: '%s'", url));
      final HttpPost request = new HttpPost(url.toURI());
      final String auth = sonarLogin + ":" + sonarPassword;
      final byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("ISO-8859-1")));
      final String authHeader = "Basic " + new String(encodedAuth);
      request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
      final HttpClient client = HttpClientBuilder.create().build();
      final HttpResponse response = client.execute(request);
      final int responseCode = response.getStatusLine().getStatusCode();
      final String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";
      getLog().info(String.format("Received response code is '%s' and body '%s'", responseCode, responseBody));
      if (responseCode < 200 || responseCode >= 300) {
         getLog().error("Error has occurred when posting to Sonar!");
         System.exit(1);
      }
      return responseBody;
   }

}
