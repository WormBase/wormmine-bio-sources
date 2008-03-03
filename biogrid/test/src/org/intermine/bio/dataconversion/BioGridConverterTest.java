package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2008 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.HashMap;
import java.util.Set;

import org.intermine.dataconversion.FileConverter;
import org.intermine.dataconversion.ItemsTestCase;
import org.intermine.dataconversion.MockItemWriter;
import org.intermine.metadata.Model;

import java.io.InputStreamReader;
import java.io.Reader;

public class BioGridConverterTest extends ItemsTestCase
{
    public BioGridConverterTest(String arg) {
        super(arg);
    }

    public void setUp() throws Exception {
        super.setUp();
    }

    public void testProcess() throws Exception {

        Reader reader = new InputStreamReader(getClass().getClassLoader()
                                            .getResourceAsStream("BioGridConverterTest_src.xml"));
        MockItemWriter itemWriter = new MockItemWriter(new HashMap());
        FileConverter converter = new BioGridConverter(itemWriter,
                                                  Model.getInstanceByName("genomic"));
        converter.process(reader);
        converter.close();

        // uncomment to write out a new target items file
        //writeItemsFile(itemWriter.getItems(), "biogrid-tgt-items.xml");

        Set expected = readItemSet("BioGridConverterTest_tgt.xml");

        assertEquals(expected, itemWriter.getItems());
    }
}
