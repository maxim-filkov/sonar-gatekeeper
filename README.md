# Sonar Quality Gate plugin

This maven plugin is intended for getting Sonar analysis information for projects and breaking maven build in case Sonar detected some issues.

## Usage

In your maven project you have to modify pom.xml file with adding the following section:

```
<project>
  ...
  <build>
    <plugins>
      <build>
        <plugins>
          <plugin>
            <groupId>com.tobetester.sonar</groupId>
            <artifactId>sonar-gatekeeper-maven-plugin</artifactId>
            <version>1.0.0</version>
            <configuration>
              <qualityGate>MyCustomQualityGate</qualityGate>
              <branch>feature/my_feature</branch>
              <stashKey>KEY</stashKey>
            </configuration>
          </plugin>
      </plugins>
  </build>
  ...
</project>
```

Please note that the configuration node in the example above is optional. Below is given an example oh how to define the same parameters from command line.

```
mvn sonar-gatekeeper:analyse \
    -DqualityGate='MyCustomQualityGate' \
    -Dbranch='feature/my_feature' \
    -DstashKey='KEY'
```

There **qualityGate** is the name of Sonar Quality gate (see https://docs.sonarqube.org/display/SONAR/Quality+Gates).
The property **branch** is the branch name which you want Sonar to analyse.
The property **stashKey** means Stash key for your team, e.g. 'KEY'.