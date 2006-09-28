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

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

import org.intermine.util.TextFileUtil;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.metadata.Model;
import org.intermine.metadata.MetaDataException;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.ItemHelper;
import org.intermine.xml.full.ItemFactory;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.dataconversion.FileConverter;

import org.apache.log4j.Logger;

/**
 * DataConverter to load Drosophila2 Affymetrix probe set from a .gin file.
 * Written to parse Drosophila_2 set but can hopefully be extended to any set.
 *
 * @author Richard Smith
 */
public class Drosophila2ProbeConverter extends FileConverter
{
    protected static final String GENOMIC_NS = "http://www.flymine.org/model/genomic#";

    protected static final Logger LOG = Logger.getLogger(Drosophila2ProbeConverter.class);

    protected Item dataSource, dataSet, org;
    protected ItemFactory itemFactory;
    protected Map bioMap = new HashMap(), chrMap = new HashMap(), ids = new HashMap();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @throws ObjectStoreException if an error occurs in storing
     * @throws MetaDataException if cannot generate model
     */
    public Drosophila2ProbeConverter(ItemWriter writer)
        throws ObjectStoreException, MetaDataException {
        super(writer);

        itemFactory = new ItemFactory(Model.getInstanceByName("genomic"), "-1_");

        dataSource = createItem("DataSource");
        dataSource.setAttribute("name", "Affymetrix");
        writer.store(ItemHelper.convert(dataSource));

        dataSet = createItem("DataSet");
        dataSet.setReference("dataSource", dataSource.getIdentifier());

        org = createItem("Organism");
        org.setAttribute("taxonId", "7227");
        writer.store(ItemHelper.convert(org));

    }


    /**
     * Read each line from flat file.
     *
     * @see DataConverter#process
     */
    public void process(Reader reader) throws Exception {
        String arrayName;
        Iterator lineIter = TextFileUtil.parseTabDelimitedReader(reader);
        String [] headers = null;
        int lineNo = 0;
        boolean readingData = false;

        while (lineIter.hasNext()) {
            String[] line = (String[]) lineIter.next();


            // get the name of the array.  TODO also version?
            if (readingData) {
                String seqType = line[6];

                Item probeSet = createProbeSet(line[3]);
                if (seqType.equalsIgnoreCase("control sequence")) {
                    // add a description and flag
                    probeSet.setAttribute("description", line[4]);
                    probeSet.setAttribute("isControl", "true");
                } else {
                    probeSet.setAttribute("isControl", "false");

                    // create chromosome location for probe set

                    String chrId = line[12];
                    if (chrId != null && !chrId.equals("")) {
                        Item chr = createChromosome(line[12]);

                        Item loc = createItem("Location");
                        loc.setReference("subject", chr.getIdentifier());
                        loc.setReference("object", probeSet.getIdentifier());
                        loc.setAttribute("strand", line[13].equals("+") ? "1" : "-1");
                        loc.setAttribute("start", line[14]);
                        loc.setAttribute("end", line[15]);
                        loc.setCollection("evidence",
                            new ArrayList(Collections.singleton(dataSet.getIdentifier())));

                        writer.store(ItemHelper.convert(loc));
                    }
                }

                if (seqType.equals("BDGP")) {
                    // set reference to transcript
                    Item transcript = createBioEntity("Transcript", line[7]);
                    probeSet.setReference("transcript", transcript.getIdentifier());

                    // create a gene, get CG identifier from start of description
                    String desc = line[8];
                    if (desc != null && !desc.equals("") && desc.startsWith("[CG")) {
                        // e.g. '[CG2239 gene symbol:jdp FBgn0027654 ]'
                        String geneId = desc.substring(1, desc.indexOf(' '));
                        Item gene = createBioEntity("Gene", geneId);
                        probeSet.setReference("gene", gene.getIdentifier());
                    }
                }
                writer.store(ItemHelper.convert(probeSet));
            } else {
                // still in the header
                if (line[0].startsWith("Arrays")) {
                    arrayName = line[0].substring(line[0].indexOf('=') + 1);
                    dataSet.setAttribute("title", "Affymetrix array: " + arrayName);
                    writer.store(ItemHelper.convert(dataSet));
                }
            }

            // TODO create dataset and data source

            // actual data starts when first column title is Index
            if (line[0].equalsIgnoreCase("index")) {
                readingData = true;
            }
        }
    }

    /**
     * @param clsName = target class name
     * @param ordId = ref id for organism
     * @param geneEnsembl = ensembl identifier used for gene organismDbId
     * @param writer = itemWriter write item to objectstore
     * @return item
     * @throws exception if anything goes wrong when writing items to objectstore
     */
    private Item createBioEntity(String clsName, String identifier)
        throws ObjectStoreException {
        Item bio = (Item) bioMap.get(identifier);
        if (bio == null) {
            bio = createItem(clsName);
            bio.setReference("organism", org.getIdentifier());
            bio.setAttribute("identifier", identifier);
            bioMap.put(identifier, bio);
            writer.store(ItemHelper.convert(bio));
        }
        return bio;
    }

    /**
     * @param clsName = target class name
     * @param id = identifier
     * @param ordId = ref id for organism
     * @param datasourceId = ref id for datasource item
     * @param datasetId = ref id for dataset item
     * @param writer = itemWriter write item to objectstore
     * @return item
     * @throws exception if anything goes wrong when writing items to objectstore
     */
    private Item createProbeSet(String probeSetId) throws ObjectStoreException {
        Item probeSet = createItem("ProbeSet");
        probeSet.setAttribute("identifier", probeSetId);
        probeSet.setAttribute("name", probeSetId);
        probeSet.setReference("organism", org.getIdentifier());
        probeSet.setCollection("evidence",
            new ArrayList(Collections.singleton(dataSet.getIdentifier())));

        Item synonym = createItem("Synonym");
        synonym.setAttribute("type", "identifier");
        synonym.setAttribute("value", probeSetId);
        synonym.setReference("source", dataSource.getIdentifier());
        synonym.setReference("subject", probeSet.getIdentifier());
        writer.store(ItemHelper.convert(synonym));

        return probeSet;
    }

    private Item createChromosome(String chrId) throws ObjectStoreException {
        Item chr = (Item) chrMap.get(chrId);
        if (chr == null) {
            chr = createItem("Chromosome");
            // convert 'chr2L' -> '2L'
            chr.setAttribute("identifier", chrId.substring(3, chrId.length()));
            chr.setReference("organism", org.getIdentifier());
            chrMap.put(chrId, chr);
            writer.store(ItemHelper.convert(chr));
        }
        return chr;
    }

    private String newId(String className) {
        Integer id = (Integer) ids.get(className);
        if (id == null) {
            id = new Integer(0);
            ids.put(className, id);
        }
        id = new Integer(id.intValue() + 1);
        ids.put(className, id);
        return id.toString();
    }

    private Item createItem(String className) {
        return itemFactory.makeItem(alias(className) + "_" + newId(className),
                                    GENOMIC_NS + className, "");
    }
}
