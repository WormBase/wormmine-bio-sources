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

import java.io.Reader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

import org.intermine.objectstore.ObjectStoreException;
import org.intermine.metadata.Model;
import org.intermine.metadata.MetaDataException;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.ReferenceList;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.util.StringUtil;

import org.apache.log4j.Logger;

/**
 * DataConverter to load flat file linking BDGP clones to Flybase genes.
 * @author Wenyan Ji
 */
public class ImageCloneConverter extends CDNACloneConverter
{
    protected static final Logger LOG = Logger.getLogger(ImageCloneConverter.class);

    protected Item dataSource;
    protected Item dataSet;
    protected Item organism;
    protected Map geneMap = new HashMap();
    protected Map cloneMap = new HashMap();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     * @throws ObjectStoreException if an error occurs in storing
     * @throws MetaDataException if cannot generate model
     */
    public ImageCloneConverter(ItemWriter writer, Model model)
        throws ObjectStoreException, MetaDataException {
        super(writer, model);

        dataSource = createItem("DataSource");
        dataSource.setAttribute("name", "RZPD");
        store(dataSource);

        dataSet = createItem("DataSet");
        dataSet.setAttribute("title", "RZPD uniprot data set");
        store(dataSet);

        organism = createItem("Organism");
        organism.setAttribute("abbreviation", "HS");
        store(organism);
    }


    /**
     * Read each line from flat file.
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {

        BufferedReader br = new BufferedReader(reader);
        //intentionally throw away first line
        String line = br.readLine();

        while ((line = br.readLine()) != null) {
            String[] array = line.split("\t", -1); //keep trailing empty Strings

            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }

            String[] geneId = array[16].trim().split(",");
            String cloneId = array[5].trim();
            // if clone points to more than one gene, or points to a gene without identifier
            // don't create gene
            if (geneId.length == 1 && geneId[0].length() > 1 && StringUtil.allDigits(cloneId)) {
                Item gene = createGene("Gene", geneId[0], organism.getIdentifier(),
                                       getItemWriter());
                createClone("CDNAClone", cloneId, organism.getIdentifier(),
                            gene.getIdentifier(), dataSource.getIdentifier(),
                            dataSet.getIdentifier(), getItemWriter());
            }
        }

    }

    /**
     * @param clsName = target class name
     * @param id = identifier
     * @param ordId = ref id for organism
     * @param writer = itemWriter write item to objectstore
     * @return item
     * @throws exception if anything goes wrong when writing items to objectstore
     */
    private Item createGene(String clsName, String id, String orgId, ItemWriter writer)
        throws Exception {
        Item gene = (Item) geneMap.get(id);
        if (gene == null) {
            gene = createItem(clsName);
            gene.setAttribute("primaryIdentifier", id);
            gene.setReference("organism", orgId);
            geneMap.put(id, gene);
            store(gene);
        }
        return gene;
    }

    /**
     * @param clsName = target class name
     * @param id = identifier
     * @param ordId = ref id for organism
     * @param geneId = ref id for gene item
     * @param dbId = ref id for db item
     * @param writer = itemWriter write item to objectstore
     * @return item
     * @throws exception if anything goes wrong when writing items to objectstore
     */
     private void createClone(String clsName, String id, String orgId, String geneId,
                              String datasourceId, String datasetId, ItemWriter writer)
        throws Exception {
        Item clone = (Item) cloneMap.get(id);
        if (clone == null) {
            clone = createItem(clsName);
            clone.setAttribute("primaryIdentifier", id);
            clone.setReference("organism", orgId);
            clone.setReference("gene", geneId);
            clone.addCollection(new ReferenceList("evidence",
                                new ArrayList(Collections.singleton(datasetId))));
            cloneMap.put(id, clone);
            store(clone);

            Item synonym = createItem("Synonym");
            synonym.setAttribute("type", "identifier");
            synonym.setAttribute("value", id);
            synonym.setReference("source", datasourceId);
            synonym.setReference("subject", clone.getIdentifier());
            store(synonym);
        }
    }

}


