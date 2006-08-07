package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2005 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import org.intermine.bio.io.gff3.GFF3Parser;
import org.intermine.bio.io.gff3.GFF3Record;
import org.intermine.dataconversion.MockItemWriter;
import org.intermine.metadata.Model;
import org.intermine.util.TypeUtil;
import org.intermine.xml.full.FullParser;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.ItemFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;

/**
 * Tests for the RedFlyGFF3RecordHandler class.
 *
 * @author Kim Rutherford
 */
public class RedFlyGFF3RecordHandlerTest extends XMLTestCase
{
    private Model tgtModel;
    private RedFlyGFF3RecordHandler handler;
    private MockItemWriter writer = new MockItemWriter(new LinkedHashMap());
    private String seqClsName = "Chromosome";
    private String orgAbbrev = "DM";
    private String dataSourceName = "FlyReg";
    private String dataSetTitle = "FlyReg data set";
    private String tgtNs;
    private ItemFactory itemFactory;
    private List featureIdentifiers;

    public void setUp() throws Exception {
        tgtModel = Model.getInstanceByName("genomic");
        handler = new RedFlyGFF3RecordHandler(tgtModel);
        // call the GFF3Converter constructor to initialise the handler
        new GFF3Converter(writer, seqClsName, orgAbbrev, dataSourceName,
                          dataSetTitle, tgtModel, handler);
        tgtNs = tgtModel.getNameSpace().toString();
        itemFactory = handler.getItemFactory();

    }


    public void testFlyRegHandler() throws Exception {
        String gff =
            "2L\tREDfly\tregulatory_region\t12092691\t12095792\t.\t.\t.\tID=\"prd_A8_repressor\"; Dbxref=\"Flybase:FBgn0003145\", \"PMID:7873402\", \"REDfly:391\"; Evidence=\"reporter construct (in vivo)\"; Ontology_term=\"FBbt:00005304\",\"FBbt:00000111\",\"FBbt:00005427\",\"FBbt:00005414\"\n"
            + "2L\tREDfly\tregulatory_region\t12087891\t12088492\t.\t.\t.\tID=\"prd_P1_enhancer\"; Dbxref=\"Flybase:FBgn0003145\", \"PMID:7873402\", \"REDfly:392\"; Evidence=\"reporter construct (in vivo)\"; Ontology_term=\"FBbt:00005304\",\"FBbt:100000111\",\"FBbt:100005427\",\"FBbt:100005414\"\n"
            + "3R\tREDfly\tregulatory_region\t2667896\t2676484\t.\t.\t.\tID=\"Scr_BSR\"; Dbxref=\"Flybase:FBgn0003339\", \"PMID:7713432\", \"REDfly:576\"; Evidence=\"reporter construct (in vivo)\"; Ontology_term=\"FBbt:00000090\",\"FBbt:00000111\",\"FBbt:00005245\",\"FBbt:00000169\",\"FBbt:00000170\",\"FBbt:00000158\",\"FBbt:00000168\"";

        BufferedReader srcReader = new BufferedReader(new StringReader(gff));

        LinkedHashSet allItems = new LinkedHashSet();
        
        Iterator iter = GFF3Parser.parse(srcReader);

        featureIdentifiers = new ArrayList();

        while (iter.hasNext()) {
            GFF3Record record = (GFF3Record) iter.next();
            String term = record.getType();
            String className = TypeUtil.javaiseClassName(term);
            Item feature = itemFactory.makeItem(null, tgtNs + className, "");

            handler.setFeature(feature);
            handler.process(record);
            feature.setAttribute("identifier", record.getId());
            
            featureIdentifiers.add(feature.getIdentifier());
            
            allItems.addAll(handler.getItems());
        }
        
        StringBuffer actualXml = new StringBuffer();
        
        actualXml.append("<items>");

        Iterator itemIter = allItems.iterator();
        
        while (itemIter.hasNext()) {
            actualXml.append(itemIter.next());
        }
        
        actualXml.append("</items>");
        
        XMLUnit.setIgnoreWhitespace(true);
        
        System.err.println("exp: " + getExpectedItems());
        System.err.println("act: " + actualXml);
        
        assertXMLEqual(getExpectedItems(), actualXml.toString());
    }
    
    protected String getExpectedItems() throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("RedFlyGFF3RecordHandlerTest.xml")));
        StringBuffer buff = new StringBuffer();
        String line = null;
        while ((line = br.readLine()) != null) {
            buff.append(line);
        }
        return buff.toString();
    }

}
