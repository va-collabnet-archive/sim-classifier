/**
 * Copyright (c) 2009 International Health Terminology Standards Development
 * Organisation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * Copyright CSIRO Australian e-Health Research Centre (http://aehrc.com).
 * All rights reserved. Use is subject to license terms and conditions.
 */

package au.csiro.snorocket;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import org.junit.Assert;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import au.csiro.ontology.IOntology;
import au.csiro.ontology.Node;
import au.csiro.ontology.classification.NullProgressMonitor;
import au.csiro.ontology.importer.rf1.RF1Importer;
import au.csiro.ontology.model.Concept;
import au.csiro.snorocket.core.CoreFactory;
import au.csiro.snorocket.core.IFactory;
import au.csiro.snorocket.core.NormalisedOntology;
import au.csiro.snorocket.protege.SnorocketOWLReasoner;
import au.csiro.snorocket.protege.util.DebugUtils;

public class TestRegression {
    final static String TEST_DIR = "src/test/resources/";

    /**
     * Tests the classification process using the 2011 version of SNOMED-CT and
     * the RF1 loader. The original SNOMED distribution files are used as input.
     */
    @Test
    public void testSnomed_20110731_RF1() {
        System.out.println("Running testSnomed_20110731_RF1");
        
        // Test the classification of the snomed_20110731 ontology        
        InputStream concepts = this.getClass().getResourceAsStream("/sct1_Concepts_Core_INT_20110731.txt");
        InputStream descriptions = this.getClass().getResourceAsStream("/sct1_Descriptions_en_INT_20110731.txt");
        InputStream relations = this.getClass().getResourceAsStream("/res1_StatedRelationships_Core_INT_20110731.txt");
        InputStream canonical = this.getClass().getResourceAsStream("/sct1_Relationships_Core_INT_20110731.txt");
        
        testRF1Ontology(concepts, descriptions, relations, canonical, "20110731");
    }

    /**
     * Small ontology test, mainly used to test the OWL test infrastructure.
     */
    @Test
    public void testSmall() {
        System.out.println("Running testSmall");
        File stated = new File(TEST_DIR + "small_stated.owl");
        File inferred = new File(TEST_DIR + "small_inferred.owl");
        testOntology(stated, inferred, false);
    }

    /**
     * Tests an anatomy ontology that uncovered a bug in the original Snorocket
     * implementation.
     */
    @Test
    public void testAnatomy2012() {
        System.out.println("Running testAnatomy2012");
        InputStream stated = this.getClass().getResourceAsStream("/anatomy_2012_stated.owl");
        InputStream inferred = this.getClass().getResourceAsStream("/anatomy_2012_inferred.owl");
        testOntology(stated, inferred, false);
    }

    /**
     * Tests the classification of the AMT v3 20120229 ontology by using the OWL
     * functional exported file and comparing the classification output with the
     * classification results using Hermit.
     */
    @Test
    public void testAMT_20120229() {
        System.out.println("Running testAMT_20120229");
        InputStream stated = this.getClass().getResourceAsStream("/amt_20120229_stated.owl");
        InputStream inferred = this.getClass().getResourceAsStream("/amt_20120229_classified.owl");
        testOntology(stated, inferred, false);
    }

