/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.tools.examples;

import com.petebevin.markdown.MarkdownProcessor;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import static org.apache.openejb.tools.examples.FileHelper.listFilesEndingWith;
import static org.apache.openejb.tools.examples.FileHelper.listFolders;
import static org.apache.openejb.tools.examples.FileHelper.mkdirp;
import static org.apache.openejb.tools.examples.ListBuilder.newList;
import static org.apache.openejb.tools.examples.MapBuilder.newMap;
import static org.apache.openejb.tools.examples.OpenEJBTemplate.USER_JAVASCRIPTS;
import static org.apache.openejb.tools.examples.ViewHelper.getAggregateClasses;
import static org.apache.openejb.tools.examples.ViewHelper.getAndUpdateApis;
import static org.apache.openejb.tools.examples.ViewHelper.getClassesByApi;
import static org.apache.openejb.tools.examples.ViewHelper.getExamplesClassesByApi;
import static org.apache.openejb.tools.examples.ViewHelper.getLink;
import static org.apache.openejb.tools.examples.ViewHelper.startFromPrefix;
import static org.apache.openejb.tools.examples.ZipHelper.extract;
import static org.apache.openejb.tools.examples.ZipHelper.zipDirectory;

/**
 * Most the examples do not have any documentation.
 * <p/>
 * There are some wiki pages for some of the examples, but these are hard to create and maintain.  The examples change frequently enough that we really should have documentation that goes with each version of the examples.
 * <p/>
 * If we put a README.md file in each example and use Markdown which is a really simple text format that has many tools capable of generating html, we could probably generate a web page for each example.  Then we could generate a index for all the examples.
 * <p/>
 * We could then take this all and upload it to the website
 * <p/>
 * Something kind of like this, but nicer looking, with breadcrumbs and links for navigating around to other examples: http://people.apache.org/~dblevins/simple-stateless.html
 * <p/>
 * <p/>
 * <p/>
 * <p/>
 * IDEAS FOR AFTER SOMETHING IS WORKING
 * <p/>
 * Perhaps at some point some xref style processing of the source and links to the source
 * <p/>
 * Perhaps at some point use ASM to see what annotations and API classes were used and make an index that lists examples by which APIs are used
 * <p/>
 * Perhaps at some point use Swizzle stream to do a sort of SNIPPET thing like the Confluence plugin, so we wouldn't have to copy source into the example
 *
 * @version $Rev$ $Date$
 */
public class GenerateIndex {
    private static final Logger LOGGER = Logger.getLogger(GenerateIndex.class);
    private static final MarkdownProcessor PROCESSOR = new MarkdownProcessor();

