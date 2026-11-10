<#-- The template of THIRD-PARTY.txt, filled in by the license-maven-plugin with the components
     the distribution ships in lib/, grouped by the licences they name. -->
Third-party components of markdown-ls

The distribution ships the components below in lib/, each in its unmodified JAR
and under the licence named above it. licenses/ holds the text of each of these
licences. notices/ holds the licence and notice files that name the copyright
holders of the components: those each JAR carries, in a directory named after
the JAR, and those of flexmark-java, autolink-java, flexmark-extensions,
markdown-core and markdown-service, whose JARs carry none, in a directory named
after the project.

jlatexmath and its two font JARs are under the GNU General Public License 2.0
or later with the Classpath exception. The exception permits linking them with
markdown-ls and distributing the result under the licence of markdown-ls, the
BSD 3-Clause License. Its text is in notices/jlatexmath-*/META-INF/LICENSE.

The components under the GNU General Public License, jlatexmath, its two font
JARs and PlantUML, come with their source code: sources/ holds the JAR with
the source of each of them.

The source code of the components under the Eclipse Public License is available
from Maven Central, as the JAR with the classifier "sources" of the coordinates
named below, and from the site of each project.

bom.json lists the same components and their licences as a CycloneDX software
bill of materials.

<#list licenseMap as e>
<#assign license = e.getKey()/>
<#assign projects = e.getValue()/>
${license}:
<#list projects as p>
  - ${p.name} (${p.groupId}:${p.artifactId}:${p.version}<#if p.url??>, ${p.url}</#if>)
</#list>

</#list>
