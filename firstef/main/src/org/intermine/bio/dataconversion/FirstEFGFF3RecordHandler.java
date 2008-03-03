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


import org.intermine.bio.io.gff3.GFF3Record;
import org.intermine.metadata.Model;


/**
 * A converter/retriever for FirstEF GFF3 files (from UCSC).
 *
 * @author Wenyan Ji
 */

public class FirstEFGFF3RecordHandler extends GFF3RecordHandler
{


    /**
     * Create a new FirstEFGFF3RecordHandler for the given target model.
     * @param tgtModel the model for which items will be created
     */
    public FirstEFGFF3RecordHandler(Model tgtModel) {
        super(tgtModel);

    }

    /**
     * {@inheritDoc}
     */
    public void process(GFF3Record record) {
        // empty
    }
}
