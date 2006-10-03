package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2003 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import junit.framework.TestCase;

import java.io.File;
import java.io.StringReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import org.intermine.xml.full.FullParser;
import org.intermine.xml.full.FullRenderer;
import org.intermine.dataconversion.DataTranslatorTestCase;
import org.intermine.dataconversion.MockItemWriter;

public class RNAiConverterTest extends TestCase
{
    public void testProcess() throws Exception {
        String ENDL = System.getProperty("line.separator");
        String input = "WB Gene ID\t6239\tPubMed ID\tPhenotype\tPhenotype Desc\tRemark" + ENDL
            + "WBGene00015175\t6239\t11099033\tEmb\tembryonic lethal" + ENDL
            + "WBGene00016559\t6239\t11231151\tWT\twild type morphology\tclone does not match to the reported genomic sequence" + ENDL;

        MockItemWriter itemWriter = new MockItemWriter(new HashMap());
        RNAiConverter converter = new RNAiConverter(itemWriter);

        converter.process(new StringReader(input));
        converter.close();

        // uncomment to write out a new target items file
        //FileWriter fw = new FileWriter(new File("worm-rnai_tgt.xml"));
        //fw.write(FullRenderer.render(itemWriter.getItems()));
        //fw.close();

        Set expected = new HashSet(FullParser.parse(getClass().getClassLoader().getResourceAsStream("test/RNAiConverterTest.xml")));

        System.err.println(DataTranslatorTestCase.printCompareItemSets(new HashSet(expected), itemWriter.getItems()));

        assertEquals(expected, itemWriter.getItems());
    }
}