    /**
     * Tests the sub-second incremental classification functionality by doing
     * the following:
     * 
     * <ol>
     * <li>The concept "Concretion of appendix" is removed from SNOMED.</li>
     * <li>This ontology is classified.</li>
     * <li>The axioms that were removed are added programmatically to the
     * ontology (see axioms below).</li>
     * <li>The new ontology is reclassified and the time taken to do so is
     * measured.</li>
     * <li>If time is below 1 second the test is successful.</li>
     * </ol>
     * 
     * Declaration(Class(:SCT_24764000)) AnnotationAssertion(rdfs:label
     * :SCT_24764000 "Concretion of appendix (disorder)" )
     * EquivalentClasses(:SCT_24764000 ObjectIntersectionOf(:SCT_18526009
     * ObjectSomeValuesFrom(:RoleGroup ObjectIntersectionOf(
     * ObjectSomeValuesFrom(:SCT_116676008 :SCT_56381008)
     * ObjectSomeValuesFrom(:SCT_363698007 :SCT_66754008) ) ) ) )
     */
    @Test
    public void testSnomed_20120131_Incremental() {
        System.out.println("Running testSnomed_20120131_Incremental");
        
        InputStream stated = this.getClass().getResourceAsStream("/snomed_20120131_inc_stated.owl");
        InputStream inferred = this.getClass().getResourceAsStream("/snomed_20120131_inferred.owl");

        try {
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLOntology ont = manager.loadOntologyFromOntologyDocument(stated);

            // Classify ontology from stated form
            SnorocketOWLReasoner c = new SnorocketOWLReasoner(ont, null, true);

            System.out.println("Classifying");
            c.synchronise();

            // Add additional axioms
            PrefixManager pm = new DefaultPrefixManager(
                    "http://www.ihtsdo.org/");
            OWLDataFactory df = ont.getOWLOntologyManager().getOWLDataFactory();
            OWLClass concr = df.getOWLClass(":SCT_24764000", pm);
            OWLAxiom a1 = df
                    .getOWLAnnotationAssertionAxiom(
                            concr.getIRI(),
                            df.getOWLAnnotation(
                                    df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL
                                            .getIRI()),
                                    df.getOWLLiteral("Concretion of appendix (disorder)")));
            OWLAxiom a2 = df.getOWLDeclarationAxiom(concr);
            OWLAxiom a3 = df
                    .getOWLEquivalentClassesAxiom(
                            concr,
                            df.getOWLObjectIntersectionOf(
                                    df.getOWLClass(":SCT_18526009", pm),
                                    df.getOWLObjectSomeValuesFrom(
                                            df.getOWLObjectProperty(
                                                    ":RoleGroup", pm),
                                            df.getOWLObjectIntersectionOf(
                                                    df.getOWLObjectSomeValuesFrom(
                                                            df.getOWLObjectProperty(
                                                                    ":SCT_116676008",
                                                                    pm),
                                                            df.getOWLClass(
                                                                    ":SCT_56381008",
                                                                    pm)),
                                                    df.getOWLObjectSomeValuesFrom(
                                                            df.getOWLObjectProperty(
                                                                    ":SCT_363698007",
                                                                    pm),
                                                            df.getOWLClass(
                                                                    ":SCT_66754008",
                                                                    pm))))));
            manager.addAxiom(ont, a1);
            manager.addAxiom(ont, a2);
            manager.addAxiom(ont, a3);

            // Classify again
            long start = System.currentTimeMillis();
            c.flush();

            // Measure time
            long time = System.currentTimeMillis() - start;
            System.out.println("Classified incrementally in: " + time+"ms");
            // Assert.assertTrue("Incremental classification took longer than 1 "
            // + "second: "+time, time < 1000);

            // Load ontology from inferred form to test for correctness
            OWLOntologyManager manager2 = OWLManager.createOWLOntologyManager();
            OWLOntology ont2 = manager2.loadOntologyFromOntologyDocument(inferred);

            System.out.println("Testing parent equality");
            int numOk = 0;
            int numWrong = 0;
            for (OWLClass cl : ont2.getClassesInSignature()) {
                Set<OWLClass> truth = new HashSet<OWLClass>();

                Set<OWLClassExpression> parents = cl.getSuperClasses(ont2);
                for (OWLClassExpression ocl : parents) {
                    if (!ocl.isAnonymous()) {
                        truth.add(ocl.asOWLClass());
                    }
                }

                Set<OWLClass> classified = new HashSet<OWLClass>();
                NodeSet<OWLClass> otherParents = c.getSuperClasses(cl, true);
                classified.addAll(otherParents.getFlattened());

                // Assert parents are equal
                if (truth.size() != classified.size()) {
                    numWrong++;
                    System.out.println(cl.toStringID() + "("
                            + DebugUtils.getLabel(cl, ont) + ")");
                    System.out.println("Truth: " + formatClassSet(truth, ont));
                    System.out.println("Classified: "
                            + formatClassSet(classified, ont));
                } else {
                    truth.removeAll(classified);

                    if (truth.isEmpty()) {
                        numOk++;
                    } else {
                        numWrong++;
                        System.out.println(cl.toStringID() + "("
                                + DebugUtils.getLabel(cl, ont) + ")");
                        System.out.println("Truth: "
                                + formatClassSet(truth, ont));
                        System.out.println("Classified: "
                                + formatClassSet(classified, ont));
                    }
                }
            }
            assertTrue("Num OK: " + numOk + " Num wrong: " + numWrong,
                    numWrong == 0);
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
            assertTrue("Error loading ontologies", false);
        }
    }

    /**
     * Similar to testIncremental() but removes two concepts from the original
     * ontology. The removed axioms are:
     * 
     * Declaration(Class(:SCT_24764000)) AnnotationAssertion(rdfs:label
     * :SCT_24764000 "Concretion of appendix (disorder)" )
     * EquivalentClasses(:SCT_24764000 ObjectIntersectionOf(:SCT_18526009
     * ObjectSomeValuesFrom(:RoleGroup ObjectIntersectionOf(
     * ObjectSomeValuesFrom(:SCT_116676008 :SCT_56381008)
     * ObjectSomeValuesFrom(:SCT_363698007 :SCT_66754008) ) ) ) )
     * 
     * Declaration(Class(:SCT_300307005)) AnnotationAssertion(rdfs:label
     * :SCT_300307005 "Finding of appendix (finding)" )
     * EquivalentClasses(:SCT_300307005 ObjectIntersectionOf(:SCT_118436003
     * ObjectSomeValuesFrom(:RoleGroup ObjectSomeValuesFrom(:SCT_363698007
     * :SCT_66754008) ) ) ) SubClassOf(:SCT_300308000
     * ObjectIntersectionOf(:SCT_300307005 ObjectSomeValuesFrom(:RoleGroup
     * ObjectSomeValuesFrom(:SCT_363698007 :SCT_66754008) ) ) )
     * SubClassOf(:SCT_300309008 ObjectIntersectionOf(:SCT_300307005
     * ObjectSomeValuesFrom(:RoleGroup ObjectSomeValuesFrom(:SCT_363698007
     * :SCT_66754008) ) ) ) SubClassOf(:SCT_422989001
     * ObjectIntersectionOf(:SCT_300307005 :SCT_395557000) )
     */
    @Test
    public void testSnomed_20120131_Incremental2() {
        System.out.println("Running testSnomed_20120131_Incremental2");
        
        InputStream stated = this.getClass().getResourceAsStream("/snomed_20120131_inc2_stated.owl");
        InputStream inferred = this.getClass().getResourceAsStream("/snomed_20120131_inferred.owl");

        try {
            System.out.println("Loading stated ontology");
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLOntology ont = manager.loadOntologyFromOntologyDocument(stated);

            // Classify ontology from stated form
            SnorocketOWLReasoner c = new SnorocketOWLReasoner(ont, null, true);

            System.out.println("Classifying");
            c.synchronise();

            // Add additional axioms
            PrefixManager pm = new DefaultPrefixManager(
                    "http://www.ihtsdo.org/");
            OWLDataFactory df = ont.getOWLOntologyManager().getOWLDataFactory();
            OWLClass concr = df.getOWLClass(":SCT_24764000", pm);
            OWLAxiom a1 = df
                    .getOWLAnnotationAssertionAxiom(
                            concr.getIRI(),
                            df.getOWLAnnotation(
                                    df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL
                                            .getIRI()),
                                    df.getOWLLiteral("Concretion of appendix (disorder)")));
            OWLAxiom a2 = df.getOWLDeclarationAxiom(concr);
            OWLAxiom a3 = df
                    .getOWLEquivalentClassesAxiom(
                            concr,
                            df.getOWLObjectIntersectionOf(
                                    df.getOWLClass(":SCT_18526009", pm),
                                    df.getOWLObjectSomeValuesFrom(
                                            df.getOWLObjectProperty(
                                                    ":RoleGroup", pm),
                                            df.getOWLObjectIntersectionOf(
                                                    df.getOWLObjectSomeValuesFrom(
                                                            df.getOWLObjectProperty(
                                                                    ":SCT_116676008",
                                                                    pm),
                                                            df.getOWLClass(
                                                                    ":SCT_56381008",
                                                                    pm)),
                                                    df.getOWLObjectSomeValuesFrom(
                                                            df.getOWLObjectProperty(
                                                                    ":SCT_363698007",
                                                                    pm),
                                                            df.getOWLClass(
                                                                    ":SCT_66754008",
                                                                    pm))))));

            OWLClass concr2 = df.getOWLClass(":SCT_300307005", pm);
            OWLAxiom a4 = df
                    .getOWLAnnotationAssertionAxiom(
                            concr2.getIRI(),
                            df.getOWLAnnotation(
                                    df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL
                                            .getIRI()),
                                    df.getOWLLiteral("Finding of appendix (finding)")));

            OWLAxiom a5 = df.getOWLDeclarationAxiom(concr2);

            OWLAxiom a6 = df.getOWLEquivalentClassesAxiom(df.getOWLClass(
                    ":SCT_300307005", pm), df.getOWLObjectIntersectionOf(df
                    .getOWLClass(":SCT_118436003", pm), df
                    .getOWLObjectSomeValuesFrom(df.getOWLObjectProperty(
                            ":RoleGroup", pm), df.getOWLObjectSomeValuesFrom(
                            df.getOWLObjectProperty(":SCT_363698007", pm),
                            df.getOWLClass(":SCT_66754008", pm)))));

            OWLAxiom a7 = df.getOWLSubClassOfAxiom(df.getOWLClass(
                    ":SCT_300308000", pm), df.getOWLObjectIntersectionOf(df
                    .getOWLClass("SCT_300307005", pm), df
                    .getOWLObjectSomeValuesFrom(df.getOWLObjectProperty(
                            ":RoleGroup", pm), df.getOWLObjectSomeValuesFrom(
                            df.getOWLObjectProperty(":SCT_363698007", pm),
                            df.getOWLClass(":SCT_66754008", pm)))));

            OWLAxiom a8 = df.getOWLSubClassOfAxiom(df.getOWLClass(
                    ":SCT_300309008", pm), df.getOWLObjectIntersectionOf(df
                    .getOWLClass("SCT_300307005", pm), df
                    .getOWLObjectSomeValuesFrom(df.getOWLObjectProperty(
                            ":RoleGroup", pm), df.getOWLObjectSomeValuesFrom(
                            df.getOWLObjectProperty(":SCT_363698007", pm),
                            df.getOWLClass(":SCT_66754008", pm)))));

            OWLAxiom a9 = df.getOWLSubClassOfAxiom(
                    df.getOWLClass(":SCT_422989001", pm),
                    df.getOWLObjectIntersectionOf(
                            df.getOWLClass("SCT_300307005", pm),
                            df.getOWLClass(":SCT_395557000", pm)));

            manager.addAxiom(ont, a1);
            manager.addAxiom(ont, a2);
            manager.addAxiom(ont, a3);
            manager.addAxiom(ont, a4);
            manager.addAxiom(ont, a5);
            manager.addAxiom(ont, a6);
            manager.addAxiom(ont, a7);
            manager.addAxiom(ont, a8);
            manager.addAxiom(ont, a9);

            // Classify again
            long start = System.currentTimeMillis();
            c.flush();

            // Measure time
            long time = System.currentTimeMillis() - start;
            System.out.println("Classified incrementally in: " + time+"ms");

            // Assert.assertTrue("Incremental classification took longer than 1 "
            // + "second: "+time, time < 1000);

            // Load ontology from inferred form to test for correctness
            System.out.println("Loading inferred ontology");
            OWLOntologyManager manager2 = OWLManager.createOWLOntologyManager();
            OWLOntology ont2 = manager2.loadOntologyFromOntologyDocument(inferred);

            System.out.println("Testing parent equality");
            int numOk = 0;
            int numWrong = 0;
            for (OWLClass cl : ont2.getClassesInSignature()) {
                Set<OWLClass> truth = new HashSet<OWLClass>();

                Set<OWLClassExpression> parents = cl.getSuperClasses(ont2);
                for (OWLClassExpression ocl : parents) {
                    if (!ocl.isAnonymous()) {
                        truth.add(ocl.asOWLClass());
                    }
                }

                Set<OWLClass> classified = new HashSet<OWLClass>();
                NodeSet<OWLClass> otherParents = c.getSuperClasses(cl, true);
                classified.addAll(otherParents.getFlattened());

                // Assert parents are equal
                if (truth.size() != classified.size()) {
                    numWrong++;
                    System.out.println(cl.toStringID() + "("
                            + DebugUtils.getLabel(cl, ont) + ")");
                    System.out.println("Truth: " + formatClassSet(truth, ont));
                    System.out.println("Classified: "
                            + formatClassSet(classified, ont));
                } else {
                    truth.removeAll(classified);

                    if (truth.isEmpty()) {
                        numOk++;
                    } else {
                        numWrong++;
                        System.out.println(cl.toStringID() + "("
                                + DebugUtils.getLabel(cl, ont) + ")");
                        System.out.println("Truth: "
                                + formatClassSet(truth, ont));
                        System.out.println("Classified: "
                                + formatClassSet(classified, ont));
                    }
                }
            }
            assertTrue("Num OK: " + numOk + " Num wrong: " + numWrong,
                    numWrong == 0);
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
            assertTrue("Error loading ontologies", false);
        }
    }

    /**
     * Similar to testIncremental2() but only one concept (:SCT_24764000) is
     * removed from the original ontology. This means that redundant axioms are
     * added programmatically.
     */
    @Test
    public void testSnomed_20120131_Incremental3() {
        System.out.println("Running testSnomed_20120131_Incremental3");
        
        InputStream stated = this.getClass().getResourceAsStream("/snomed_20120131_inc_stated.owl");
        InputStream inferred = this.getClass().getResourceAsStream("/snomed_20120131_inferred.owl");

        try {
            System.out.println("Loading stated ontology");
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLOntology ont = manager.loadOntologyFromOntologyDocument(stated);

            // Classify ontology from stated form
            SnorocketOWLReasoner c = new SnorocketOWLReasoner(ont, null, true);

            System.out.println("Classifying");
            c.synchronise();

            // Add additional axioms
            PrefixManager pm = new DefaultPrefixManager(
                    "http://www.ihtsdo.org/");
            OWLDataFactory df = ont.getOWLOntologyManager().getOWLDataFactory();
            OWLClass concr = df.getOWLClass(":SCT_24764000", pm);
            OWLAxiom a1 = df
                    .getOWLAnnotationAssertionAxiom(
                            concr.getIRI(),
                            df.getOWLAnnotation(
                                    df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL
                                            .getIRI()),
                                    df.getOWLLiteral("Concretion of appendix (disorder)")));
            OWLAxiom a2 = df.getOWLDeclarationAxiom(concr);
            OWLAxiom a3 = df
                    .getOWLEquivalentClassesAxiom(
                            concr,
                            df.getOWLObjectIntersectionOf(
                                    df.getOWLClass(":SCT_18526009", pm),
                                    df.getOWLObjectSomeValuesFrom(
                                            df.getOWLObjectProperty(
                                                    ":RoleGroup", pm),
                                            df.getOWLObjectIntersectionOf(
                                                    df.getOWLObjectSomeValuesFrom(
                                                            df.getOWLObjectProperty(
                                                                    ":SCT_116676008",
                                                                    pm),
                                                            df.getOWLClass(
                                                                    ":SCT_56381008",
                                                                    pm)),
                                                    df.getOWLObjectSomeValuesFrom(
                                                            df.getOWLObjectProperty(
                                                                    ":SCT_363698007",
                                                                    pm),
                                                            df.getOWLClass(
                                                                    ":SCT_66754008",
                                                                    pm))))));

            OWLClass concr2 = df.getOWLClass(":SCT_300307005", pm);
            OWLAxiom a4 = df
                    .getOWLAnnotationAssertionAxiom(
                            concr2.getIRI(),
                            df.getOWLAnnotation(
                                    df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL
                                            .getIRI()),
                                    df.getOWLLiteral("Finding of appendix (finding)")));

            OWLAxiom a5 = df.getOWLDeclarationAxiom(concr2);

            OWLAxiom a6 = df.getOWLEquivalentClassesAxiom(df.getOWLClass(
                    ":SCT_300307005", pm), df.getOWLObjectIntersectionOf(df
                    .getOWLClass(":SCT_118436003", pm), df
                    .getOWLObjectSomeValuesFrom(df.getOWLObjectProperty(
                            ":RoleGroup", pm), df.getOWLObjectSomeValuesFrom(
                            df.getOWLObjectProperty(":SCT_363698007", pm),
                            df.getOWLClass(":SCT_66754008", pm)))));

            OWLAxiom a7 = df.getOWLSubClassOfAxiom(df.getOWLClass(
                    ":SCT_300308000", pm), df.getOWLObjectIntersectionOf(df
                    .getOWLClass("SCT_300307005", pm), df
                    .getOWLObjectSomeValuesFrom(df.getOWLObjectProperty(
                            ":RoleGroup", pm), df.getOWLObjectSomeValuesFrom(
                            df.getOWLObjectProperty(":SCT_363698007", pm),
                            df.getOWLClass(":SCT_66754008", pm)))));

            OWLAxiom a8 = df.getOWLSubClassOfAxiom(df.getOWLClass(
                    ":SCT_300309008", pm), df.getOWLObjectIntersectionOf(df
                    .getOWLClass("SCT_300307005", pm), df
                    .getOWLObjectSomeValuesFrom(df.getOWLObjectProperty(
                            ":RoleGroup", pm), df.getOWLObjectSomeValuesFrom(
                            df.getOWLObjectProperty(":SCT_363698007", pm),
                            df.getOWLClass(":SCT_66754008", pm)))));

            OWLAxiom a9 = df.getOWLSubClassOfAxiom(
                    df.getOWLClass(":SCT_422989001", pm),
                    df.getOWLObjectIntersectionOf(
                            df.getOWLClass("SCT_300307005", pm),
                            df.getOWLClass(":SCT_395557000", pm)));

            manager.addAxiom(ont, a1);
            manager.addAxiom(ont, a2);
            manager.addAxiom(ont, a3);
            manager.addAxiom(ont, a4);
            manager.addAxiom(ont, a5);
            manager.addAxiom(ont, a6);
            manager.addAxiom(ont, a7);
            manager.addAxiom(ont, a8);
            manager.addAxiom(ont, a9);

            // Classify again
            long start = System.currentTimeMillis();
            c.flush();

            // Measure time
            long time = System.currentTimeMillis() - start;
            System.out.println("Classified incrementally in: " + time+"ms");

            // Assert.assertTrue("Incremental classification took longer than 1 "
            // + "second: "+time, time < 1000);

            // Load ontology from inferred form to test for correctness
            System.out.println("Loading inferred ontology");
            OWLOntologyManager manager2 = OWLManager.createOWLOntologyManager();
            OWLOntology ont2 = manager2.loadOntologyFromOntologyDocument(inferred);

            System.out.println("Testing parent equality");
            int numOk = 0;
            int numWrong = 0;
            for (OWLClass cl : ont2.getClassesInSignature()) {
                Set<OWLClass> truth = new HashSet<OWLClass>();

                Set<OWLClassExpression> parents = cl.getSuperClasses(ont2);
                for (OWLClassExpression ocl : parents) {
                    if (!ocl.isAnonymous()) {
                        truth.add(ocl.asOWLClass());
                    }
                }

                Set<OWLClass> classified = new HashSet<OWLClass>();
                NodeSet<OWLClass> otherParents = c.getSuperClasses(cl, true);
                classified.addAll(otherParents.getFlattened());

                // Assert parents are equal
                if (truth.size() != classified.size()) {
                    numWrong++;
                    System.out.println(cl.toStringID() + "("
                            + DebugUtils.getLabel(cl, ont) + ")");
                    System.out.println("Truth: " + formatClassSet(truth, ont));
                    System.out.println("Classified: "
                            + formatClassSet(classified, ont));
                } else {
                    truth.removeAll(classified);

                    if (truth.isEmpty()) {
                        numOk++;
                    } else {
                        numWrong++;
                        System.out.println(cl.toStringID() + "("
                                + DebugUtils.getLabel(cl, ont) + ")");
                        System.out.println("Truth: "
                                + formatClassSet(truth, ont));
                        System.out.println("Classified: "
                                + formatClassSet(classified, ont));
                    }
                }
            }
            assertTrue("Num OK: " + numOk + " Num wrong: " + numWrong,
                    numWrong == 0);
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
            assertTrue("Error loading ontologies", false);
        }
    }

    /**
     * Classifies the stated version of an ontology in RF1 format and compares
     * it to a correctly classified version (available in the canonical table).
     * 
     * @param concepts
     * @param relations
     * @param canonical
     */
    private void testRF1Ontology(InputStream concepts, InputStream descriptions,
            InputStream relations, InputStream canonical, String version) {
        // Classify ontology from stated form
        System.out.println("Classifying ontology");
        IFactory<String> factory = new CoreFactory<>();
        NormalisedOntology<String> no = new NormalisedOntology<>(factory);
        System.out.println("Importing axioms");
        RF1Importer imp = new RF1Importer(concepts, relations, version);
        Map<String, Map<String, IOntology<String>>> res = imp.getOntologyVersions(new NullProgressMonitor());
        System.out.println("Loading axioms");
        no.loadAxioms(new HashSet<>(res.values().iterator().next().get(version).getStatedAxioms()));
        System.out.println("Running classification");
        no.classify();
        System.out.println("Computing taxonomy");
        no.buildTaxonomy();
        System.out.println("Done");

        // Load ontology from canonical table
        System.out.println("Loading ontology from canonical table");
        BufferedReader br = null;
        try {

            Map<String, Set<String>> canonicalParents = new TreeMap<>();
            
            br = new BufferedReader(new InputStreamReader(canonical));
            String line;
            while (null != (line = br.readLine())) {
                if (line.trim().length() < 1) {
                    continue;
                }
                int idx1 = line.indexOf('\t');
                int idx2 = line.indexOf('\t', idx1 + 1);
                int idx3 = line.indexOf('\t', idx2 + 1);
                int idx4 = line.indexOf('\t', idx3 + 1);
                int idx5 = line.indexOf('\t', idx4 + 1);
                int idx6 = line.indexOf('\t', idx5 + 1);

                // 0..idx1 == relationship id
                // idx1+1..idx2 == concept id1
                // idx2+1..idx3 == relationship type
                // idx3+1..idx4 == concept id2
                // idx4+1..idx5 == characteristic type
                // idx5+1..idx6 == refinability
                // idx6+1..end == relationship group

                if (idx1 < 0 || idx2 < 0 || idx3 < 0 || idx4 < 0 || idx5 < 0
                        || idx6 < 0) {
                    throw new RuntimeException("Concepts: Mis-formatted "
                            + "line, expected 7 tab-separated fields, "
                            + "got: " + line);
                }

                final String conceptId1 = line.substring(idx1 + 1, idx2);
                final String relId = line.substring(idx2 + 1, idx3);
                final String conceptId2 = line.substring(idx3 + 1, idx4);

                if (relId.equals(imp.getMetadata().getIsAId(version))) {
                    Set<String> parents = canonicalParents.get(conceptId1);
                    if (parents == null) {
                        parents = new HashSet<String>();
                        canonicalParents.put(conceptId1, parents);
                    }
                    parents.add(conceptId2);
                }
            }

            // Build taxonomy from canonical table
            final String top = "_top_";
            final String bottom = "_bottom_";
            Map<String, Set<String>> canonicalEquivs = new TreeMap<>();
            Set<String> topSet = new HashSet<>();
            topSet.add(top);
            canonicalEquivs.put(top, topSet);
            for (String key : canonicalParents.keySet()) {
                Set<String> eq = new TreeSet<>();
                eq.add(key);
                canonicalEquivs.put(key, eq);
                Set<String> parents = canonicalParents.get(key);
                if (parents == null) {
                    // Create the equivalent set with key
                    Set<String> val = new TreeSet<>();
                    val.add(key);
                    canonicalEquivs.put(key, val);
                    continue;
                }
                for (String parent : parents) {
                    Set<String> grandpas = canonicalParents.get(parent);
                    if (grandpas != null && grandpas.contains(key)) {
                        // Concepts are equivalent
                        Set<String> equivs1 = canonicalEquivs.get(parent);
                        if (equivs1 == null)
                            equivs1 = new TreeSet<String>();
                        equivs1.add(key);
                        equivs1.add(parent);
                        Set<String> equivs2 = canonicalEquivs.get(key);
                        if (equivs2 == null)
                            equivs2 = new TreeSet<String>();
                        equivs2.add(key);
                        equivs2.add(parent);
                        equivs1.addAll(equivs2);
                        canonicalEquivs.put(key, equivs1);
                        canonicalEquivs.put(parent, equivs1);
                    }
                }
            }
            
            // Compare canonical and classified
            List<String> problems = new ArrayList<String>();
            Map<String, Node<String>> tax = no.getTaxonomy();
            
            for (Object key : tax.keySet()) {
                
                String concept = null;
                if(key == au.csiro.ontology.model.Concept.TOP) {
                    concept = top;
                } else if(key == au.csiro.ontology.model.Concept.BOTTOM){
                    concept = bottom;
                } else {
                    concept = (String) key; 
                }
                
                Node<String> ps = null;
                
                if(key instanceof String) {
                    ps = no.getEquivalents((String)key);
                } else if(key == Concept.TOP) {
                    ps = no.getTopNode();
                } else if(key == Concept.BOTTOM) {
                    ps = no.getBottomNode();
                }

                // Actual equivalents set
                Set<String> aeqs = new HashSet<>();

                for (Object cid : ps.getEquivalentConcepts()) {
                    if(cid == Concept.TOP)
                        aeqs.add(top);
                    else if(cid == Concept.BOTTOM)
                        aeqs.add(bottom);
                    else
                        aeqs.add((String)cid);
                }

                // Actual parents set
                Set<String> aps = new HashSet<>();
                Set<Node<String>> parents = ps.getParents();
                for (Node<String> parent : parents) {
                    for (Object pid : parent.getEquivalentConcepts()) {
                        if(pid == Concept.TOP)
                            aps.add(top);
                        else if(pid == Concept.BOTTOM)
                            aps.add(bottom);
                        else
                            aps.add((String)pid);
                    }
                }
                 
                // FIXME: BOTTOM is not connected and TOP is not assigned as a
                // parent of SNOMED_CT_CONCEPT
                if (bottom.equals(concept)
                        || "138875005".equals(concept))
                    continue;

                Set<String> cps = canonicalParents.get(concept);
                Set<String> ceqs = canonicalEquivs.get(concept);

                // Compare both sets
                if (cps == null) {
                    cps = Collections.emptySet();
                }

                if (cps.size() != aps.size()) {
                    problems.add("Problem with concept " + concept
                            + ": canonical parents size = " + cps.size() + " ("
                            + cps.toString() + ")" + " actual parents size = "
                            + aps.size() + " (" + aps.toString() + ")");
                    continue;
                }

                for (String s : cps) {
                    if (!aps.contains(s)) {
                        problems.add("Problem with concept " + concept
                                + ": parents do not contain concept " + s);
                    }
                }

                if (ceqs == null) {
                    ceqs = Collections.emptySet();
                }

                // Add the concept to its set of equivalents (every concept is
                // equivalent to itself)
                aeqs.add(concept);
                if (ceqs.size() != aeqs.size()) {
                    problems.add("Problem with concept " + concept
                            + ": canonical equivalents size = " + ceqs.size()
                            + " actual equivalents size = " + aeqs.size());
                }
                for (String s : ceqs) {
                    if (!aeqs.contains(s)) {
                        problems.add("Problem with concept " + concept
                                + ": equivalents do not contain concept " + s);
                    }
                }
            }

            for (String problem : problems) {
                System.err.println(problem);
            }

            Assert.assertTrue(problems.isEmpty());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(false);
        } finally {
            if (br != null) try { br.close(); } catch (Exception e) {}
        }
    }

    /**
     * Classifies the stated version of an ontology and compares it to a
     * correctly classified version. All the classes of the ontologies are
     * traversed and their direct parents are compared.
     * 
     * @param stated
     *            The {@link File} that contains the stated ontology.
     * @param iriInferred
     *            The {@link File} that contains the classified ontology.
     * @param ignoreBottom
     *            Indicates if the bottom node should be ignored in the
     *            comparison (some generated inferred files do not connect
     *            bottom).
     */
    private void testOntology(File stated, File inferred, boolean ignoreBottom) {
        try {
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLOntology ont = manager.loadOntologyFromOntologyDocument(stated);

            // Classify ontology from stated form
            SnorocketOWLReasoner c = new SnorocketOWLReasoner(ont, null, false);

            System.out.println("Classifying");
            c.synchronise();

            // Load ontology from inferred form
            System.out.println("Loading inferred ontology");
            OWLOntologyManager manager2 = OWLManager.createOWLOntologyManager();
            OWLOntology ont2 = null;
            if(inferred.getName().endsWith("gz")) {
                ont2 = manager.loadOntologyFromOntologyDocument(
                        new GZIPInputStream(new FileInputStream(inferred)));
            } else {
                ont2 = manager2.loadOntologyFromOntologyDocument(inferred);
            }

            System.out.println("Testing parent equality");
            int numOk = 0;
            int numWrong = 0;
            for (OWLClass cl : ont2.getClassesInSignature()) {

                // Ignore owl:nothing - some generated inferred files do not
                // connect childless nodes to owl:nothing
                if (ignoreBottom
                        && cl.toStringID().equals(
                                "http://www.w3.org/2002/07/owl#Nothing"))
                    continue;

                Set<OWLClass> truth = new HashSet<OWLClass>();

                Set<OWLClassExpression> parents = cl.getSuperClasses(ont2);
                for (OWLClassExpression ocl : parents) {
                    if (!ocl.isAnonymous()) {
                        truth.add(ocl.asOWLClass());
                    }
                }

                Set<OWLClass> classified = new HashSet<OWLClass>();
                NodeSet<OWLClass> otherParents = c.getSuperClasses(cl, true);
                classified.addAll(otherParents.getFlattened());

                // Assert parents are equal
                if (truth.size() != classified.size()) {
                    numWrong++;
                    System.out.println(cl.toStringID() + "("
                            + DebugUtils.getLabel(cl, ont) + ")");
                    System.out.println("Truth: " + formatClassSet(truth, ont));
                    System.out.println("Classified: "
                            + formatClassSet(classified, ont));
                } else {
                    truth.removeAll(classified);

                    if (truth.isEmpty()) {
                        numOk++;
                    } else {
                        numWrong++;
                        System.out.println(cl.toStringID() + "("
                                + DebugUtils.getLabel(cl, ont) + ")");
                        System.out.println("Truth: "
                                + formatClassSet(truth, ont));
                        System.out.println("Classified: "
                                + formatClassSet(classified, ont));
                    }
                }
            }
            assertTrue("Num OK: " + numOk + " Num wrong: " + numWrong,
                    numWrong == 0);
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
            assertTrue("Error loading ontologies", false);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Classifies the stated version of an ontology and compares it to a
     * correctly classified version. All the classes of the ontologies are
     * traversed and their direct parents are compared.
     * 
     * @param stated
     *            The {@link InputStream} of the stated ontology.
     * @param inferred
     *            The {@link InputStream} of the classified ontology.
     * @param ignoreBottom
     *            Indicates if the bottom node should be ignored in the
     *            comparison (some generated inferred files do not connect
     *            bottom).
     */
    private void testOntology(InputStream stated, InputStream inferred, boolean ignoreBottom) {
        try {
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLOntology ont = manager.loadOntologyFromOntologyDocument(stated);
            // Classify ontology from stated form
            SnorocketOWLReasoner c = new SnorocketOWLReasoner(ont, null, false);

            System.out.println("Classifying");
            c.synchronise();

            // Load ontology from inferred form
            System.out.println("Loading inferred ontology");
            OWLOntologyManager manager2 = OWLManager.createOWLOntologyManager();
            OWLOntology ont2 = manager2.loadOntologyFromOntologyDocument(inferred);

            System.out.println("Testing parent equality");
            int numOk = 0;
            int numWrong = 0;
            for (OWLClass cl : ont2.getClassesInSignature()) {

                // Ignore owl:nothing - some generated inferred files do not
                // connect childless nodes to owl:nothing
                if (ignoreBottom
                        && cl.toStringID().equals(
                                "http://www.w3.org/2002/07/owl#Nothing"))
                    continue;

                Set<OWLClass> truth = new HashSet<OWLClass>();

                Set<OWLClassExpression> parents = cl.getSuperClasses(ont2);
                for (OWLClassExpression ocl : parents) {
                    if (!ocl.isAnonymous()) {
                        truth.add(ocl.asOWLClass());
                    }
                }

                Set<OWLClass> classified = new HashSet<OWLClass>();
                NodeSet<OWLClass> otherParents = c.getSuperClasses(cl, true);
                classified.addAll(otherParents.getFlattened());

                // Assert parents are equal
                if (truth.size() != classified.size()) {
                    numWrong++;
                    System.out.println(cl.toStringID() + "("
                            + DebugUtils.getLabel(cl, ont) + ")");
                    System.out.println("Truth: " + formatClassSet(truth, ont));
                    System.out.println("Classified: "
                            + formatClassSet(classified, ont));
                } else {
                    truth.removeAll(classified);

                    if (truth.isEmpty()) {
                        numOk++;
                    } else {
                        numWrong++;
                        System.out.println(cl.toStringID() + "("
                                + DebugUtils.getLabel(cl, ont) + ")");
                        System.out.println("Truth: "
                                + formatClassSet(truth, ont));
                        System.out.println("Classified: "
                                + formatClassSet(classified, ont));
                    }
                }
            }
            assertTrue("Num OK: " + numOk + " Num wrong: " + numWrong,
                    numWrong == 0);
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
            assertTrue("Error loading ontologies", false);
        }
    }

    private String formatClassSet(Set<OWLClass> set, OWLOntology ont) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (OWLClass c : set) {
            sb.append(c.toStringID());
            sb.append("(");
            sb.append(DebugUtils.getLabel(c, ont));
            sb.append(") ");
        }
        sb.append("]");
        return sb.toString();
    }

}