    /**
     * Can be run in an IDE or via Maven like so:
     * <p/>
     * mvn clean install exec:java -Dexec.mainClass=org.apache.openejb.tools.examples.GenerateIndex
     *
     * @param examplesZip examples zip location
     * @param workFolder work folder
     */
    public static void generate(String examplesZip, String workFolder) {
        Properties properties = ExamplesPropertiesManager.get();

        // working folder
        File extractedDir = new File(workFolder, properties.getProperty("extracted"));
        File generatedDir = new File(workFolder, properties.getProperty("generated"));

        // crack open the examples zip file
        extract(examplesZip, extractedDir.getPath());

        // generate index.html by example
        Map<String, Set<String>> exampleLinksByKeyword = new TreeMap<String, Set<String>>();
        Map<String, String> nameByLink = new TreeMap<String, String>();
        Map<String, String> zipLinks = new TreeMap<String, String>();
        Collection<File> examples = listFolders(extractedDir, properties.getProperty("pom"));
        for (File example : examples) {
            // create a directory for each example
            File generated = new File(generatedDir, example.getPath().replace(extractedDir.getPath(), ""));
            mkdirp(generated);

            File readme = new File(example, properties.getProperty("readme"));
            String html = "";
            if (readme.exists()) {
                try {
                    html = PROCESSOR.markdown(FileUtils.readFileToString(readme));
                } catch (IOException e) {
                    LOGGER.warn("can't read readme file for example " + example.getName());
                }
            }

            File index = new File(generated, properties.getProperty("index"));
            String link = getLink(generatedDir, index);
            nameByLink.put(link, example.getName());

            File zip = new File(generated, example.getName() + ".zip");
            String zipLink = getLink(generatedDir, zip);
            zipLinks.put(link, zipLink);

            try {
                zipDirectory(example, zip, example.getParent());
            } catch (IOException e) {
                LOGGER.error("can't zip example " + example.getName(), e);
            }

            List<File> javaFiles = listFilesEndingWith(example, ".java");
            Map<String, Integer> apiCount = getAndUpdateApis(javaFiles, exampleLinksByKeyword, generatedDir, index);
            for (File java : javaFiles) {
                String code;
                try {
                    code = FileUtils.readFileToString(java);
                } catch (IOException e) {
                    LOGGER.error("can't read source " + java.getPath(), e);
                    continue;
                }

                String source = getLink(example, java);
                File sourceFile = new File(generated, source);
                mkdirp(sourceFile.getParentFile());

                tpl(properties.getProperty("template.code"),
                    newMap(String.class, Object.class)
                        .add("title", source + " source")
                        .add(OpenEJBTemplate.USER_JAVASCRIPTS, newList(String.class).add("prettyprint.js").list())
                        .add("file", source)
                        .add("code", code)
                        .map(),
                    sourceFile.getPath() + ".html");
            }

            if (html.isEmpty()) {
                LOGGER.warn("no readme for example " + example.getName() + " [" + example.getPath() + "]");

                tpl(properties.getProperty("template.default"),
                    newMap(String.class, Object.class)
                        .add("title", example.getName() + " example")
                        .add(OpenEJBTemplate.USER_JAVASCRIPTS, newList(String.class).add("prettyprint.js").list())
                        .add("apis", apiCount)
                        .add("link", zip.getName())
                        .add("files", startFromPrefix("src/", javaFiles))
                        .map(),
                    index.getPath());
            } else {
                tpl(properties.getProperty("template.external"),
                    newMap(String.class, Object.class)
                        .add("title", example.getName() + " example")
                        .add(OpenEJBTemplate.USER_JAVASCRIPTS, newList(String.class).add("prettyprint.js").list())
                        .add("content", html)
                        .map(),
                    index.getPath());
            }
        }

        Map<String, String> classesByApi = getClassesByApi(exampleLinksByKeyword, '.', ViewHelper.REPLACED_CHAR); // css class(es) for aggregates
        Map<String, String> examplesClassesByApi = getExamplesClassesByApi(exampleLinksByKeyword); // css class(es) for buttons
        Map<String, String> aggregatedClasses = getAggregateClasses(new ArrayList<String>(nameByLink.keySet()), exampleLinksByKeyword);

        // create a glossary page (OR search)
        tpl(properties.getProperty("template.glossary"),
            newMap(String.class, Object.class)
                .add("title", "OpenEJB Example Glossary")
                .add(USER_JAVASCRIPTS, newList(String.class).add("glossary.js").list())
                .add("links", nameByLink)
                .add("zipLinks", zipLinks)
                .add("examples", nameByLink)
                .add("classes", classesByApi)
                .add("exampleByKeyword", exampleLinksByKeyword)
                .add("aggregatedClasses", aggregatedClasses)
                .map(),
            new File(generatedDir, properties.getProperty("glossary")).getPath());

        // create an index for all example directories
        tpl(properties.getProperty("template.main"),
            newMap(String.class, Object.class)
                .add("title", "OpenEJB Example")
                .add(USER_JAVASCRIPTS, newList(String.class).add("index.js").list())
                .add("zipLinks", zipLinks)
                .add("examples", nameByLink)
                .add("classes", classesByApi)
                .add("examplesClasses", examplesClassesByApi)
                .add("aggregatedClasses", aggregatedClasses)
                .map(),
            new File(generatedDir, properties.getProperty("index")).getPath());
    }

    // just a shortcut
    private static void tpl(String template, Map<String, Object> mapContext, String path) {
        OpenEJBTemplate.get().apply(template,
            newMap(mapContext)
                .add("base", ExamplesPropertiesManager.get().getProperty("home.resources"))
                .add("home", ExamplesPropertiesManager.get().getProperty("home.site"))
                .map(),
            path);
    }
}
